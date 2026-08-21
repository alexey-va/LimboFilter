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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import org.junit.jupiter.api.Test;

class AdaptiveLoadingGateTest {

  @Test
  void usesAnOutsideWorldAnchorOnlyForClientsWithTheChunkLoadTracker() {
    assertFalse(AdaptiveLoadingGate.required(ProtocolVersion.MINECRAFT_1_20_2));
    assertTrue(AdaptiveLoadingGate.required(ProtocolVersion.MINECRAFT_1_20_3));
    assertTrue(AdaptiveLoadingGate.required(ProtocolVersion.MINECRAFT_1_21_9));
  }

  @Test
  void reservesANonChallengeTeleportOutsideEveryVanillaDimensionHeight() {
    assertEquals(Integer.MAX_VALUE, AdaptiveLoadingGate.TELEPORT_ID);
    assertTrue(AdaptiveLoadingGate.POSITION.y() > 2_032.0);
    assertTrue(AdaptiveLoadingGate.SETTLE_MILLIS > 0L);
  }
}
