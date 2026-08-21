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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.stream.Stream;
import net.elytrium.limbofilter.verification.MotionVector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdaptivePositionTest {

  @ParameterizedTest
  @MethodSource("modernVersions")
  void encodesAbsolutePositionVelocityAndNonce(ProtocolVersion version) {
    AdaptivePosition packet = new AdaptivePosition(
        918_273, new MotionVector(4.25, 117.5, -8.75),
        new MotionVector(0.15, 0.30, -0.20), 73.0F, -12.5F);
    ByteBuf buffer = Unpooled.buffer();

    packet.encode(buffer, Direction.CLIENTBOUND, version);

    assertEquals(918_273, ProtocolUtils.readVarInt(buffer));
    assertEquals(4.25, buffer.readDouble());
    assertEquals(117.5, buffer.readDouble());
    assertEquals(-8.75, buffer.readDouble());
    assertEquals(0.15, buffer.readDouble());
    assertEquals(0.30, buffer.readDouble());
    assertEquals(-0.20, buffer.readDouble());
    assertEquals(73.0F, buffer.readFloat());
    assertEquals(-12.5F, buffer.readFloat());
    assertEquals(0, buffer.readInt());
    assertEquals(0, buffer.readableBytes());
    buffer.release();
  }

  private static Stream<ProtocolVersion> modernVersions() {
    return Stream.of(
        ProtocolVersion.MINECRAFT_1_21_2,
        ProtocolVersion.MINECRAFT_1_21_5,
        ProtocolVersion.MINECRAFT_1_21_9,
        ProtocolVersion.MINECRAFT_26_1
    );
  }
}
