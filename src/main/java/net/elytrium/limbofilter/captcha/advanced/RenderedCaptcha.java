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

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;

public record RenderedCaptcha(CaptchaFamily family, String answer, BufferedImage image) {

  public RenderedCaptcha {
    Objects.requireNonNull(family, "family");
    Objects.requireNonNull(answer, "answer");
    Objects.requireNonNull(image, "image");
    answer = answer.trim().toUpperCase(Locale.ROOT);
    if (answer.isEmpty()) {
      throw new IllegalArgumentException("captcha answer cannot be empty");
    }
    boolean singleMap = image.getWidth() == 128 && image.getHeight() == 128;
    boolean interactiveWall = image.getWidth() == 3 * 128 && image.getHeight() == 3 * 128;
    if (!singleMap && !interactiveWall) {
      throw new IllegalArgumentException("advanced captcha must be one map or a 3x3 interactive wall");
    }
  }
}
