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

import com.velocitypowered.api.network.ProtocolVersion;

public final class AdaptiveLoadingGate {

  public static final int TELEPORT_ID = Integer.MAX_VALUE;
  public static final MotionVector POSITION = new MotionVector(0.0, 4096.0, 0.0);
  public static final long SETTLE_MILLIS = 150L;

  private AdaptiveLoadingGate() {
  }

  public static boolean required(ProtocolVersion version) {
    return version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3);
  }
}
