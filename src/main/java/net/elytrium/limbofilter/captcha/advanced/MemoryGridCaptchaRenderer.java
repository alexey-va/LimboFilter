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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

final class MemoryGridCaptchaRenderer {

  private static final int MAP_SIZE = 128;
  private static final int GRID_SIZE = 3;
  private static final int PATH_LENGTH = 3;
  private static final Color[] TILE_COLORS = {
      new Color(181, 58, 52),
      new Color(222, 121, 34),
      new Color(226, 190, 49),
      new Color(91, 166, 61),
      new Color(70, 153, 194),
      new Color(65, 94, 183),
      new Color(113, 70, 162),
      new Color(190, 74, 155),
      new Color(218, 221, 224)
  };

  private MemoryGridCaptchaRenderer() {
  }

  public static RenderedCaptcha render(RandomGenerator random) {
    final int[] path = createPath(random);
    int canvasSize = MAP_SIZE * GRID_SIZE;
    BufferedImage image = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    configure(graphics);
    graphics.setColor(new Color(12, 17, 28));
    graphics.fillRect(0, 0, canvasSize, canvasSize);

    for (int tile = 0; tile < GRID_SIZE * GRID_SIZE; ++tile) {
      int row = tile / GRID_SIZE;
      drawTile(graphics, displayColumn(tile) * MAP_SIZE, row * MAP_SIZE, tile, orderOf(path, tile));
    }
    drawPath(graphics, path);
    graphics.dispose();

    String answer = "WALK:" + path[0] + "," + path[1] + "," + path[2];
    return new RenderedCaptcha(CaptchaFamily.MEMORY_GRID, answer, image);
  }

  static int[] createPath(RandomGenerator random) {
    int[] path = new int[PATH_LENGTH];
    boolean[] used = new boolean[GRID_SIZE * GRID_SIZE];
    path[0] = random.nextInt(GRID_SIZE);
    used[path[0]] = true;

    for (int index = 1; index < path.length; ++index) {
      List<Integer> candidates = adjacent(path[index - 1], used);
      path[index] = candidates.get(random.nextInt(candidates.size()));
      used[path[index]] = true;
    }
    return path;
  }

  private static List<Integer> adjacent(int tile, boolean[] used) {
    int row = tile / GRID_SIZE;
    int column = tile % GRID_SIZE;
    List<Integer> candidates = new ArrayList<>(4);
    addIfAvailable(candidates, used, row - 1, column);
    addIfAvailable(candidates, used, row + 1, column);
    addIfAvailable(candidates, used, row, column - 1);
    addIfAvailable(candidates, used, row, column + 1);
    return candidates;
  }

  private static void addIfAvailable(List<Integer> candidates, boolean[] used, int row, int column) {
    if (row < 0 || row >= GRID_SIZE || column < 0 || column >= GRID_SIZE) {
      return;
    }
    int tile = row * GRID_SIZE + column;
    if (!used[tile]) {
      candidates.add(tile);
    }
  }

  private static int orderOf(int[] path, int tile) {
    for (int index = 0; index < path.length; ++index) {
      if (path[index] == tile) {
        return index + 1;
      }
    }
    return 0;
  }

  private static void drawTile(Graphics2D graphics, int left, int top, int tile, int order) {
    Color color = TILE_COLORS[tile];
    graphics.setColor(color.darker().darker());
    graphics.fillRect(left + 4, top + 4, MAP_SIZE - 8, MAP_SIZE - 8);
    graphics.setColor(color);
    graphics.fillRoundRect(left + 11, top + 11, MAP_SIZE - 22, MAP_SIZE - 22, 20, 20);
    graphics.setStroke(new BasicStroke(order == 0 ? 3.0F : 7.0F));
    graphics.setColor(order == 0 ? new Color(255, 255, 255, 85) : new Color(255, 247, 194));
    graphics.drawRoundRect(left + 11, top + 11, MAP_SIZE - 22, MAP_SIZE - 22, 20, 20);

    if (order == 0) {
      return;
    }
    String label = Integer.toString(order);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 58));
    FontMetrics metrics = graphics.getFontMetrics();
    int labelX = left + (MAP_SIZE - metrics.stringWidth(label)) / 2;
    int labelY = top + (MAP_SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
    graphics.setColor(new Color(12, 17, 28, 160));
    graphics.drawString(label, labelX + 3, labelY + 4);
    graphics.setColor(new Color(255, 252, 224));
    graphics.drawString(label, labelX, labelY);
  }

  private static void drawPath(Graphics2D graphics, int[] path) {
    graphics.setStroke(new BasicStroke(10.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(new Color(255, 247, 194, 210));
    for (int index = 1; index < path.length; ++index) {
      graphics.drawLine(centerX(path[index - 1]), centerY(path[index - 1]),
          centerX(path[index]), centerY(path[index]));
    }
  }

  private static int centerX(int tile) {
    return displayColumn(tile) * MAP_SIZE + MAP_SIZE / 2;
  }

  private static int displayColumn(int tile) {
    return GRID_SIZE - 1 - tile % GRID_SIZE;
  }

  private static int centerY(int tile) {
    return (tile / GRID_SIZE) * MAP_SIZE + MAP_SIZE / 2;
  }

  private static void configure(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }
}
