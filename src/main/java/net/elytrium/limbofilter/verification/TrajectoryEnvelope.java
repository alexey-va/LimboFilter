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

  private static final double MAX_HORIZONTAL_CONTROL_DELTA_PER_TICK = 0.08;

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
      double verticalTolerance = predicted.onGround()
          ? profile.collisionTolerance() : profile.positionTolerance();
      double horizontalTolerance = verticalTolerance;
      if (previous.tick() > 0) {
        horizontalTolerance += MAX_HORIZONTAL_CONTROL_DELTA_PER_TICK * gap;
      }
      if (predicted.onGround() == onGround
          && within(actualPosition, predicted.position(), horizontalTolerance, verticalTolerance)) {
        return new TrajectoryMatch(true, gap,
            acceptedSample(previous, predicted, actualPosition, gap), predicted.onGround());
      }
      if (predicted.onGround()) {
        break;
      }
    }

    return new TrajectoryMatch(false, 0, predicted, false);
  }

  private static MotionSample acceptedSample(MotionSample previous, MotionSample predicted,
                                             MotionVector actualPosition, int gap) {
    MotionVector acceptedVelocity = predicted.velocity();
    if (predicted.onGround()) {
      acceptedVelocity = MotionVector.ZERO;
    } else if (gap == 1) {
      acceptedVelocity = new MotionVector(
          actualPosition.x() - previous.position().x(),
          actualPosition.y() - previous.position().y(),
          actualPosition.z() - previous.position().z());
    }
    return new MotionSample(predicted.tick(), actualPosition, acceptedVelocity, predicted.onGround());
  }

  private static boolean within(MotionVector actual, MotionVector expected,
                                double horizontalTolerance, double verticalTolerance) {
    double deltaX = actual.x() - expected.x();
    double deltaZ = actual.z() - expected.z();
    return Math.hypot(deltaX, deltaZ) <= horizontalTolerance
        && Math.abs(actual.y() - expected.y()) <= verticalTolerance;
  }
}
