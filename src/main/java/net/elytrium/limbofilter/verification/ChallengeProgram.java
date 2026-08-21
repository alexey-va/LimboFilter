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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ChallengeProgram(List<ChallengeInstruction> instructions) {

  public ChallengeProgram {
    Objects.requireNonNull(instructions, "instructions");
    instructions = List.copyOf(instructions);
    if (instructions.isEmpty() || instructions.size() > 3) {
      throw new IllegalArgumentException("a challenge program must contain 1..3 phases");
    }

    Set<Integer> teleportIds = new HashSet<>();
    Set<MotionVector> starts = new HashSet<>();
    for (ChallengeInstruction instruction : instructions) {
      if (!teleportIds.add(instruction.teleportId())) {
        throw new IllegalArgumentException("teleport IDs must be unique within a program");
      }
      if (!starts.add(instruction.start())) {
        throw new IllegalArgumentException("challenge starts must be unique within a program");
      }
    }
  }
}
