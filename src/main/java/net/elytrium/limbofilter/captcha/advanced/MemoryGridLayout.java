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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MemoryGridLayout {

  private static final int GRID_SIZE = 3;
  private static final int TILE_BLOCK_SIZE = 3;
  private static final int GRID_MIN_BLOCK = -4;
  private static final int GRID_MAX_BLOCK = 4;
  private static final int START_PAD_MIN_Z = -7;
  private static final int START_PAD_MAX_Z = -5;
  private static final int BLOCK_Y = 79;
  private static final int GUIDE_FRAME_MIN_X = -1;
  private static final int GUIDE_FRAME_MIN_Y = BLOCK_Y + 3;
  private static final int GUIDE_FRAME_Z = GRID_MAX_BLOCK + 2;
  private static final float START_YAW = 0.0F;
  private static final double TOP_Y = BLOCK_Y + 1.0;
  private static final double Y_TOLERANCE = 0.35;
  private static final List<String> MATERIALS = List.of(
      "minecraft:red_wool",
      "minecraft:orange_wool",
      "minecraft:yellow_wool",
      "minecraft:lime_wool",
      "minecraft:light_blue_wool",
      "minecraft:blue_wool",
      "minecraft:purple_wool",
      "minecraft:magenta_wool",
      "minecraft:white_wool"
  );

  private MemoryGridLayout() {
  }

  public static int tileAt(double x, double y, double z, boolean onGround) {
    if (!onGround || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
        || Math.abs(y - TOP_Y) > Y_TOLERANCE) {
      return -1;
    }

    int blockX = (int) Math.floor(x);
    int blockZ = (int) Math.floor(z);
    if (blockX < GRID_MIN_BLOCK || blockX > GRID_MAX_BLOCK
        || blockZ < GRID_MIN_BLOCK || blockZ > GRID_MAX_BLOCK) {
      return -1;
    }

    int column = columnForBlock(blockX);
    int row = (blockZ - GRID_MIN_BLOCK) / TILE_BLOCK_SIZE;
    return row * GRID_SIZE + column;
  }

  public static String materialForTile(int tile) {
    if (tile < 0 || tile >= MATERIALS.size()) {
      throw new IllegalArgumentException("memory grid tile must be in range 0..8");
    }
    return MATERIALS.get(tile);
  }

  public static int tileForBlock(int blockX, int blockZ) {
    if (blockX < GRID_MIN_BLOCK || blockX > GRID_MAX_BLOCK
        || blockZ < GRID_MIN_BLOCK || blockZ > GRID_MAX_BLOCK) {
      return -1;
    }
    int column = columnForBlock(blockX);
    int row = (blockZ - GRID_MIN_BLOCK) / TILE_BLOCK_SIZE;
    return row * GRID_SIZE + column;
  }

  private static int columnForBlock(int blockX) {
    return GRID_SIZE - 1 - (blockX - GRID_MIN_BLOCK) / TILE_BLOCK_SIZE;
  }

  public static Set<ChunkCoordinate> chunks() {
    Set<ChunkCoordinate> chunks = new HashSet<>();
    int minChunkX = Math.floorDiv(GRID_MIN_BLOCK, 16);
    int maxChunkX = Math.floorDiv(GRID_MAX_BLOCK, 16);
    int minChunkZ = Math.floorDiv(START_PAD_MIN_Z, 16);
    int maxChunkZ = Math.floorDiv(GRID_MAX_BLOCK, 16);
    for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
        chunks.add(new ChunkCoordinate(chunkX, chunkZ));
      }
    }
    return Set.copyOf(chunks);
  }

  public static int gridMinBlock() {
    return GRID_MIN_BLOCK;
  }

  public static int gridMaxBlock() {
    return GRID_MAX_BLOCK;
  }

  public static int startPadMinZ() {
    return START_PAD_MIN_Z;
  }

  public static int startPadMaxZ() {
    return START_PAD_MAX_Z;
  }

  public static int blockY() {
    return BLOCK_Y;
  }

  public static double startX() {
    return 0.5;
  }

  public static double startY() {
    return TOP_Y;
  }

  public static double startZ() {
    return START_PAD_MIN_Z + 0.5;
  }

  public static float startYaw() {
    return START_YAW;
  }

  public static double guideFrameX(int mapId) {
    requireMapId(mapId);
    return GUIDE_FRAME_MIN_X + mapId % GRID_SIZE;
  }

  public static double guideFrameY(int mapId) {
    requireMapId(mapId);
    return GUIDE_FRAME_MIN_Y + mapId / GRID_SIZE;
  }

  public static double guideFrameZ() {
    return GUIDE_FRAME_Z;
  }

  private static void requireMapId(int mapId) {
    if (mapId < 0 || mapId >= GRID_SIZE * GRID_SIZE) {
      throw new IllegalArgumentException("memory grid map id must be in range 0..8");
    }
  }

  public record ChunkCoordinate(int x, int z) {
  }
}
