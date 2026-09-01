/*
 * Copyright (C) 2026 RusCrafting
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

package net.elytrium.limbofilter.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class PacketTraceHandlerTest {

  @Test
  void installsIntoTheVelocityLoginConnectionPipeline() throws Exception {
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(new ArrayList<>()));
    manager.reload(true, List.of("IIILV"), 10, 60_000);

    EmbeddedChannel channel = new EmbeddedChannel();
    MinecraftConnection connection = new MinecraftConnection(channel, null);
    channel.pipeline().addLast("minecraft-connection", connection);

    Constructor<InitialInboundConnection> initialConstructor = InitialInboundConnection.class
        .getDeclaredConstructor(MinecraftConnection.class, String.class,
            com.velocitypowered.proxy.protocol.packet.HandshakePacket.class);
    initialConstructor.setAccessible(true);
    InitialInboundConnection initial = initialConstructor.newInstance(connection, "", null);
    Constructor<LoginInboundConnection> loginConstructor = LoginInboundConnection.class
        .getDeclaredConstructor(InitialInboundConnection.class);
    loginConstructor.setAccessible(true);

    manager.install(loginConstructor.newInstance(initial), "IIILV");
    channel.runPendingTasks();

    PacketTraceHandler handler = channel.pipeline().toMap().values().stream()
        .filter(PacketTraceHandler.class::isInstance)
        .map(PacketTraceHandler.class::cast)
        .findFirst()
        .orElse(null);
    assertNotNull(handler);

    channel.pipeline().remove(handler);
    channel.pipeline().remove(connection);
    channel.finishAndReleaseAll();
  }

  @Test
  void tracesBothDirectionsWithoutRenderingPayload() {
    List<String> logs = new ArrayList<>();
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(logs));
    manager.reload(true, List.of("IIILV"), 10, 60_000);

    PacketTraceHandler handler = new PacketTraceHandler(
        manager, "IIILV", 10, TimeUnit.MINUTES.toNanos(1), () -> "PLAY");
    EmbeddedChannel channel = new EmbeddedChannel(handler);

    channel.writeInbound(new SensitivePacket("client-secret"));
    channel.writeOutbound(new SensitivePacket("server-secret"));

    String joined = String.join("\n", logs);
    assertTrue(joined.contains("direction=client_to_proxy"));
    assertTrue(joined.contains("direction=proxy_to_client"));
    assertTrue(joined.contains("packet=SensitivePacket"));
    assertFalse(joined.contains("client-secret"));
    assertFalse(joined.contains("server-secret"));
    channel.finishAndReleaseAll();
  }

  @Test
  void disablingTargetRemovesHandlerOnNextPacket() {
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(new ArrayList<>()));
    manager.reload(true, List.of("IIILV"), 10, 60_000);

    PacketTraceHandler handler = new PacketTraceHandler(
        manager, "IIILV", 10, TimeUnit.MINUTES.toNanos(1), () -> "PLAY");
    EmbeddedChannel channel = new EmbeddedChannel(handler);
    manager.reload(false, List.of("IIILV"), 10, 60_000);
    channel.writeInbound(new SensitivePacket("not-logged"));

    assertNull(channel.pipeline().context(handler));
    channel.finishAndReleaseAll();
  }

  @Test
  void eventLimitStopsTrace() {
    List<String> logs = new ArrayList<>();
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(logs));
    manager.reload(true, List.of("IIILV"), 2, 60_000);

    PacketTraceHandler handler = new PacketTraceHandler(
        manager, "IIILV", 2, TimeUnit.MINUTES.toNanos(1), () -> "PLAY");
    EmbeddedChannel channel = new EmbeddedChannel(handler);
    channel.writeInbound(new SensitivePacket("one"));
    channel.writeInbound(new SensitivePacket("two"));

    assertNull(channel.pipeline().context(handler));
    assertTrue(String.join("\n", logs).contains("reason=event_limit events=2"));
    channel.finishAndReleaseAll();
  }

  @Test
  void durationLimitStopsTrace() {
    List<String> logs = new ArrayList<>();
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(logs));
    manager.reload(true, List.of("IIILV"), 10, 60_000);

    PacketTraceHandler handler = new PacketTraceHandler(manager, "IIILV", 10, 1, () -> "PLAY");
    EmbeddedChannel channel = new EmbeddedChannel(handler);
    channel.writeInbound(new SensitivePacket("not-logged"));

    assertNull(channel.pipeline().context(handler));
    assertTrue(String.join("\n", logs).contains("reason=duration_limit events=0"));
    channel.finishAndReleaseAll();
  }

  @Test
  void targetsAreCaseInsensitiveValidatedAndBounded() {
    PacketTraceManager manager = new PacketTraceManager(capturingLogger(new ArrayList<>()));
    manager.reload(true, List.of("IIILV", "GrocerMC", "third", "fourth", "fifth", "bad name"), 10, 60_000);

    assertTrue(manager.isEnabled("iiilv"));
    assertTrue(manager.isEnabled("GROCermc"));
    assertTrue(manager.isEnabled("fourth"));
    assertFalse(manager.isEnabled("fifth"));
    assertFalse(manager.isEnabled("bad name"));
  }

  private static Logger capturingLogger(List<String> logs) {
    return (Logger) Proxy.newProxyInstance(
        Logger.class.getClassLoader(),
        new Class<?>[]{Logger.class},
        (proxy, method, args) -> {
          if (method.getName().equals("info") && args != null && args.length > 0) {
            logs.add(String.valueOf(args[0]));
          }
          return null;
        }
    );
  }

  private static final class SensitivePacket {

    private final String secret;

    private SensitivePacket(String secret) {
      this.secret = secret;
    }

    @Override
    public String toString() {
      return this.secret;
    }
  }
}
