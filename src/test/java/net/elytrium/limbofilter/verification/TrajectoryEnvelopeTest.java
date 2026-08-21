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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrajectoryEnvelopeTest {

  private static final PhysicsProfile PROFILE = PhysicsProfile.javaModern(0.035, 0.0625, 4);
  private static final MotionSample START = new MotionSample(
      0, new MotionVector(0.0, 10.0, 0.0), MotionVector.ZERO, false);

  @Test
  void matchesOneVanillaTick() {
    TrajectoryMatch match = TrajectoryEnvelope.match(
        START, new MotionVector(0.0, 9.9216, 0.0), false, PROFILE, 0.0);

    assertTrue(match.matched());
    assertEquals(1, match.advancedTicks());
    assertEquals(1, match.predicted().tick());
  }

  @Test
  void matchesAClientPacketThatCoalescesFourTicks() {
    TrajectoryMatch match = TrajectoryEnvelope.match(
        START, new MotionVector(0.0, 9.2315238272, 0.0), false, PROFILE, 0.0);

    assertTrue(match.matched());
    assertEquals(4, match.advancedTicks());
  }

  @Test
  void rejectsMovementBeyondTheBoundedPacketGap() {
    TrajectoryMatch match = TrajectoryEnvelope.match(
        START, new MotionVector(0.0, 8.854893350656, 0.0), false, PROFILE, 0.0);

    assertFalse(match.matched());
  }

  @Test
  void rejectsHorizontalMutationAndEarlyGround() {
    assertFalse(TrajectoryEnvelope.match(
        START, new MotionVector(0.2, 9.9216, 0.0), false, PROFILE, 0.0).matched());
    assertFalse(TrajectoryEnvelope.match(
        START, new MotionVector(0.0, 9.9216, 0.0), true, PROFILE, 0.0).matched());
  }

  @Test
  void usesCollisionToleranceOnlyForTheTerminalSnap() {
    MotionSample nearPlatform = new MotionSample(
        7, new MotionVector(0.0, 0.05, 0.0), MotionVector.ZERO, false);

    TrajectoryMatch match = TrajectoryEnvelope.match(
        nearPlatform, new MotionVector(0.0, 0.04, 0.0), true, PROFILE, 0.0);

    assertTrue(match.matched());
    assertTrue(match.collision());
  }
}
