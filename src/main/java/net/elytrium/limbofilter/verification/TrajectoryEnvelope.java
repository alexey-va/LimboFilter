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

public final class TrajectoryEnvelope {

  private TrajectoryEnvelope() {
  }

  public static TrajectoryMatch match(MotionSample previous, MotionVector actualPosition, boolean onGround,
                                      PhysicsProfile profile, double platformTopY) {
    if (!actualPosition.isFinite()) {
      return new TrajectoryMatch(false, 0, previous, false);
    }

    MotionSample predicted = previous;
    for (int gap = 1; gap <= profile.maxPacketGapTicks(); ++gap) {
      predicted = VanillaPhysics.next(predicted, profile, platformTopY);
      double tolerance = predicted.onGround() ? profile.collisionTolerance() : profile.positionTolerance();
      if (predicted.onGround() == onGround && within(actualPosition, predicted.position(), tolerance)) {
        return new TrajectoryMatch(true, gap, predicted, predicted.onGround());
      }
      if (predicted.onGround()) {
        break;
      }
    }

    return new TrajectoryMatch(false, 0, predicted, false);
  }

  private static boolean within(MotionVector actual, MotionVector expected, double tolerance) {
    return Math.abs(actual.x() - expected.x()) <= tolerance
        && Math.abs(actual.y() - expected.y()) <= tolerance
        && Math.abs(actual.z() - expected.z()) <= tolerance;
  }
}
