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
      boolean wallChallenge = family == CaptchaFamily.ITEM_SEQUENCE
          || family.name().equals("MEMORY_GRID");
      int expectedSize = wallChallenge ? 3 * 128 : 128;
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
  void memoryGridRendersAnAdjacentWalkablePathFromTheEntrance() {
    RenderedCaptcha captcha = this.renderer.render(
        CaptchaFamily.valueOf("MEMORY_GRID"), new Random(42L));

    assertEquals(3 * 128, captcha.image().getWidth());
    assertEquals(3 * 128, captcha.image().getHeight());
    assertTrue(captcha.answer().matches("WALK:[0-8](,[0-8]){2}"), captcha.answer());

    int[] tiles = java.util.Arrays.stream(captcha.answer().substring("WALK:".length()).split(","))
        .mapToInt(Integer::parseInt)
        .toArray();
    assertTrue(tiles[0] <= 2, "the first tile must be reachable from the north entrance");
    assertEquals(3, java.util.Arrays.stream(tiles).distinct().count());
    for (int index = 1; index < tiles.length; ++index) {
      int rowDistance = Math.abs(tiles[index] / 3 - tiles[index - 1] / 3);
      int columnDistance = Math.abs(tiles[index] % 3 - tiles[index - 1] % 3);
      assertEquals(1, rowDistance + columnDistance,
          "successive memory tiles must share an edge");
    }
  }

  @Test
  void memoryGridMatchesTheFloorFromThePlayersView() {
    RenderedCaptcha captcha = this.renderer.render(CaptchaFamily.MEMORY_GRID, new Random(42L));

    assertEquals(new java.awt.Color(113, 70, 162).getRGB(), captcha.image().getRGB(24, 24));
    assertEquals(new java.awt.Color(218, 221, 224).getRGB(),
        captcha.image().getRGB(2 * 128 + 24, 24));
    assertEquals(new java.awt.Color(181, 58, 52).getRGB(),
        captcha.image().getRGB(24, 2 * 128 + 24));
    assertEquals(new java.awt.Color(226, 190, 49).getRGB(),
        captcha.image().getRGB(2 * 128 + 24, 2 * 128 + 24));
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
