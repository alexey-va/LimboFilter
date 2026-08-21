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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AdvancedCaptchaRendererTest {

  private final AdvancedCaptchaRenderer renderer = new AdvancedCaptchaRenderer(128, 128);

  @Test
  void rendersEveryFamilyAsAUsableMinecraftMap() {
    for (CaptchaFamily family : CaptchaFamily.values()) {
      RenderedCaptcha captcha = this.renderer.render(family, new Random(1000L + family.ordinal()));

      assertEquals(family, captcha.family());
      int expectedSize = family == CaptchaFamily.ITEM_SEQUENCE ? 3 * 128 : 128;
      assertEquals(expectedSize, captcha.image().getWidth());
      assertEquals(expectedSize, captcha.image().getHeight());
      assertFalse(captcha.answer().isBlank());
      assertEquals(captcha.answer(), captcha.answer().trim().toUpperCase());
    }
  }

  @Test
  void textAnswersExcludeCommonlyConfusedGlyphs() {
    for (int seed = 0; seed < 500; ++seed) {
      String answer = this.renderer.render(CaptchaFamily.TEXT, new Random(seed)).answer();
      assertEquals(4, answer.length());
      assertFalse(answer.matches(".*[IO01].*"), answer);
    }
  }

  @Test
  void arithmeticAnswersAreSmallSignedIntegers() {
    for (int seed = 0; seed < 100; ++seed) {
      String answer = this.renderer.render(CaptchaFamily.ARITHMETIC, new Random(seed)).answer();
      int value = Integer.parseInt(answer);
      assertTrue(value >= 0 && value <= 18, answer);
    }
  }

  @Test
  void markedGlyphAnswersContainOnlyTheSelectedSubset() {
    for (int seed = 0; seed < 100; ++seed) {
      String answer = this.renderer.render(CaptchaFamily.MARKED_GLYPHS, new Random(seed)).answer();
      assertTrue(answer.length() >= 2 && answer.length() <= 3, answer);
      assertFalse(answer.matches(".*[IO01].*"), answer);
    }
  }

  @Test
  void itemSequenceRendersAThreeByThreeClickableMapWall() {
    RenderedCaptcha captcha = this.renderer.render(CaptchaFamily.ITEM_SEQUENCE, new Random(42L));

    assertEquals(3 * 128, captcha.image().getWidth());
    assertEquals(3 * 128, captcha.image().getHeight());
    assertTrue(captcha.answer().matches("CLICK:(1[0-5],){2}1[0-5]"), captcha.answer());
  }

  @Test
  void rejectsPartialMapWallDimensions() {
    BufferedImage partialWall = new BufferedImage(2 * 128, 128, BufferedImage.TYPE_INT_ARGB);

    assertThrows(IllegalArgumentException.class,
        () -> new RenderedCaptcha(CaptchaFamily.TEXT, "ABCD", partialWall));
  }

  @Test
  void seededRenderingIsReproducibleButNotConstant() throws NoSuchAlgorithmException {
    String first = hash(this.renderer.render(CaptchaFamily.MARKED_GLYPHS, new Random(42L)).image());
    String repeated = hash(this.renderer.render(CaptchaFamily.MARKED_GLYPHS, new Random(42L)).image());
    String anotherSeed = hash(this.renderer.render(CaptchaFamily.MARKED_GLYPHS, new Random(43L)).image());

    assertEquals(first, repeated);
    assertNotEquals(first, anotherSeed);
  }

  private static String hash(BufferedImage image) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * image.getWidth() * image.getHeight());
    for (int y = 0; y < image.getHeight(); ++y) {
      for (int x = 0; x < image.getWidth(); ++x) {
        buffer.putInt(image.getRGB(x, y));
      }
    }
    return HexFormat.of().formatHex(digest.digest(buffer.array()));
  }
}
