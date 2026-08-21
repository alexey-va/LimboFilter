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
  void acceptsBoundedVanillaControlAfterTheInitialImpulseAndRebasesMotion() {
    MotionSample firstImpulseTick = new MotionSample(
        1,
        new MotionVector(4.1997997579483535, 115.31783333426651, -4.140488608596645),
        new MotionVector(0.1997997579483533, 0.3178333342665067, -0.14048860859664447),
        false);
    MotionVector vanillaPosition = new MotionVector(
        4.308890438459324, 115.550910006384, -4.217195397800108);

    TrajectoryMatch match = TrajectoryEnvelope.match(
        firstImpulseTick, vanillaPosition, false, PROFILE, 108.0);

    assertTrue(match.matched());
    assertEquals(vanillaPosition, match.predicted().position());
    assertEquals(new MotionVector(
        vanillaPosition.x() - firstImpulseTick.position().x(),
        vanillaPosition.y() - firstImpulseTick.position().y(),
        vanillaPosition.z() - firstImpulseTick.position().z()), match.predicted().velocity());
  }

  @Test
  void rejectsUnboundedHorizontalControlAfterTheInitialImpulse() {
    MotionSample firstImpulseTick = new MotionSample(
        1, new MotionVector(0.2, 10.2, 0.0), new MotionVector(0.2, 0.2, 0.0), false);

    assertFalse(TrajectoryEnvelope.match(
        firstImpulseTick, new MotionVector(1.5, 10.3176, 0.0), false, PROFILE, 0.0).matched());
  }

  @Test
  void rebasesAcceptedDriftBeforeTheNextLateFallSample() {
    MotionVector tick19Velocity = new MotionVector(
        -0.035728734275078784, -0.9637907774805827, 0.030817970994509437);
    MotionVector tick19PredictedPosition = new MotionVector(
        -5.8065969468444045, 109.30126605207413, 5.558287844119194);
    MotionSample tick18 = new MotionSample(
        18,
        new MotionVector(
            tick19PredictedPosition.x() - tick19Velocity.x(),
            tick19PredictedPosition.y() - tick19Velocity.y(),
            tick19PredictedPosition.z() - tick19Velocity.z()),
        new MotionVector(
            tick19Velocity.x() / PROFILE.horizontalDrag(),
            tick19Velocity.y() / PROFILE.verticalDrag() + PROFILE.gravity(),
            tick19Velocity.z() / PROFILE.horizontalDrag()),
        false);
    MotionVector tick19ActualPosition = new MotionVector(
        tick19PredictedPosition.x(), tick19PredictedPosition.y() - 0.034, tick19PredictedPosition.z());

    TrajectoryMatch tick19 = TrajectoryEnvelope.match(
        tick18, tick19ActualPosition, false, PROFILE, 106.0);
    TrajectoryMatch tick20 = TrajectoryEnvelope.match(
        tick19.predicted(),
        new MotionVector(-5.8391104414654045, 108.24293239114046, 5.5863324965394465),
        false, PROFILE, 106.0);

    assertTrue(tick19.matched());
    assertTrue(tick20.matched());
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
