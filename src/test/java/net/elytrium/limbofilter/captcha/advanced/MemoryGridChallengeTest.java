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

import org.junit.jupiter.api.Test;

class MemoryGridChallengeTest {

  @Test
  void acceptsOnlyGroundedTilesAfterTheTeleportNonceIsConfirmed() {
    MemoryGridChallenge challenge = MemoryGridChallenge.fromAnswer("WALK:1,4,7", 91);

    assertEquals(MemoryGridChallenge.Result.IGNORED,
        challenge.move(0.5, 80.0, -2.5, true));
    MemoryGridChallenge.Traversal traversal = challenge.beginTraversal();
    assertEquals(91, traversal.teleportId());
    assertEquals(0.5, traversal.x());
    assertEquals(80.0, traversal.y());
    assertEquals(-6.5, traversal.z());
    assertEquals(MemoryGridChallenge.Result.PENDING, challenge.confirmTeleport(91));

    assertEquals(MemoryGridChallenge.Result.IGNORED,
        challenge.move(0.5, 80.0, -6.5, true));
    assertEquals(MemoryGridChallenge.Result.IGNORED,
        challenge.move(0.5, 80.0, -2.5, false));
    assertEquals(MemoryGridChallenge.Result.PENDING,
        challenge.move(0.5, 80.0, -2.5, true));
    assertEquals(MemoryGridChallenge.Result.IGNORED,
        challenge.move(0.5, 80.0, -2.5, true));
    assertEquals(MemoryGridChallenge.Result.PENDING,
        challenge.move(0.5, 80.0, 0.5, true));
    assertEquals(MemoryGridChallenge.Result.PASSED,
        challenge.move(0.5, 80.0, 3.5, true));
  }

  @Test
  void rejectsAReplayedOrWrongTeleportNonce() {
    MemoryGridChallenge challenge = MemoryGridChallenge.fromAnswer("WALK:1,4,7", 91);
    challenge.beginTraversal();

    assertEquals(MemoryGridChallenge.Result.FAILED_PROTOCOL, challenge.confirmTeleport(92));
    assertEquals(MemoryGridChallenge.Result.IGNORED, challenge.confirmTeleport(91));
  }

  @Test
  void failsWhenThePlayerStepsOnAnotherGridTile() {
    MemoryGridChallenge challenge = MemoryGridChallenge.fromAnswer("WALK:1,4,7", 91);
    challenge.beginTraversal();
    challenge.confirmTeleport(91);

    assertEquals(MemoryGridChallenge.Result.FAILED,
        challenge.move(3.5, 80.0, -2.5, true));
    assertEquals(MemoryGridChallenge.Result.IGNORED,
        challenge.move(0.5, 80.0, -2.5, true));
  }
}
