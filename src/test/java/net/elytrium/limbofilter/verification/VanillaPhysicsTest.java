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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VanillaPhysicsTest {

  private static final PhysicsProfile PROFILE = PhysicsProfile.javaModern(0.035, 0.0625, 4);

  @Test
  void predictsKnownVanillaGravitySequence() {
    MotionSample first = VanillaPhysics.next(
        new MotionSample(0, new MotionVector(0.0, 10.0, 0.0), MotionVector.ZERO, false), PROFILE, 0.0);
    MotionSample second = VanillaPhysics.next(first, PROFILE, 0.0);

    assertAll(
        () -> assertEquals(-0.0784, first.velocity().y(), 1.0e-12),
        () -> assertEquals(9.9216, first.position().y(), 1.0e-12),
        () -> assertEquals(-0.155232, second.velocity().y(), 1.0e-12),
        () -> assertEquals(9.766368, second.position().y(), 1.0e-12)
    );
  }

  @Test
  void appliesHorizontalAirDragToAnImpulse() {
    MotionSample next = VanillaPhysics.next(
        new MotionSample(0, new MotionVector(2.0, 10.0, -2.0), new MotionVector(0.4, 0.0, -0.2), false),
        PROFILE, 0.0);

    assertAll(
        () -> assertEquals(2.364, next.position().x(), 1.0e-12),
        () -> assertEquals(-2.182, next.position().z(), 1.0e-12),
        () -> assertEquals(0.364, next.velocity().x(), 1.0e-12),
        () -> assertEquals(-0.182, next.velocity().z(), 1.0e-12)
    );
  }

  @Test
  void clampsCrossingMotionToThePlatformTop() {
    MotionSample next = VanillaPhysics.next(
        new MotionSample(7, new MotionVector(0.0, 0.05, 0.0), MotionVector.ZERO, false), PROFILE, 0.0);

    assertAll(
        () -> assertEquals(0.0, next.position().y()),
        () -> assertEquals(MotionVector.ZERO, next.velocity()),
        () -> assertTrue(next.onGround()),
        () -> assertEquals(8, next.tick())
    );
  }
}
