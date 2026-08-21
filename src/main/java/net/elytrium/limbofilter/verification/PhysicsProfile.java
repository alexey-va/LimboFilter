/*
 * Copyright (C) 2021 - 2026 Elytrium
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

package net.elytrium.limbofilter.verification;

public record PhysicsProfile(double gravity, double verticalDrag, double horizontalDrag,
                             double positionTolerance, double collisionTolerance,
                             int maxPacketGapTicks, boolean impulseSupported) {

  public PhysicsProfile {
    requireFinitePositive(gravity, "gravity");
    requireDrag(verticalDrag, "verticalDrag");
    requireDrag(horizontalDrag, "horizontalDrag");
    requireFinitePositive(positionTolerance, "positionTolerance");
    requireFinitePositive(collisionTolerance, "collisionTolerance");
    if (maxPacketGapTicks < 1 || maxPacketGapTicks > 20) {
      throw new IllegalArgumentException("maxPacketGapTicks must be in range 1..20");
    }
  }

  public static PhysicsProfile javaLegacy(double positionTolerance, double collisionTolerance,
                                          int maxPacketGapTicks) {
    return javaProfile(positionTolerance, collisionTolerance, maxPacketGapTicks, false);
  }

  public static PhysicsProfile javaModern(double positionTolerance, double collisionTolerance,
                                          int maxPacketGapTicks) {
    return javaProfile(positionTolerance, collisionTolerance, maxPacketGapTicks, true);
  }

  private static PhysicsProfile javaProfile(double positionTolerance, double collisionTolerance,
                                            int maxPacketGapTicks, boolean impulseSupported) {
    return new PhysicsProfile(0.08, 0.98, 0.91, positionTolerance, collisionTolerance,
        maxPacketGapTicks, impulseSupported);
  }

  private static void requireFinitePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireDrag(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be in range (0, 1]");
    }
  }
}
