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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SonarBaselineResistanceTest {

  @Test
  void rejectsAtLeastNinetyNinePercentOfFixedFallOnlyResponders() {
    PhysicsProfile profile = PhysicsProfile.javaModern(0.035, 0.0625, 4);
    int rejected = 0;

    for (int seed = 0; seed < 10_000; ++seed) {
      ChallengeProgram program = ChallengeProgramFactory.create(profile, new Random(seed), 2);
      AdaptiveVerificationSession session = new AdaptiveVerificationSession(
          program, profile, 160, 12_000L, () -> 0L);
      session.start();

      VerificationResult result = VerificationResult.PENDING;
      while (!result.terminal()) {
        ChallengeInstruction instruction = session.currentInstruction();
        result = session.confirmTeleport(instruction.teleportId());
        if (result.terminal()) {
          break;
        }
        result = driveFixedFallOnly(session, instruction, profile);
      }
      if (result != VerificationResult.PASS) {
        ++rejected;
      }
    }

    assertTrue(rejected >= 9_900, "fixed fall responder rejection was " + rejected + "/10000");
  }

  private static VerificationResult driveFixedFallOnly(AdaptiveVerificationSession session,
                                                        ChallengeInstruction instruction,
                                                        PhysicsProfile profile) {
    MotionVector position = instruction.start();
    double deltaY = 0.0;

    for (int tick = 0; tick < 160; ++tick) {
      deltaY = (deltaY - profile.gravity()) * profile.verticalDrag();
      double nextY = position.y() + deltaY;
      boolean collision = nextY <= instruction.platformTopY();
      position = new MotionVector(position.x(), collision ? instruction.platformTopY() : nextY, position.z());

      VerificationResult result = session.move(position, collision);
      if (result != VerificationResult.PENDING) {
        return result;
      }
    }
    return VerificationResult.TIMEOUT;
  }
}
