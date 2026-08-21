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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class ChallengeProgramFactory {

  private static final List<PlatformSlot> PLATFORM_SLOTS = List.of(
      new PlatformSlot(-12.0, 96.0, -12.0),
      new PlatformSlot(-12.0, 98.0, 12.0),
      new PlatformSlot(12.0, 100.0, -12.0),
      new PlatformSlot(12.0, 102.0, 12.0),
      new PlatformSlot(-4.0, 104.0, -4.0),
      new PlatformSlot(-4.0, 106.0, 4.0),
      new PlatformSlot(4.0, 108.0, -4.0),
      new PlatformSlot(4.0, 110.0, 4.0)
  );

  private ChallengeProgramFactory() {
  }

  public static ChallengeProgram create(PhysicsProfile profile, RandomGenerator random, int phases) {
    if (phases < 2 || phases > 3) {
      throw new IllegalArgumentException("phases must be in range 2..3");
    }

    List<PlatformSlot> availableSlots = new ArrayList<>(PLATFORM_SLOTS);
    List<ChallengeInstruction> instructions = new ArrayList<>(phases);
    Set<Integer> teleportIds = new HashSet<>();
    int mandatoryImpulse = profile.impulseSupported() ? random.nextInt(phases) : -1;

    for (int index = 0; index < phases; ++index) {
      PlatformSlot slot = availableSlots.remove(random.nextInt(availableSlots.size()));
      ChallengePhase phase = choosePhase(profile, random, index, mandatoryImpulse);
      int teleportId = nextUniqueTeleportId(random, teleportIds);
      double startY = slot.platformTopY + random.nextInt(6, 11);
      MotionVector velocity = phase == ChallengePhase.IMPULSE_COLLISION
          ? randomImpulse(random) : MotionVector.ZERO;
      instructions.add(new ChallengeInstruction(
          phase, teleportId, new MotionVector(slot.x, startY, slot.z), velocity,
          slot.platformTopY, 4.5));
    }

    return new ChallengeProgram(instructions);
  }

  public static List<MotionVector> platformCenters() {
    return PLATFORM_SLOTS.stream()
        .map(slot -> new MotionVector(slot.x, slot.platformTopY, slot.z))
        .toList();
  }

  private static ChallengePhase choosePhase(PhysicsProfile profile, RandomGenerator random,
                                            int index, int mandatoryImpulse) {
    if (!profile.impulseSupported()) {
      return ChallengePhase.FALL_COLLISION;
    }
    if (index == mandatoryImpulse || random.nextBoolean()) {
      return ChallengePhase.IMPULSE_COLLISION;
    }
    return ChallengePhase.FALL_COLLISION;
  }

  private static int nextUniqueTeleportId(RandomGenerator random, Set<Integer> used) {
    int teleportId;
    do {
      teleportId = random.nextInt(1, Integer.MAX_VALUE);
    } while (!used.add(teleportId));
    return teleportId;
  }

  private static MotionVector randomImpulse(RandomGenerator random) {
    return new MotionVector(
        signed(random, 0.12 + random.nextDouble() * 0.08),
        0.25 + random.nextDouble() * 0.10,
        signed(random, 0.12 + random.nextDouble() * 0.08)
    );
  }

  private static double signed(RandomGenerator random, double value) {
    return random.nextBoolean() ? value : -value;
  }

  private record PlatformSlot(double x, double platformTopY, double z) {
  }
}
