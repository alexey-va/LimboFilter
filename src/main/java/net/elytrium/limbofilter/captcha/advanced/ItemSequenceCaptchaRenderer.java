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
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

final class ItemSequenceCaptchaRenderer {

  private static final int MAP_SIZE = 128;
  private static final int GRID_SIZE = 3;
  private static final int OPTION_OFFSET = GRID_SIZE;
  private static final int OPTION_COUNT = 6;
  private static final int FIRST_FRAME_ENTITY_ID = 10;
  private static final Color[] ITEM_COLORS = {
      new Color(0x35, 0xB9, 0xC8),
      new Color(0xF2, 0xA9, 0x3B),
      new Color(0xB4, 0x65, 0xD9),
      new Color(0x63, 0xC1, 0x74),
      new Color(0xEB, 0x5D, 0x68),
      new Color(0x5F, 0x86, 0xE8)
  };

  private ItemSequenceCaptchaRenderer() {
  }

  public static RenderedCaptcha render(RandomGenerator random) {
    int canvasSize = MAP_SIZE * GRID_SIZE;
    BufferedImage image = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    configure(graphics);
    graphics.setPaint(new GradientPaint(0, 0, new Color(15, 22, 36), canvasSize, canvasSize,
        new Color(31, 20, 48)));
    graphics.fillRect(0, 0, canvasSize, canvasSize);

    List<ItemToken> tokens = createItemTokens(random);
    List<Integer> targetOptions = shuffledIndexes(OPTION_COUNT, random).subList(0, GRID_SIZE);

    for (int order = 0; order < targetOptions.size(); ++order) {
      drawItemCard(graphics, tokens.get(targetOptions.get(order)), order * MAP_SIZE, 0, order + 1, true, random);
    }
    for (int option = 0; option < OPTION_COUNT; ++option) {
      int visualTile = OPTION_OFFSET + option;
      int tileX = visualTile % GRID_SIZE;
      int tileY = visualTile / GRID_SIZE;
      drawItemCard(graphics, tokens.get(option), tileX * MAP_SIZE, tileY * MAP_SIZE, 0, false, random);
    }

    graphics.dispose();
    String answer = targetOptions.stream()
        .map(option -> Integer.toString(entityIdForVisualTile(OPTION_OFFSET + option)))
        .reduce("CLICK", (prefix, entityId) -> prefix + (prefix.equals("CLICK") ? ":" : ",") + entityId);
    return new RenderedCaptcha(CaptchaFamily.ITEM_SEQUENCE, answer, image);
  }

  private static List<ItemToken> createItemTokens(RandomGenerator random) {
    List<ItemToken> pool = new ArrayList<>();
    for (ItemShape shape : ItemShape.values()) {
      for (int color = 0; color < ITEM_COLORS.length; ++color) {
        pool.add(new ItemToken(shape, ITEM_COLORS[color], color % 3));
      }
    }
    shuffle(pool, random);
    return List.copyOf(pool.subList(0, OPTION_COUNT));
  }

  private static void drawItemCard(Graphics2D graphics, ItemToken token, int left, int top,
                                   int order, boolean target, RandomGenerator random) {
    Graphics2D card = (Graphics2D) graphics.create();
    configure(card);
    card.setColor(target ? new Color(29, 38, 57) : new Color(24, 29, 42));
    card.fillRoundRect(left + 8, top + 8, MAP_SIZE - 16, MAP_SIZE - 16, 18, 18);
    card.setStroke(new BasicStroke(target ? 4.0F : 2.0F));
    card.setColor(target ? new Color(242, 204, 96) : new Color(102, 118, 148));
    card.drawRoundRect(left + 8, top + 8, MAP_SIZE - 16, MAP_SIZE - 16, 18, 18);

    for (int index = 0; index < (target ? 10 : 18); ++index) {
      int shade = 75 + random.nextInt(75);
      card.setColor(new Color(shade, shade, shade, 80));
      int x = left + 13 + random.nextInt(MAP_SIZE - 26);
      int y = top + 13 + random.nextInt(MAP_SIZE - 26);
      card.fillRect(x, y, 1 + random.nextInt(3), 1 + random.nextInt(3));
    }

    int centerX = left + MAP_SIZE / 2;
    int centerY = top + MAP_SIZE / 2 + (target ? 8 : 0);
    Graphics2D symbol = (Graphics2D) card.create();
    double rotation = Math.toRadians(random.nextDouble(target ? -4.0 : -11.0, target ? 4.0 : 11.0));
    symbol.rotate(rotation, centerX, centerY);
    drawItemShape(symbol, token, centerX, centerY, target ? 27 : 35);
    symbol.dispose();

    if (target) {
      card.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
      card.setColor(new Color(245, 224, 150));
      card.drawString(Integer.toString(order), left + 17, top + 30);
      card.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
      card.drawString("CLICK", left + 77, top + 26);
    }
    card.dispose();
  }

