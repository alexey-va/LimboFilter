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

package net.elytrium.limbofilter.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MotionVectorTest {

  @Test
  void composesMotionWithoutLosingAxes() {
    MotionVector result = new MotionVector(1.0, -2.0, 3.5)
        .add(new MotionVector(0.5, 1.0, -1.5))
        .scale(2.0);

    assertEquals(new MotionVector(3.0, -2.0, 4.0), result);
    assertEquals(5.385164807134504, result.distanceTo(MotionVector.ZERO), 1.0e-12);
  }

  @Test
  void exposesNonFiniteNetworkCoordinates() {
    assertTrue(new MotionVector(1.0, 2.0, 3.0).isFinite());
    assertFalse(new MotionVector(Double.NaN, 2.0, 3.0).isFinite());
    assertFalse(new MotionVector(1.0, Double.POSITIVE_INFINITY, 3.0).isFinite());
  }
}
