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

import java.util.Objects;

public record ChallengeInstruction(ChallengePhase phase, int teleportId, MotionVector start,
                                   MotionVector initialVelocity, double platformTopY,
                                   double platformHalfWidth) {

  public ChallengeInstruction {
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(initialVelocity, "initialVelocity");
    if (teleportId <= 0) {
      throw new IllegalArgumentException("teleportId must be positive");
    }
    if (!start.isFinite() || !initialVelocity.isFinite() || !Double.isFinite(platformTopY)) {
      throw new IllegalArgumentException("challenge coordinates and velocity must be finite");
    }
    if (!Double.isFinite(platformHalfWidth) || platformHalfWidth <= 0.0) {
      throw new IllegalArgumentException("platformHalfWidth must be finite and positive");
    }
    if (start.y() <= platformTopY) {
      throw new IllegalArgumentException("challenge must start above the platform");
    }
    if (phase == ChallengePhase.FALL_COLLISION && !initialVelocity.equals(MotionVector.ZERO)) {
      throw new IllegalArgumentException("fall challenge cannot have an initial impulse");
    }
    if (phase == ChallengePhase.IMPULSE_COLLISION && initialVelocity.equals(MotionVector.ZERO)) {
      throw new IllegalArgumentException("impulse challenge requires a non-zero velocity");
    }
  }
}
