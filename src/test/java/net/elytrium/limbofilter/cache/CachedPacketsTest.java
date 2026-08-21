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

package net.elytrium.limbofilter.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.velocitypowered.api.network.ProtocolVersion;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.PacketFactory;
import org.junit.jupiter.api.Test;

class CachedPacketsTest {

  @Test
  void gatesLevelChunksLoadStartToMinecraft1203AndNewer() {
    Object packet = new Object();
    AtomicReference<Integer> reason = new AtomicReference<>();
    AtomicReference<Float> value = new AtomicReference<>();
    AtomicReference<Object> prepared = new AtomicReference<>();
    AtomicReference<ProtocolVersion> minimumVersion = new AtomicReference<>();

    PacketFactory packetFactory = (PacketFactory) Proxy.newProxyInstance(
        PacketFactory.class.getClassLoader(), new Class<?>[]{PacketFactory.class},
        (proxy, method, arguments) -> {
          assertEquals("createChangeGameStatePacket", method.getName());
          reason.set((Integer) arguments[0]);
          value.set((Float) arguments[1]);
          return packet;
        });
    PreparedPacket preparedPacket = (PreparedPacket) Proxy.newProxyInstance(
        PreparedPacket.class.getClassLoader(), new Class<?>[]{PreparedPacket.class},
        (proxy, method, arguments) -> {
          assertEquals("prepare", method.getName());
          prepared.set(arguments[0]);
          minimumVersion.set((ProtocolVersion) arguments[1]);
          return proxy;
        });

    CachedPackets.prepareLevelChunksLoadStart(preparedPacket, packetFactory);

    assertEquals(13, reason.get());
    assertEquals(0.0f, value.get());
    assertSame(packet, prepared.get());
    assertEquals(ProtocolVersion.MINECRAFT_1_20_3, minimumVersion.get());
  }

  @Test
  void keepsThePreviewTitleVisibleForTheWholeConfiguredPreview() {
    assertEquals(160, CachedPackets.memoryGridPreviewTitleStayTicks(8_000));
    assertEquals(161, CachedPackets.memoryGridPreviewTitleStayTicks(8_001));
  }
}
