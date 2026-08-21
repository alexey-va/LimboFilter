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

package net.elytrium.limbofilter.protocol.packets;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.elytrium.limbofilter.verification.MotionVector;

public final class AdaptivePosition implements MinecraftPacket {

  private final int teleportId;
  private final MotionVector position;
  private final MotionVector velocity;
  private final float yaw;
  private final float pitch;

  public AdaptivePosition(int teleportId, MotionVector position, MotionVector velocity,
                          float yaw, float pitch) {
    if (teleportId <= 0) {
      throw new IllegalArgumentException("teleportId must be positive");
    }
    this.position = Objects.requireNonNull(position, "position");
    this.velocity = Objects.requireNonNull(velocity, "velocity");
    if (!position.isFinite() || !velocity.isFinite() || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
      throw new IllegalArgumentException("adaptive position fields must be finite");
    }
    this.teleportId = teleportId;
    this.yaw = yaw;
    this.pitch = pitch;
  }

  @Override
  public void decode(ByteBuf buffer, ProtocolUtils.Direction direction, ProtocolVersion version) {
    throw new IllegalStateException("clientbound packet cannot be decoded");
  }

  @Override
  public void encode(ByteBuf buffer, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      throw new IllegalArgumentException("adaptive velocity packet requires protocol 1.21.2+");
    }
    ProtocolUtils.writeVarInt(buffer, this.teleportId);
    buffer.writeDouble(this.position.x());
    buffer.writeDouble(this.position.y());
    buffer.writeDouble(this.position.z());
    buffer.writeDouble(this.velocity.x());
    buffer.writeDouble(this.velocity.y());
    buffer.writeDouble(this.velocity.z());
    buffer.writeFloat(this.yaw);
    buffer.writeFloat(this.pitch);
    buffer.writeInt(0);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return true;
  }
}
