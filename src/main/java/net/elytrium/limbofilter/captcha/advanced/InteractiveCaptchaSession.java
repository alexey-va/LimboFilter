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
  private static final Pattern ANSWER_PATTERN = Pattern.compile("^(CLICK|WALK):([0-9]+),([0-9]+),([0-9]+)$");

  private final Kind kind;
  private final int[] targetEntityIds;
  private int selectedCount;
  private int lastSelectedEntityId = -1;
  private boolean terminal;

  private InteractiveCaptchaSession(Kind kind, int[] targetEntityIds) {
    this.kind = kind;
    this.targetEntityIds = targetEntityIds;
  }

  public static boolean isInteractiveAnswer(String answer) {
    try {
      fromAnswer(answer);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public static boolean isMemoryGridAnswer(String answer) {
    return isInteractiveAnswer(answer) && answer.startsWith("WALK:");
  }

  public static InteractiveCaptchaSession fromAnswer(String answer) {
    Matcher matcher = ANSWER_PATTERN.matcher(answer == null ? "" : answer);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("invalid interactive captcha answer");
    }

    Kind kind = Kind.valueOf(matcher.group(1));
    int[] targetEntityIds = {
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(3)),
        Integer.parseInt(matcher.group(4))
    };
    if (Arrays.stream(targetEntityIds).distinct().count() != targetEntityIds.length) {
      throw new IllegalArgumentException("interactive captcha targets must be unique");
    }
    if (Arrays.stream(targetEntityIds).anyMatch(target -> !kind.accepts(target))) {
      throw new IllegalArgumentException("interactive captcha target is outside its selectable range");
    }
    return new InteractiveCaptchaSession(kind, targetEntityIds);
  }

  public SelectionResult select(int entityId) {
    if (this.terminal || !this.kind.accepts(entityId)
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

  private enum Kind {
    CLICK(FIRST_OPTION_ENTITY_ID, LAST_OPTION_ENTITY_ID),
    WALK(0, 8);

    private final int firstTarget;
    private final int lastTarget;

    Kind(int firstTarget, int lastTarget) {
      this.firstTarget = firstTarget;
      this.lastTarget = lastTarget;
    }

    private boolean accepts(int target) {
      return target >= this.firstTarget && target <= this.lastTarget;
    }
  }
}
