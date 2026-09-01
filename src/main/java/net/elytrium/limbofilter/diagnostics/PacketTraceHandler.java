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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class PacketTraceHandler extends ChannelDuplexHandler {

  private final PacketTraceManager manager;
  private final String username;
  private final int maxEvents;
  private final long maxDurationNanos;
  private final Supplier<String> state;

  private long startedNanos;
  private long lastEventNanos;
  private int sequence;
  private boolean stopped;

  PacketTraceHandler(PacketTraceManager manager, String username, int maxEvents,
                     long maxDurationNanos, Supplier<String> state) {
    if (maxEvents <= 0 || maxDurationNanos <= 0) {
      throw new IllegalArgumentException("packet trace limits must be positive");
    }

    this.manager = manager;
    this.username = username;
    this.maxEvents = maxEvents;
    this.maxDurationNanos = maxDurationNanos;
    this.state = state;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    this.startedNanos = System.nanoTime();
    this.lastEventNanos = this.startedNanos;
    this.manager.log(
        "PACKET_TRACE_START player=" + this.username
            + " payload_logging=false max_events=" + this.maxEvents
            + " max_millis=" + TimeUnit.NANOSECONDS.toMillis(this.maxDurationNanos)
    );
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
    this.trace(ctx, "client_to_proxy", message);
    super.channelRead(ctx, message);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
    this.trace(ctx, "proxy_to_client", message);
    super.write(ctx, message, promise);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    this.stop(ctx, "disconnected");
    super.channelInactive(ctx);
  }

  private void trace(ChannelHandlerContext ctx, String direction, Object message) {
    if (this.stopped) {
      return;
    }
    if (!this.manager.isEnabled(this.username)) {
      this.stop(ctx, "disabled");
      return;
    }

    long now = System.nanoTime();
    if (now - this.startedNanos >= this.maxDurationNanos) {
      this.stop(ctx, "duration_limit");
      return;
    }

    ++this.sequence;
    this.manager.log(
        "PACKET_TRACE player=" + this.username
            + " direction=" + direction
            + " seq=" + this.sequence
            + " elapsed_ms=" + TimeUnit.NANOSECONDS.toMillis(now - this.startedNanos)
            + " gap_ms=" + TimeUnit.NANOSECONDS.toMillis(now - this.lastEventNanos)
            + " state=" + this.safeState()
            + " packet=" + packetType(message)
            + " size_bytes=" + readableBytes(message)
    );
    this.lastEventNanos = now;

    if (this.sequence >= this.maxEvents) {
      this.stop(ctx, "event_limit");
    }
  }

  private String safeState() {
    try {
      return this.state.get();
    } catch (RuntimeException ignored) {
      return "unknown";
    }
  }

  private void stop(ChannelHandlerContext ctx, String reason) {
    if (this.stopped) {
      return;
    }

    this.stopped = true;
    this.manager.log(
        "PACKET_TRACE_STOP player=" + this.username
            + " reason=" + reason
            + " events=" + this.sequence
            + " elapsed_ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.startedNanos)
    );
    if (ctx.pipeline().context(this) != null) {
      ctx.pipeline().remove(this);
    }
  }

  private static String packetType(Object message) {
    if (message == null) {
      return "null";
    }

    String simpleName = message.getClass().getSimpleName();
    return simpleName.isEmpty() ? message.getClass().getName() : simpleName;
  }

  private static int readableBytes(Object message) {
    if (message instanceof ByteBuf byteBuf) {
      return byteBuf.readableBytes();
    }
    if (message instanceof ByteBufHolder holder) {
      return holder.content().readableBytes();
    }

    return -1;
  }
}
