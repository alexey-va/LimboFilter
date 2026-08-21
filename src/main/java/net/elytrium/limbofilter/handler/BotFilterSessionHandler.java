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

package net.elytrium.limbofilter.handler;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.CaptchaHolder;
import net.elytrium.limbofilter.captcha.advanced.InteractiveCaptchaSession;
import net.elytrium.limbofilter.listener.TcpListener;
import net.elytrium.limbofilter.protocol.data.EntityMetadata;
import net.elytrium.limbofilter.protocol.data.ItemFrame;
import net.elytrium.limbofilter.protocol.packets.AdaptivePosition;
import net.elytrium.limbofilter.protocol.packets.Interact;
import net.elytrium.limbofilter.protocol.packets.SetEntityMetadata;
import net.elytrium.limbofilter.stats.Statistics;
import net.elytrium.limbofilter.verification.AdaptiveLoadingGate;
import net.elytrium.limbofilter.verification.AdaptiveMode;
import net.elytrium.limbofilter.verification.AdaptiveModeSelector;
import net.elytrium.limbofilter.verification.AdaptivePacketTraceBudget;
import net.elytrium.limbofilter.verification.AdaptiveVerificationSession;
import net.elytrium.limbofilter.verification.ChallengeInstruction;
import net.elytrium.limbofilter.verification.ChallengeProgram;
import net.elytrium.limbofilter.verification.ChallengeProgramFactory;
import net.elytrium.limbofilter.verification.MotionVector;
import net.elytrium.limbofilter.verification.PhysicsProfile;
import net.elytrium.limbofilter.verification.VerificationResult;

public class BotFilterSessionHandler implements LimboSessionHandler {

  private static final double[] LOADED_CHUNK_SPEED_CACHE = new double[Settings.IMP.MAIN.FALLING_CHECK_TICKS];
  private static long FALLING_CHECK_TOTAL_TIME;

  private final Map<Integer, Integer> frameRotation = new HashMap<>();
  private final Player proxyPlayer;
  private final ProtocolVersion version;
  private final LimboFilter plugin;
  private final Statistics statistics;
  private final int validX;
  private final int validY;
  private final int validZ;
  private final int validTeleportId;
  private final AdaptiveMode adaptiveMode;
  private final AdaptiveVerificationSession adaptiveSession;
  private final boolean adaptiveDiagnosticsEnabled;
  private final AdaptivePacketTraceBudget adaptivePacketTraceBudget;

  private double posX;
  private double posY;
  private double lastY;
  private double posZ;
  private int waitingTeleportId;
  private boolean onGround;

  private int ticks = 1;
  private int ignoredTicks;

  private long joinTime;
  private ScheduledFuture<?> filterMainTask;

  private CheckState state;
  private LimboPlayer player;
  private Limbo server;
  private String captchaAnswer;
  private InteractiveCaptchaSession interactiveCaptchaSession;
  private int attempts = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS;
  private int nonValidPacketsSize;
  private boolean startedListening;
  private boolean checkedBySettings;
  private boolean checkedByBrand;
  private boolean adaptiveActive;
  private boolean adaptiveLoading;
  private boolean adaptiveChallengeStarted;
  private ScheduledFuture<?> adaptiveLoadingTask;