  private static void drawItemShape(Graphics2D graphics, ItemToken token, int centerX, int centerY, int radius) {
    graphics.setStroke(new BasicStroke(5.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(new Color(5, 8, 14, 150));
    java.awt.Shape shadow = createShape(token.shape(), centerX + 4, centerY + 5, radius);
    graphics.fill(shadow);
    graphics.setColor(token.color());
    java.awt.Shape shape = createShape(token.shape(), centerX, centerY, radius);
    graphics.fill(shape);
    graphics.setColor(new Color(245, 248, 255));
    graphics.draw(shape);

    graphics.setStroke(new BasicStroke(3.0F));
    graphics.setColor(new Color(255, 255, 255, 175));
    if (token.pattern() == 0) {
      graphics.drawLine(centerX - radius / 2, centerY, centerX + radius / 2, centerY);
    } else if (token.pattern() == 1) {
      graphics.drawLine(centerX, centerY - radius / 2, centerX, centerY + radius / 2);
    } else {
      graphics.fillOval(centerX - 4, centerY - 4, 8, 8);
    }
  }

  private static java.awt.Shape createShape(ItemShape shape, int centerX, int centerY, int radius) {
    return switch (shape) {
      case CIRCLE -> new java.awt.geom.Ellipse2D.Double(
          centerX - radius, centerY - radius, radius * 2.0, radius * 2.0);
      case TRIANGLE -> new Polygon(
          new int[]{centerX, centerX + radius, centerX - radius},
          new int[]{centerY - radius, centerY + radius, centerY + radius}, 3);
      case DIAMOND -> new Polygon(
          new int[]{centerX, centerX + radius, centerX, centerX - radius},
          new int[]{centerY - radius, centerY, centerY + radius, centerY}, 4);
      case CROSS -> crossPath(centerX, centerY, radius);
      case STAR -> starPath(centerX, centerY, radius);
      case SHIELD -> shieldPath(centerX, centerY, radius);
    };
  }

  private static Path2D crossPath(int centerX, int centerY, int radius) {
    int arm = Math.max(6, radius / 3);
    Path2D path = new Path2D.Double();
    path.moveTo(centerX - arm, centerY - radius);
    path.lineTo(centerX + arm, centerY - radius);
    path.lineTo(centerX + arm, centerY - arm);
    path.lineTo(centerX + radius, centerY - arm);
    path.lineTo(centerX + radius, centerY + arm);
    path.lineTo(centerX + arm, centerY + arm);
    path.lineTo(centerX + arm, centerY + radius);
    path.lineTo(centerX - arm, centerY + radius);
    path.lineTo(centerX - arm, centerY + arm);
    path.lineTo(centerX - radius, centerY + arm);
    path.lineTo(centerX - radius, centerY - arm);
    path.lineTo(centerX - arm, centerY - arm);
    path.closePath();
    return path;
  }

  private static Path2D starPath(int centerX, int centerY, int radius) {
    Path2D path = new Path2D.Double();
    for (int point = 0; point < 10; ++point) {
      double angle = -Math.PI / 2.0 + point * Math.PI / 5.0;
      double length = point % 2 == 0 ? radius : radius * 0.46;
      double x = centerX + Math.cos(angle) * length;
      double y = centerY + Math.sin(angle) * length;
      if (point == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.closePath();
    return path;
  }

  private static Path2D shieldPath(int centerX, int centerY, int radius) {
    Path2D path = new Path2D.Double();
    path.moveTo(centerX - radius, centerY - radius * 0.75);
    path.quadTo(centerX, centerY - radius * 1.15, centerX + radius, centerY - radius * 0.75);
    path.lineTo(centerX + radius * 0.78, centerY + radius * 0.45);
    path.quadTo(centerX, centerY + radius * 1.25, centerX - radius * 0.78, centerY + radius * 0.45);
    path.closePath();
    return path;
  }

  private static int entityIdForVisualTile(int visualTile) {
    return FIRST_FRAME_ENTITY_ID + GRID_SIZE * GRID_SIZE - 1 - visualTile;
  }

  private static List<Integer> shuffledIndexes(int size, RandomGenerator random) {
    List<Integer> indexes = new ArrayList<>(size);
    for (int index = 0; index < size; ++index) {
      indexes.add(index);
    }
    shuffle(indexes, random);
    return indexes;
  }

  private static <T> void shuffle(List<T> values, RandomGenerator random) {
    for (int index = values.size() - 1; index > 0; --index) {
      int selected = random.nextInt(index + 1);
      T value = values.get(index);
      values.set(index, values.get(selected));
      values.set(selected, value);
    }
  }

  private static void configure(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  private enum ItemShape {
    CIRCLE,
    TRIANGLE,
    DIAMOND,
    CROSS,
    STAR,
    SHIELD
  }

  private record ItemToken(ItemShape shape, Color color, int pattern) {
  }
}
