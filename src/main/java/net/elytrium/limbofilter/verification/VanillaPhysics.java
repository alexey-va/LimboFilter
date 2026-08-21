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

public final class VanillaPhysics {

  private VanillaPhysics() {
  }

  public static MotionSample next(MotionSample previous, PhysicsProfile profile, double platformTopY) {
    if (!previous.position().isFinite() || !previous.velocity().isFinite() || !Double.isFinite(platformTopY)) {
      throw new IllegalArgumentException("motion and platform coordinates must be finite");
    }

    if (previous.onGround()) {
      return new MotionSample(previous.tick() + 1, previous.position(), MotionVector.ZERO, true);
    }

    MotionVector velocity = previous.tick() == 0 && !previous.velocity().equals(MotionVector.ZERO)
        ? previous.velocity()
        : new MotionVector(
            previous.velocity().x() * profile.horizontalDrag(),
            (previous.velocity().y() - profile.gravity()) * profile.verticalDrag(),
            previous.velocity().z() * profile.horizontalDrag()
        );
    MotionVector position = previous.position().add(velocity);

    if (previous.position().y() >= platformTopY && position.y() <= platformTopY) {
      return new MotionSample(previous.tick() + 1,
          new MotionVector(position.x(), platformTopY, position.z()), MotionVector.ZERO, true);
    }

    return new MotionSample(previous.tick() + 1, position, velocity, false);
  }
}