  public BotFilterSessionHandler(Player proxyPlayer, LimboFilter plugin) {
    this.proxyPlayer = proxyPlayer;
    this.version = this.proxyPlayer.getProtocolVersion();
    this.plugin = plugin;

    this.statistics = this.plugin.getStatistics();

    Settings.MAIN.FALLING_COORDS fallingCoords = Settings.IMP.MAIN.FALLING_COORDS;
    this.validX = fallingCoords.X;
    this.validY = fallingCoords.Y;
    this.validZ = fallingCoords.Z;
    this.validTeleportId = fallingCoords.TELEPORT_ID;

    this.posX = this.validX;
    this.posY = this.validY;
    this.posZ = this.validZ;

    boolean geyserConnection = proxyPlayer.getRemoteAddress().getPort() == 0;
    if (geyserConnection) {
      this.state = plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
          ? Settings.IMP.MAIN.GEYSER_CHECK_STATE : Settings.IMP.MAIN.GEYSER_CHECK_STATE_NON_TOGGLED;
    } else {
      this.state = plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
          ? Settings.IMP.MAIN.CHECK_STATE : Settings.IMP.MAIN.CHECK_STATE_NON_TOGGLED;
    }

    Settings.MAIN.ADAPTIVE_VERIFICATION adaptive = Settings.IMP.MAIN.ADAPTIVE_VERIFICATION;
    AdaptiveModeSelector.Policy adaptivePolicy = AdaptiveModeSelector.resolvePolicy(
        adaptive.MODE,
        adaptive.TEST_USERNAMES,
        adaptive.FULL_TEST_USERNAMES,
        this.proxyPlayer.getUsername());
    this.adaptiveDiagnosticsEnabled = adaptivePolicy.diagnosticsEnabled();
    this.adaptivePacketTraceBudget = AdaptiveModeSelector.shouldTracePackets(
        adaptive.PACKET_DEBUG, adaptive.PACKET_DEBUG_USERNAMES, this.proxyPlayer.getUsername())
        ? new AdaptivePacketTraceBudget(adaptive.PACKET_DEBUG_MAX_EVENTS) : null;
    if (!geyserConnection && adaptivePolicy.forceCaptcha()) {
      this.state = CheckState.CAPTCHA_POSITION;
    }

    if (geyserConnection || this.state == CheckState.ONLY_CAPTCHA) {
      this.adaptiveMode = AdaptiveMode.OFF;
      this.adaptiveSession = null;
    } else {
      this.adaptiveMode = adaptivePolicy.mode();
      this.adaptiveSession = this.createAdaptiveSession(adaptive);
    }
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.server = server;
    this.player = player;

    this.joinTime = System.currentTimeMillis();
    this.traceAdaptivePacket("state", "spawn",
        "mode=" + this.adaptiveMode + ", checkState=" + this.state);
    if (this.state == CheckState.ONLY_CAPTCHA) {
      this.changeStateToCaptcha();
    } else if (this.adaptiveSession != null) {
      if (AdaptiveLoadingGate.required(this.version)) {
        this.adaptiveLoading = true;
        this.sendAdaptiveLoadingAnchor();
      } else {
        this.beginAdaptiveChallenge();
      }
      this.player.writePacket(this.plugin.getPackets().getAdaptiveVerificationPlatformPackets());
      this.traceAdaptivePacket("outbound", "adaptive-platform", "chunks=preloaded");
    } else if (this.state == CheckState.ONLY_POSITION || this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.sendFallingCheckPackets();
      this.sendFallingCheckTitleAndChat();
    } else if (this.state == CheckState.CAPTCHA_POSITION) {
      this.sendFallingCheckPackets();

      if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
        this.sendFallingCheckTitleAndChat();
      }
    }

    this.player.flushPackets();

