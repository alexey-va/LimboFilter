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

public record MotionVector(double x, double y, double z) {

  public static final MotionVector ZERO = new MotionVector(0.0, 0.0, 0.0);

  public MotionVector add(MotionVector another) {
    return new MotionVector(this.x + another.x, this.y + another.y, this.z + another.z);
  }

  public MotionVector scale(double multiplier) {
    return new MotionVector(this.x * multiplier, this.y * multiplier, this.z * multiplier);
  }

  public boolean isFinite() {
    return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
  }

  public double distanceTo(MotionVector another) {
    double deltaX = this.x - another.x;
    double deltaY = this.y - another.y;
    double deltaZ = this.z - another.z;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
  }
}
