/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.captcha.advanced;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InteractiveCaptchaSession {

  private static final int FIRST_OPTION_ENTITY_ID = 10;
  private static final int LAST_OPTION_ENTITY_ID = 15;
  private static final Pattern ANSWER_PATTERN = Pattern.compile("^CLICK:(1[0-5]),(1[0-5]),(1[0-5])$");

  private final int[] targetEntityIds;
  private int selectedCount;
  private int lastSelectedEntityId = -1;
  private boolean terminal;

  private InteractiveCaptchaSession(int[] targetEntityIds) {
    this.targetEntityIds = targetEntityIds;
  }

  public static boolean isInteractiveAnswer(String answer) {
    return answer != null && ANSWER_PATTERN.matcher(answer).matches();
  }

  public static InteractiveCaptchaSession fromAnswer(String answer) {
    Matcher matcher = ANSWER_PATTERN.matcher(answer == null ? "" : answer);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("invalid interactive captcha answer");
    }

    int[] targetEntityIds = {
        Integer.parseInt(matcher.group(1)),
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(3))
    };
    if (Arrays.stream(targetEntityIds).distinct().count() != targetEntityIds.length) {
      throw new IllegalArgumentException("interactive captcha targets must be unique");
    }
    return new InteractiveCaptchaSession(targetEntityIds);
  }

  public SelectionResult select(int entityId) {
    if (this.terminal || entityId < FIRST_OPTION_ENTITY_ID || entityId > LAST_OPTION_ENTITY_ID
        || entityId == this.lastSelectedEntityId) {
      return SelectionResult.IGNORED;
    }

    this.lastSelectedEntityId = entityId;
    if (entityId != this.targetEntityIds[this.selectedCount]) {
      this.terminal = true;
      return SelectionResult.FAILED;
    }

    ++this.selectedCount;
    if (this.selectedCount == this.targetEntityIds.length) {
      this.terminal = true;
      return SelectionResult.PASSED;
    }
    return SelectionResult.PENDING;
  }

  public enum SelectionResult {
    IGNORED,
    PENDING,
    PASSED,
    FAILED
  }
}
