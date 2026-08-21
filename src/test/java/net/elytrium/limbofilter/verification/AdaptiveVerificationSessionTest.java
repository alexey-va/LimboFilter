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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AdaptiveVerificationSessionTest {

  private static final PhysicsProfile PROFILE = PhysicsProfile.javaModern(0.035, 0.0625, 4);

  @Test
  void acceptsAHandCheckedVanillaFall() {
    ChallengeInstruction instruction = new ChallengeInstruction(
        ChallengePhase.FALL_COLLISION, 123, new MotionVector(0.0, 1.0, 0.0), MotionVector.ZERO, 0.0, 2.0);
    AdaptiveVerificationSession session = session(instruction);

    assertEquals(instruction, session.start());
    assertEquals(VerificationResult.PENDING, session.confirmTeleport(123));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.0, 0.9216, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.0, 0.766368, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.0, 0.53584064, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.0, 0.2315238272, 0.0), false));
    assertEquals(VerificationResult.PASS, session.move(new MotionVector(0.0, 0.0, 0.0), true));
  }

  @Test
  void acceptsAHandCheckedHorizontalAndVerticalImpulse() {
    ChallengeInstruction instruction = new ChallengeInstruction(
        ChallengePhase.IMPULSE_COLLISION, 321, new MotionVector(0.0, 0.5, 0.0),
        new MotionVector(0.2, 0.2, 0.0), 0.0, 2.0);
    AdaptiveVerificationSession session = session(instruction);

    session.start();
    assertEquals(VerificationResult.PENDING, session.confirmTeleport(321));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.182, 0.6176, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.34762, 0.654448, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.4983342, 0.61215904, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.635484122, 0.4923158592, 0.0), false));
    assertEquals(VerificationResult.PENDING, session.move(new MotionVector(0.76029055102, 0.296469542016, 0.0), false));
    assertEquals(VerificationResult.PENDING,
        session.move(new MotionVector(0.8738644014282, 0.02614015117568, 0.0), false));
    assertEquals(VerificationResult.PASS,
        session.move(new MotionVector(0.977216605299662, 0.0, 0.0), true));
  }

  @Test
  void advancesOnlyAfterCollisionAndRequiresTheNextNonce() {
    ChallengeInstruction first = new ChallengeInstruction(
        ChallengePhase.FALL_COLLISION, 1, new MotionVector(0.0, 0.05, 0.0), MotionVector.ZERO, 0.0, 2.0);
    ChallengeInstruction second = new ChallengeInstruction(
        ChallengePhase.FALL_COLLISION, 2, new MotionVector(4.0, 0.05, 4.0), MotionVector.ZERO, 0.0, 2.0);
    AdaptiveVerificationSession session = new AdaptiveVerificationSession(
        new ChallengeProgram(List.of(first, second)), PROFILE, 160, 12_000L, () -> 0L);

    session.start();
    session.confirmTeleport(1);
    assertEquals(VerificationResult.PHASE_PASSED, session.move(new MotionVector(0.0, 0.0, 0.0), true));
    assertEquals(second, session.currentInstruction());
    assertEquals(VerificationResult.FAIL_PROTOCOL, session.move(new MotionVector(4.0, 0.0, 4.0), true));
  }

  @Test
  void rejectsProtocolReplayAndInvalidMotion() {
    ChallengeInstruction instruction = new ChallengeInstruction(
        ChallengePhase.FALL_COLLISION, 44, new MotionVector(0.0, 1.0, 0.0), MotionVector.ZERO, 0.0, 2.0);

    AdaptiveVerificationSession wrongNonce = session(instruction);
    wrongNonce.start();
    assertEquals(VerificationResult.FAIL_PROTOCOL, wrongNonce.confirmTeleport(45));

    AdaptiveVerificationSession replay = session(instruction);
    replay.start();
    replay.confirmTeleport(44);
    assertEquals(VerificationResult.FAIL_REPLAY, replay.confirmTeleport(44));

    AdaptiveVerificationSession nonFinite = session(instruction);
    nonFinite.start();
    nonFinite.confirmTeleport(44);
    assertEquals(VerificationResult.FAIL_TRAJECTORY,
        nonFinite.move(new MotionVector(Double.NaN, 0.9, 0.0), false));
  }

  @Test
  void distinguishesCollisionFailureAndTimeout() {
    ChallengeInstruction instruction = new ChallengeInstruction(
        ChallengePhase.FALL_COLLISION, 88, new MotionVector(0.0, 0.05, 0.0), MotionVector.ZERO, 0.0, 1.0);

    AdaptiveVerificationSession collision = session(instruction);
    collision.start();
    collision.confirmTeleport(88);
    assertEquals(VerificationResult.FAIL_COLLISION,
        collision.move(new MotionVector(2.0, 0.0, 0.0), true));

    AtomicLong clock = new AtomicLong(100L);
    AdaptiveVerificationSession timeout = new AdaptiveVerificationSession(
        new ChallengeProgram(List.of(instruction)), PROFILE, 160, 12_000L, clock::get);
    timeout.start();
    timeout.confirmTeleport(88);
    clock.set(12_101L);
    assertEquals(VerificationResult.TIMEOUT,
        timeout.move(new MotionVector(0.0, 0.0, 0.0), true));
  }

  private static AdaptiveVerificationSession session(ChallengeInstruction instruction) {
    return new AdaptiveVerificationSession(
        new ChallengeProgram(List.of(instruction)), PROFILE, 160, 12_000L, () -> 0L);
  }
}
