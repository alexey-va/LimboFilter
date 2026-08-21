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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeProgramFactoryTest {

  @Test
  void producesAReproducibleBoundedModernProgram() {
    PhysicsProfile profile = PhysicsProfile.javaModern(0.035, 0.0625, 4);
    ChallengeProgram first = ChallengeProgramFactory.create(profile, new Random(8128L), 3);
    ChallengeProgram second = ChallengeProgramFactory.create(profile, new Random(8128L), 3);

    assertEquals(first, second);
    assertEquals(3, first.instructions().size());
    assertTrue(first.instructions().stream().anyMatch(i -> i.phase() == ChallengePhase.IMPULSE_COLLISION));

    Set<Integer> teleportIds = new HashSet<>();
    Set<MotionVector> starts = new HashSet<>();
    for (ChallengeInstruction instruction : first.instructions()) {
      assertTrue(teleportIds.add(instruction.teleportId()));
      assertTrue(starts.add(instruction.start()));
      assertTrue(Math.abs(instruction.start().x()) <= 12.0);
      assertTrue(Math.abs(instruction.start().z()) <= 12.0);
      assertTrue(instruction.platformTopY() >= 96.0 && instruction.platformTopY() <= 110.0);
      assertTrue(instruction.start().y() - instruction.platformTopY() >= 6.0);
      assertTrue(instruction.start().y() - instruction.platformTopY() <= 10.0);
      assertEquals(4.5, instruction.platformHalfWidth());
      assertTrue(instruction.initialVelocity().isFinite());
    }
  }

  @Test
  void changesTheProgramWhenTheSessionSeedChanges() {
    PhysicsProfile profile = PhysicsProfile.javaModern(0.035, 0.0625, 4);

    assertNotEquals(
        ChallengeProgramFactory.create(profile, new Random(1L), 2),
        ChallengeProgramFactory.create(profile, new Random(2L), 2)
    );
  }

  @Test
  void neverSendsAnImpulseToLegacyProtocols() {
    ChallengeProgram program = ChallengeProgramFactory.create(
        PhysicsProfile.javaLegacy(0.035, 0.0625, 4), new Random(3L), 3);

    assertFalse(program.instructions().stream().anyMatch(i -> i.phase() == ChallengePhase.IMPULSE_COLLISION));
  }

  @Test
  void includesEveryChunkTouchedByAnAdaptivePlatform() {
    Set<ChallengeProgramFactory.ChunkCoordinate> expected = Set.of(
        new ChallengeProgramFactory.ChunkCoordinate(-1, -1),
        new ChallengeProgramFactory.ChunkCoordinate(-1, 0),
        new ChallengeProgramFactory.ChunkCoordinate(-1, 1),
        new ChallengeProgramFactory.ChunkCoordinate(0, -1),
        new ChallengeProgramFactory.ChunkCoordinate(0, 0),
        new ChallengeProgramFactory.ChunkCoordinate(0, 1),
        new ChallengeProgramFactory.ChunkCoordinate(1, -1),
        new ChallengeProgramFactory.ChunkCoordinate(1, 0),
        new ChallengeProgramFactory.ChunkCoordinate(1, 1)
    );

    assertEquals(expected, ChallengeProgramFactory.platformChunks());
  }
}
