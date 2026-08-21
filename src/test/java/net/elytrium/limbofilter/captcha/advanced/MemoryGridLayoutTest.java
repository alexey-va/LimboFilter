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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MemoryGridLayoutTest {

  @Test
  void mapsGroundedMovementToTheVisibleThreeByThreeTiles() {
    assertEquals(0, MemoryGridLayout.tileAt(3.5, 80.0, -2.5, true));
    assertEquals(2, MemoryGridLayout.tileAt(-2.5, 80.0, -2.5, true));
    assertEquals(4, MemoryGridLayout.tileAt(0.5, 80.0, 0.5, true));
    assertEquals(6, MemoryGridLayout.tileAt(3.5, 80.0, 3.5, true));
    assertEquals(5, MemoryGridLayout.tileAt(-2.5, 80.0, 0.5, true));
  }

  @Test
  void ignoresTheEntranceAirbornePacketsAndPositionsOutsideTheGrid() {
    assertEquals(-1, MemoryGridLayout.tileAt(0.5, 80.0, -6.5, true));
    assertEquals(-1, MemoryGridLayout.tileAt(0.5, 80.6, 0.5, true));
    assertEquals(-1, MemoryGridLayout.tileAt(0.5, 80.0, 0.5, false));
    assertEquals(-1, MemoryGridLayout.tileAt(5.0, 80.0, 0.5, true));
  }

  @Test
  void givesEveryTileAVisuallyDistinctLegacySafeWoolMaterial() {
    HashSet<String> materials = new HashSet<>();
    for (int tile = 0; tile < 9; ++tile) {
      String material = MemoryGridLayout.materialForTile(tile);
      assertTrue(material.startsWith("minecraft:"), material);
      assertTrue(material.endsWith("_wool"), material);
      materials.add(material);
    }
    assertEquals(9, materials.size());
  }

  @Test
  void placesThePersistentGuideAboveTheFarEdgeWithoutRotatingTheRoute() {
    assertEquals(1.0, MemoryGridLayout.guideFrameX(8));
    assertEquals(84.0, MemoryGridLayout.guideFrameY(8));
    assertEquals(-1.0, MemoryGridLayout.guideFrameX(0));
    assertEquals(82.0, MemoryGridLayout.guideFrameY(0));
    assertTrue(MemoryGridLayout.guideFrameZ() > MemoryGridLayout.gridMaxBlock());
    assertEquals(0.0F, MemoryGridLayout.startYaw());

    assertEquals(0, MemoryGridLayout.tileAt(3.5, 80.0, -2.5, true));
    assertEquals(8, MemoryGridLayout.tileAt(-2.5, 80.0, 3.5, true));
  }
}
