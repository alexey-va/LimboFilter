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

import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public final class PacketTraceManager {

  private static final int MAX_TARGETS = 4;
  private static final String HANDLER_NAME = "limbofilter-packet-trace";
  private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

  private final Logger logger;

  private volatile TracePolicy policy = TracePolicy.disabled();

  public PacketTraceManager(Logger logger) {
    this.logger = logger;
  }

  public void reload(boolean enabled, List<String> configuredTargets, int maxEvents, long maxMillis) {
    Set<String> normalized = new LinkedHashSet<>();
    int rejected = 0;

    if (configuredTargets != null) {
      for (String configuredTarget : configuredTargets) {
        String target = configuredTarget == null ? "" : configuredTarget.trim();
        if (!PLAYER_NAME.matcher(target).matches() || normalized.size() >= MAX_TARGETS) {
          ++rejected;
          continue;
        }

        normalized.add(target.toLowerCase(Locale.ROOT));
      }
    }

    boolean active = enabled && !normalized.isEmpty();
    this.policy = new TracePolicy(active, Set.copyOf(normalized), maxEvents, maxMillis);
    this.logger.info(
        "PACKET_TRACE_CONFIG status={} target_count={} rejected={} payload_logging=false max_events={} max_millis={}",
        active ? "enabled" : "disabled", normalized.size(), rejected, maxEvents, maxMillis
    );
  }

  public boolean isEnabled(String username) {
    TracePolicy current = this.policy;
    return username != null
        && current.enabled()
        && current.targets().contains(username.toLowerCase(Locale.ROOT));
  }

  public void install(InboundConnection inbound, String username) {
    if (!this.isEnabled(username)) {
      return;
    }

    try {
      LoginInboundConnection loginInbound = (LoginInboundConnection) inbound;
      MethodHandle delegateField = MethodHandles.privateLookupIn(LoginInboundConnection.class, MethodHandles.lookup())
          .findGetter(LoginInboundConnection.class, "delegate", InitialInboundConnection.class);
      InitialInboundConnection initialInbound = (InitialInboundConnection) delegateField.invokeExact(loginInbound);
      this.install(initialInbound.getConnection(), username);
    } catch (Throwable throwable) {
      this.logger.warn(
          "PACKET_TRACE_INSTALL_FAILED player={} reason={}",
          username,
          throwable.getClass().getSimpleName()
      );
    }
  }

  private void install(MinecraftConnection connection, String username) {
    Channel channel = connection.getChannel();
    Runnable install = () -> {
      if (!this.isEnabled(username) || !channel.isActive()) {
        return;
      }

      ChannelPipeline pipeline = channel.pipeline();
      if (pipeline.get(HANDLER_NAME) != null) {
        return;
      }

      ChannelHandlerContext connectionContext = pipeline.context(connection);
      if (connectionContext == null) {
        this.logger.warn(
            "PACKET_TRACE_INSTALL_FAILED player={} reason=missing_minecraft_connection_context",
            username
        );
        return;
      }

      TracePolicy current = this.policy;
      pipeline.addBefore(
          connectionContext.name(),
          HANDLER_NAME,
          new PacketTraceHandler(
              this,
              username,
              current.maxEvents(),
              TimeUnit.MILLISECONDS.toNanos(current.maxMillis()),
              () -> String.valueOf(connection.getState())
          )
      );
    };

    if (channel.eventLoop().inEventLoop()) {
      install.run();
    } else {
      channel.eventLoop().execute(install);
    }
  }

  void log(String message) {
    this.logger.info(message);
  }

  private record TracePolicy(boolean enabled, Set<String> targets, int maxEvents, long maxMillis) {

    private static TracePolicy disabled() {
      return new TracePolicy(false, Set.of(), 1, 1);
    }
  }
}
