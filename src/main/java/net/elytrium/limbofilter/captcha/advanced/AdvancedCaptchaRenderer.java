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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class AdvancedCaptchaRenderer {

  private static final String GLYPHS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final String[] FONT_NAMES = {Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
  private static final Color[] INK_COLORS = {
      new Color(25, 34, 54),
      new Color(74, 31, 91),
      new Color(18, 78, 68),
      new Color(105, 37, 42)
  };
  private static final Color MARKER_COLOR = new Color(0, 115, 190);

  private final int width;
  private final int height;

  public AdvancedCaptchaRenderer(int width, int height) {
    if (width != 128 || height != 128) {
      throw new IllegalArgumentException("advanced captcha renderer supports exactly one Minecraft map");
    }
    this.width = width;
    this.height = height;
  }

  public RenderedCaptcha render(CaptchaFamily family, RandomGenerator random) {
    Objects.requireNonNull(family, "family");
    Objects.requireNonNull(random, "random");

    if (family == CaptchaFamily.ITEM_SEQUENCE) {
      return ItemSequenceCaptchaRenderer.render(random);
    }
    if (family == CaptchaFamily.MEMORY_GRID) {
      return MemoryGridCaptchaRenderer.render(random);
    }

    BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    configure(graphics);
    this.drawBackground(graphics, random);

    String answer = switch (family) {
      case TEXT -> this.drawTextChallenge(graphics, random);
      case ARITHMETIC -> this.drawArithmeticChallenge(graphics, random);
      case MARKED_GLYPHS -> this.drawMarkedGlyphChallenge(graphics, random);
      case ITEM_SEQUENCE -> throw new IllegalStateException("item sequence is rendered separately");
      case MEMORY_GRID -> throw new IllegalStateException("memory grid is rendered separately");
    };

    this.drawInterference(graphics, random);
    graphics.dispose();
    return new RenderedCaptcha(family, answer, image);
  }

  private static void configure(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  private void drawBackground(Graphics2D graphics, RandomGenerator random) {
    Color first = new Color(225 + random.nextInt(20), 225 + random.nextInt(20), 225 + random.nextInt(20));
    Color second = new Color(205 + random.nextInt(30), 215 + random.nextInt(25), 225 + random.nextInt(20));
    graphics.setPaint(new GradientPaint(0, 0, first, this.width, this.height, second));
    graphics.fillRect(0, 0, this.width, this.height);

    for (int index = 0; index < 90; ++index) {
      int shade = 130 + random.nextInt(80);
      graphics.setColor(new Color(shade, shade, shade, 70 + random.nextInt(70)));
      int size = 1 + random.nextInt(3);
      graphics.fillOval(random.nextInt(this.width), random.nextInt(this.height), size, size);
    }
  }

  private String drawTextChallenge(Graphics2D graphics, RandomGenerator random) {
    String text = randomGlyphs(random, 4);
    this.drawGlyphRow(graphics, text, 27, 82, 48, null, random);
    return text;
  }

  private String drawArithmeticChallenge(Graphics2D graphics, RandomGenerator random) {
    int left = 2 + random.nextInt(8);
    boolean addition = random.nextBoolean();
    int right = addition ? 1 + random.nextInt(9) : random.nextInt(left + 1);
    final String expression = left + (addition ? "+" : "-") + right;
    final int answer = addition ? left + right : left - right;

    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
    graphics.setColor(new Color(45, 55, 70));
    graphics.drawString("SOLVE", 43, 20);
    this.drawGlyphRow(graphics, expression, 15, 87, 50, null, random);
    return Integer.toString(answer);
  }

  private String drawMarkedGlyphChallenge(Graphics2D graphics, RandomGenerator random) {
    String text = randomGlyphs(random, 6);
    boolean[] marked = new boolean[text.length()];
    int markedCount = 2 + random.nextInt(2);
    StringBuilder answer = new StringBuilder(markedCount);
    for (int selected = 0; selected < markedCount; ) {
      int index = random.nextInt(marked.length);
      if (!marked[index]) {
        marked[index] = true;
        ++selected;
      }
    }
    for (int index = 0; index < marked.length; ++index) {
      if (marked[index]) {
        answer.append(text.charAt(index));
      }
    }

    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    graphics.setColor(new Color(45, 55, 70));
    graphics.drawString("TYPE MARKED", 24, 16);
    graphics.setColor(MARKER_COLOR);
    graphics.fillOval(105, 7, 7, 7);
    this.drawGlyphRow(graphics, text, 5, 91, 31, marked, random);
    return answer.toString();
  }

  private void drawGlyphRow(Graphics2D graphics, String text, int left, int baseline,
                            int fontSize, boolean[] marked, RandomGenerator random) {
    int available = this.width - 2 * left;
    double slotWidth = (double) available / text.length();

    for (int index = 0; index < text.length(); ++index) {
      final double centerX = left + slotWidth * index + slotWidth / 2.0;
      final int jitterY = random.nextInt(-5, 6);
      final double rotation = Math.toRadians(random.nextDouble(-13.0, 13.0));
      Font font = new Font(FONT_NAMES[random.nextInt(FONT_NAMES.length)], Font.BOLD, fontSize);

      Graphics2D glyphGraphics = (Graphics2D) graphics.create();
      configure(glyphGraphics);
      glyphGraphics.setFont(font);
      glyphGraphics.setColor(INK_COLORS[random.nextInt(INK_COLORS.length)]);
      FontMetrics metrics = glyphGraphics.getFontMetrics();
      String glyph = String.valueOf(text.charAt(index));
      double glyphX = centerX - metrics.stringWidth(glyph) / 2.0;
      AffineTransform transform = AffineTransform.getRotateInstance(rotation, centerX, baseline + jitterY - fontSize / 2.0);
      glyphGraphics.transform(transform);
      glyphGraphics.drawString(glyph, (float) glyphX, baseline + jitterY);
      glyphGraphics.dispose();

      if (marked != null && marked[index]) {
        graphics.setColor(MARKER_COLOR);
        int markerX = (int) Math.round(centerX) - 4;
        int markerY = baseline - fontSize - 7 + jitterY;
        graphics.fillOval(markerX, markerY, 8, 8);
        graphics.setStroke(new BasicStroke(2.0F));
        graphics.drawLine((int) (centerX - slotWidth * 0.30), baseline + 8,
            (int) (centerX + slotWidth * 0.30), baseline + 8);
      }
    }
  }

  private void drawInterference(Graphics2D graphics, RandomGenerator random) {
    graphics.setStroke(new BasicStroke(1.0F));
    for (int index = 0; index < 7; ++index) {
      Color color = INK_COLORS[random.nextInt(INK_COLORS.length)];
      graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
      int x1 = random.nextInt(this.width);
      int y1 = 20 + random.nextInt(this.height - 20);
      int x2 = random.nextInt(this.width);
      int y2 = 20 + random.nextInt(this.height - 20);
      graphics.drawLine(x1, y1, x2, y2);
    }
  }

  private static String randomGlyphs(RandomGenerator random, int length) {
    StringBuilder result = new StringBuilder(length);
    for (int index = 0; index < length; ++index) {
      result.append(GLYPHS.charAt(random.nextInt(GLYPHS.length())));
    }
    return result.toString();
  }
}