    this.filterMainTask = player.getScheduledExecutor().schedule(() ->
        this.disconnect(this.plugin.getPackets().getTimesUp(), true), this.getTimeout(), TimeUnit.MILLISECONDS);
  }

  private void sendFallingCheckPackets() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckPackets());
  }

  private void sendFallingCheckTitleAndChat() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckTitleAndChat());
  }

  private void sendAdaptiveLoadingAnchor() {
    Settings.MAIN.COORDS coords = Settings.IMP.MAIN.COORDS;
    MotionVector position = AdaptiveLoadingGate.POSITION;
    if (this.version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.player.writePacket(new AdaptivePosition(
          AdaptiveLoadingGate.TELEPORT_ID, position, MotionVector.ZERO,
          (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH));
    } else {
      this.player.writePacket(this.plugin.getPacketFactory().createPositionRotationPacket(
          position.x(), position.y(), position.z(),
          (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH,
          false, AdaptiveLoadingGate.TELEPORT_ID, true));
    }
    this.traceAdaptivePacket("outbound", "loading-anchor",
        "teleportId=" + AdaptiveLoadingGate.TELEPORT_ID + ", position=" + position);
  }

  private void beginAdaptiveChallenge() {
    if (this.adaptiveChallengeStarted) {
      return;
    }
    this.adaptiveChallengeStarted = true;
    this.adaptiveLoading = false;
    this.adaptiveActive = true;
    this.sendAdaptiveInstruction(this.adaptiveSession.start());
    if (this.state != CheckState.CAPTCHA_POSITION || Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      this.sendFallingCheckTitleAndChat();
    }
    this.traceAdaptivePacket("state", "challenge-start", "settled=true");
  }

  @Override
  public void onMove(double x, double y, double z) {
    this.traceAdaptivePacket("inbound", "move",
        "position=(" + x + ", " + y + ", " + z + "), onGround=" + this.onGround);
    if (this.adaptiveLoading) {
      return;
    }
    if (this.adaptiveActive) {
      VerificationResult result = this.adaptiveSession.move(new MotionVector(x, y, z), this.onGround);
      this.logAdaptiveResult(result, "move=(" + x + ", " + y + ", " + z + "), onGround=" + this.onGround);
      this.handleAdaptiveResult(result);
      return;
    }

    if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0
        && x == this.validX && y == this.validY && z == this.validZ && this.waitingTeleportId == this.validTeleportId) {
      this.ticks = 1;
      this.posY = -1;
      this.waitingTeleportId = -1;
    }

    this.posX = x;
    this.lastY = this.posY;
    this.posY = y;
    this.posZ = z;

    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      this.logPosition();
    }
    if (!this.startedListening && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.posX == this.validX && this.posZ == this.validZ) {
        this.startedListening = true;

        if (this.state == CheckState.CAPTCHA_POSITION && !Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
          this.sendCaptcha();
        }
      }
      if (this.nonValidPacketsSize > Settings.IMP.MAIN.NON_VALID_POSITION_XZ_ATTEMPTS) {
        this.fallingCheckFailed("A lot of non-valid XZ attempts");
        return;
      }

      this.lastY = this.validY;
      ++this.nonValidPacketsSize;
    }
    if (this.startedListening && this.state != CheckState.SUCCESSFUL && this.state != CheckState.ONLY_CAPTCHA && !this.onGround) {
      if (this.lastY - this.posY == 0) {
        ++this.ignoredTicks;
        return;
      }
      if (this.ignoredTicks > Settings.IMP.MAIN.NON_VALID_POSITION_Y_ATTEMPTS) {
        this.fallingCheckFailed("A lot of non-valid Y attempts");
        return;
      }
      if (this.ticks >= Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        if (this.state == CheckState.CAPTCHA_POSITION) {
          this.changeStateToCaptcha();
        } else {
          this.finishCheck();
        }
        return;
      }
      if (this.checkY()) {
        this.fallingCheckFailed("Non-valid X, Z or Velocity");
        return;
      }
      PreparedPacket experience = this.plugin.getPackets().getExperience(this.ticks);
      if (experience != null) {
        this.player.writePacketAndFlush(experience);
      }

      ++this.ticks;
    }
  }

  private void fallingCheckFailed(String reason) {
    this.traceAdaptivePacket("check", "falling-check-failed", "reason=" + reason);
    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      LimboFilter.getLogger().info(reason);
      this.logPosition();
    }

    if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.player.writePacketAndFlush(this.plugin.getPackets().getLastExperience());
      this.changeStateToCaptcha();
    } else {
      this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
    }
  }

  private void logPosition() {
    LimboFilter.getLogger().info(
        "lastY=" + this.lastY + "; y=" + this.posY + "; delta=" + (this.lastY - this.posY) + "; need=" + getLoadedChunkSpeed(this.ticks)
            + "; x=" + this.posX + "; z=" + this.posZ + "; validX=" + this.validX + "; validY=" + this.validY + "; validZ=" + this.validZ
            + "; ticks=" + this.ticks + "; ignoredTicks=" + this.ignoredTicks + "; state=" + this.state
            + "; diff=" + (this.lastY - this.posY - getLoadedChunkSpeed(this.ticks))
    );
  }

  private boolean checkY() {
    while (this.ticks < LOADED_CHUNK_SPEED_CACHE.length
        && Math.abs(this.lastY - this.posY - getLoadedChunkSpeed(this.ticks)) > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE) {
      ++this.ticks;
      ++this.ignoredTicks;
    }

    return this.ticks >= LOADED_CHUNK_SPEED_CACHE.length;
  }

  @Override
  public void onGround(boolean onGround) {
    this.onGround = onGround;
    this.traceAdaptivePacket("inbound", "on-ground", "value=" + onGround);
  }

  @Override
  public void onTeleport(int teleportId) {
    this.traceAdaptivePacket("inbound", "teleport-confirm", "teleportId=" + teleportId);
    if (this.adaptiveLoading) {
      if (teleportId == AdaptiveLoadingGate.TELEPORT_ID && this.adaptiveLoadingTask == null) {
        this.traceAdaptivePacket("state", "loading-anchor-confirmed",
            "settleMillis=" + AdaptiveLoadingGate.SETTLE_MILLIS);
        this.adaptiveLoadingTask = this.player.getScheduledExecutor().schedule(() -> {
          this.beginAdaptiveChallenge();
          this.player.flushPackets();
        }, AdaptiveLoadingGate.SETTLE_MILLIS, TimeUnit.MILLISECONDS);
      } else if (teleportId != AdaptiveLoadingGate.TELEPORT_ID) {
        this.traceAdaptivePacket("check", "loading-anchor-confirm", "ignoredUnexpectedId=" + teleportId);
      }
      return;
    }
    if (this.adaptiveActive) {
      VerificationResult result = this.adaptiveSession.confirmTeleport(teleportId);
      this.logAdaptiveResult(result, "teleport-confirm=" + teleportId);
      this.handleAdaptiveResult(result);
      return;
    }

    if (teleportId == this.waitingTeleportId) {
      this.ticks = 1;
      this.posY = -1;
      this.lastY = -1;
      this.waitingTeleportId = -1;
    }
  }

  @Override
  public void onChat(String message) {
    this.traceAdaptivePacket("inbound", "chat",
        "length=" + message.length() + ", state=" + this.state);
    if (this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA) {
      if (this.interactiveCaptchaSession != null) {
        this.traceAdaptivePacket("check", "interactive-captcha-chat", "ignored=true");
        return;
      }
      if (this.equalsCaptchaAnswer(message) || (message.startsWith("/") && this.equalsCaptchaAnswer(message.substring(1)))) {
        this.player.writePacketAndFlush(this.plugin.getPackets().getResetSlot());
        this.finishCheck();
      } else {
        this.handleCaptchaFailure();
      }
    }
  }

  private boolean equalsCaptchaAnswer(String message) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.IGNORE_CASE) {
      return message.equalsIgnoreCase(this.captchaAnswer);
    } else {
      return message.equals(this.captchaAnswer);
    }
  }

  @Override
  public void onGeneric(Object packet) {
    this.traceAdaptivePacket("inbound", "generic", "type=" + packet.getClass().getSimpleName());
    if (packet instanceof PluginMessagePacket) {
      PluginMessagePacket pluginMessage = (PluginMessagePacket) packet;
      if (PluginMessageUtil.isMcBrand(pluginMessage) && !this.checkedByBrand) {
        String brand = PluginMessageUtil.readBrandMessage(pluginMessage.content());
        LimboFilter.getLogger().info("{} has client brand {}", this.proxyPlayer, brand);
        if (!Settings.IMP.MAIN.BLOCKED_CLIENT_BRANDS.contains(brand)) {
          this.checkedByBrand = true;
        }
      }
    } else if (packet instanceof ClientSettingsPacket) {
      if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
        this.checkedBySettings = true;
      }
    } else if (packet instanceof Interact) {
      Interact interact = (Interact) packet;
      if (this.interactiveCaptchaSession != null
          && (this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA)) {
        InteractiveCaptchaSession.SelectionResult result =
            this.interactiveCaptchaSession.select(interact.getEntityId());
        this.traceAdaptivePacket("check", "interactive-captcha-click",
            "entityId=" + interact.getEntityId() + ", result=" + result);
        if (result == InteractiveCaptchaSession.SelectionResult.PENDING) {
          this.rotateFrame(interact.getEntityId());
        } else if (result == InteractiveCaptchaSession.SelectionResult.PASSED) {
          this.player.writePacketAndFlush(this.plugin.getPackets().getResetSlot());
          this.finishCheck();
        } else if (result == InteractiveCaptchaSession.SelectionResult.FAILED) {
          this.handleCaptchaFailure();
        }
        return;
      }
      if (interact.getType() == 0 || interact.getType() == 1) {
        this.rotateFrame(interact.getEntityId());
      }
    }
  }

  private void rotateFrame(int entityId) {
    int rotation = this.frameRotation.compute(entityId, (key, value) -> (value != null ? value : 0) + 1);
    EntityMetadata metadata = ItemFrame.createRotationMetadata(this.version, rotation);
    this.player.writePacketAndFlush(new SetEntityMetadata(entityId, metadata));
  }

  @Override
  public void onDisconnect() {
    this.traceAdaptivePacket("state", "disconnect", "checkState=" + this.state);
    if (this.filterMainTask != null) {
      this.filterMainTask.cancel(true);
    }
    if (this.adaptiveLoadingTask != null) {
      this.adaptiveLoadingTask.cancel(true);
    }

    TcpListener tcpListener = this.plugin.getTcpListener();
    if (tcpListener != null) {
      tcpListener.removeAddress(this.proxyPlayer.getRemoteAddress().getAddress());
    }
  }

  private void finishCheck() {
    if (System.currentTimeMillis() - this.joinTime < FALLING_CHECK_TOTAL_TIME && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.state == CheckState.CAPTCHA_POSITION && this.ticks < Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        this.state = CheckState.ONLY_POSITION;
      } else {
        if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
          this.changeStateToCaptcha();
        } else {
          this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
        }
      }
      return;
    }

    this.completeClientChecks();
  }

  private void completeClientChecks() {
    this.traceAdaptivePacket("check", "client-checks",
        "settings=" + this.checkedBySettings + ", brand=" + this.checkedByBrand);

    if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
      this.traceAdaptivePacket("check", "client-settings", "result=FAIL");
      this.disconnect(this.plugin.getPackets().getKickClientCheckSettings(), true);
      return;
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_BRAND && !this.checkedByBrand) {
      this.traceAdaptivePacket("check", "client-brand", "result=FAIL");
      this.disconnect(this.plugin.getPackets().getKickClientCheckBrand(), true);
      return;
    }

    if (this.checkPing()) {
      return;
    }

    this.state = CheckState.SUCCESSFUL;
    this.traceAdaptivePacket("state", "successful", "cached=true");
    this.plugin.cacheFilterUser(this.proxyPlayer);

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.NEED_TO_RECONNECT)) {
      this.disconnect(this.plugin.getPackets().getSuccessfulBotFilterDisconnect(), false);
    } else {
      this.player.writePacketAndFlush(this.plugin.getPackets().getSuccessfulBotFilterChat());
      this.player.disconnect();
    }
  }

  private boolean checkPing() {
    int l7Ping = this.player.getPing();
    int l4Ping = this.statistics.getPing(this.proxyPlayer.getRemoteAddress().getAddress());

    if (Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_ENABLED && (l7Ping - l4Ping) > Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_DIFFERENCE) {
      this.disconnect(this.plugin.getPackets().getKickProxyCheck(), true);

      if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_FAIL) {
        LimboFilter.getLogger().info("{} failed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
      }

      return true;
    }

    if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_SUCCESS) {
      LimboFilter.getLogger().info("{} passed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
    }

    return false;
  }

  private void changeStateToCaptcha() {
    this.adaptiveActive = false;
    this.adaptiveLoading = false;
    this.traceAdaptivePacket("state", "captcha", "transition=true");
    if (this.state != CheckState.ONLY_CAPTCHA && this.version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.player.writePacket(this.plugin.getPackets().getFallingCheckChunkUnload());
    }

    this.state = CheckState.ONLY_CAPTCHA;
    this.server.respawnPlayer(this.proxyPlayer);
    if (Settings.IMP.MAIN.DISABLE_FALLING_ON_CAPTCHA) {
      this.player.writePacketAndFlush(this.plugin.getPackets().getNoAbilities());
    }

    this.waitingTeleportId = this.validTeleportId;
    if (this.captchaAnswer == null) {
      this.sendCaptcha();
    }
  }

  private void sendCaptcha() {
    CaptchaHolder captchaHolder = this.plugin.getNextCaptcha();

    if (captchaHolder == null) {
      this.player.closeWith(this.plugin.getPackets().getCaptchaNotReadyYet());
      return;
    }

    this.captchaAnswer = captchaHolder.getAnswer();
    this.interactiveCaptchaSession = InteractiveCaptchaSession.isInteractiveAnswer(this.captchaAnswer)
        ? InteractiveCaptchaSession.fromAnswer(this.captchaAnswer) : null;
    this.traceAdaptivePacket("outbound", "captcha",
        "attempts=" + this.attempts + ", answerLength=" + this.captchaAnswer.length()
            + ", interactive=" + (this.interactiveCaptchaSession != null));

    PreparedPacket framedCaptchaPacket = this.interactiveCaptchaSession == null
        ? this.plugin.getPackets().getFramedCaptchaPackets()
        : this.plugin.getPackets().getInteractiveCaptchaPackets();
    if (framedCaptchaPacket != null) {
      this.player.writePacket(framedCaptchaPacket);
    }

    this.player.writePacket(this.interactiveCaptchaSession == null
        ? this.plugin.getPackets().getCaptchaAttemptsPacket(this.attempts)
        : this.plugin.getPackets().getInteractiveCaptchaAttemptsPacket(this.attempts));
    for (Object packet : captchaHolder.getMapPacket(this.version)) {
      this.player.writePacket(packet);
    }

    this.player.flushPackets();
  }

  private void handleCaptchaFailure() {
    if (--this.attempts != 0) {
      this.sendCaptcha();
    } else {
      this.disconnect(this.plugin.getPackets().getCaptchaFailed(), true);
    }
  }

  private void disconnect(PreparedPacket reason, boolean blocked) {
    this.player.closeWith(reason);
    if (blocked) {
      this.statistics.addBlockedConnection();
    }
  }

  private int getTimeout() {
    if (this.proxyPlayer.getRemoteAddress().getPort() == 0) {
      return Settings.IMP.MAIN.GEYSER_TIME_OUT;
    } else {
      return Settings.IMP.MAIN.TIME_OUT;
    }
  }

  private AdaptiveVerificationSession createAdaptiveSession(Settings.MAIN.ADAPTIVE_VERIFICATION settings) {
    if (this.adaptiveMode == AdaptiveMode.OFF) {
      return null;
    }

    boolean modernImpulse = settings.IMPULSE_ENABLED && this.version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2);
    PhysicsProfile profile = modernImpulse
        ? PhysicsProfile.javaModern(settings.POSITION_TOLERANCE, settings.COLLISION_TOLERANCE, settings.MAX_PACKET_GAP_TICKS)
        : PhysicsProfile.javaLegacy(settings.POSITION_TOLERANCE, settings.COLLISION_TOLERANCE, settings.MAX_PACKET_GAP_TICKS);
    ChallengeProgram program = ChallengeProgramFactory.create(profile, new SecureRandom(), settings.PHASES_PER_SESSION);
    return new AdaptiveVerificationSession(
        program, profile, settings.MAX_SAMPLES_PER_PHASE, settings.MAX_SESSION_MILLIS,
        System::currentTimeMillis, AdaptiveLoadingGate.required(this.version));
  }

  private void sendAdaptiveInstruction(ChallengeInstruction instruction) {
    Settings.MAIN.COORDS coords = Settings.IMP.MAIN.COORDS;
    this.onGround = false;
    if (this.version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.player.writePacket(new AdaptivePosition(
          instruction.teleportId(), instruction.start(), instruction.initialVelocity(),
          (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH));
    } else {
      MotionVector position = instruction.start();
      this.player.writePacket(this.plugin.getPacketFactory().createPositionRotationPacket(
          position.x(), position.y(), position.z(),
          (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH,
          false, instruction.teleportId(), true));
      if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
        this.handleAdaptiveResult(this.adaptiveSession.confirmTeleport(instruction.teleportId()));
      }
    }
    this.traceAdaptivePacket("outbound", "challenge-instruction", instruction.toString());
  }

  private void handleAdaptiveResult(VerificationResult result) {
    if (result == VerificationResult.PENDING) {
      return;
    }
    if (result == VerificationResult.PHASE_PASSED) {
      this.traceAdaptivePacket("state", "phase-transition",
          "next=" + this.adaptiveSession.currentInstruction());
      this.sendAdaptiveInstruction(this.adaptiveSession.currentInstruction());
      this.player.flushPackets();
      return;
    }

    this.adaptiveActive = false;
    this.traceAdaptivePacket("state", "adaptive-terminal", "result=" + result);
    if (this.adaptiveMode == AdaptiveMode.SHADOW) {
      this.fallbackToLegacyFallingCheck();
    } else if (result == VerificationResult.PASS) {
      if (this.state == CheckState.CAPTCHA_POSITION) {
        this.changeStateToCaptcha();
      } else {
        this.completeClientChecks();
      }
    } else {
      this.fallingCheckFailed("Adaptive verification failed: " + result);
    }
  }

  private void logAdaptiveResult(VerificationResult result, String event) {
    if (this.adaptivePacketTraceBudget != null) {
      AdaptiveVerificationSession.Diagnostics diagnostics = this.adaptiveSession.diagnostics();
      this.traceAdaptivePacket("check", "adaptive-result",
          "result=" + result + ", event=" + event + ", phase=" + diagnostics.phaseNumber()
              + "/" + diagnostics.totalPhases() + ", samples=" + diagnostics.samples()
              + ", teleportConfirmed=" + diagnostics.teleportConfirmed()
              + ", awaitingInitialMotion=" + diagnostics.awaitingInitialMotion()
              + ", previous=" + diagnostics.previous() + ", match=" + diagnostics.lastMatch());
    }
    if ((!Settings.IMP.MAIN.FALLING_CHECK_DEBUG && !this.adaptiveDiagnosticsEnabled)
        || result == VerificationResult.PENDING) {
      return;
    }

    AdaptiveVerificationSession.Diagnostics diagnostics = this.adaptiveSession.diagnostics();
    LimboFilter.getLogger().info(
        "{} adaptive verification: protocol={}, mode={}, result={}, event={}, phase={}/{}, samples={}, "
            + "teleportConfirmed={}, awaitingInitialMotion={}, initialEchoes={}, previous={}, instruction={}",
        this.proxyPlayer, this.version, this.adaptiveMode, result, event,
        diagnostics.phaseNumber(), diagnostics.totalPhases(), diagnostics.samples(),
        diagnostics.teleportConfirmed(), diagnostics.awaitingInitialMotion(), diagnostics.initialPositionEchoes(),
        diagnostics.previous(), diagnostics.instruction());
  }

  private void traceAdaptivePacket(String direction, String packet, String details) {
    if (this.adaptivePacketTraceBudget == null) {
      return;
    }

    AdaptivePacketTraceBudget.Decision decision = this.adaptivePacketTraceBudget.next();
    if (decision == AdaptivePacketTraceBudget.Decision.SKIP) {
      return;
    }
    if (decision == AdaptivePacketTraceBudget.Decision.TRUNCATED) {
      LimboFilter.getLogger().info(
          "{} adaptive packet-debug: trace truncated after configured event budget",
          this.proxyPlayer);
      return;
    }

    LimboFilter.getLogger().info(
        "{} adaptive packet-debug: direction={}, packet={}, details={}, protocol={}, state={}, loading={}, active={}",
        this.proxyPlayer, direction, packet, details, this.version, this.state,
        this.adaptiveLoading, this.adaptiveActive);
  }

  private void fallbackToLegacyFallingCheck() {
    this.startedListening = false;
    this.onGround = false;
    this.ticks = 1;
    this.ignoredTicks = 0;
    this.nonValidPacketsSize = 0;
    this.posX = this.validX;
    this.posY = this.validY;
    this.lastY = this.validY;
    this.posZ = this.validZ;
    this.waitingTeleportId = 0;
    this.sendFallingCheckPackets();
    this.player.flushPackets();
  }

  static {
    for (int i = 0; i < Settings.IMP.MAIN.FALLING_CHECK_TICKS; ++i) {
      LOADED_CHUNK_SPEED_CACHE[i] = -((Math.pow(0.98, i) - 1) * 3.92);
    }
  }

  public static double getLoadedChunkSpeed(int ticks) {
    if (ticks == -1) {
      return 0;
    }

    return LOADED_CHUNK_SPEED_CACHE[ticks];
  }

  public static void setFallingCheckTotalTime(long time) {
    FALLING_CHECK_TOTAL_TIME = time;
  }

  public enum CheckState {

    ONLY_POSITION,
    ONLY_CAPTCHA,
    CAPTCHA_POSITION,
    CAPTCHA_ON_POSITION_FAILED,
    SUCCESSFUL
  }
}
