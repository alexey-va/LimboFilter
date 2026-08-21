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

import java.util.Objects;
import java.util.function.LongSupplier;

public final class AdaptiveVerificationSession {

  private final ChallengeProgram program;
  private final PhysicsProfile profile;
  private final int maxSamplesPerPhase;
  private final long maxSessionMillis;
  private final LongSupplier clock;
  private final boolean loadingAnchorTailAllowed;

  private int phaseIndex;
  private int samples;
  private long startedAt;
  private boolean started;
  private boolean motionStarted;
  private boolean teleportConfirmed;
  private boolean awaitingInitialMotion;
  private int initialPositionEchoes;
  private int preTeleportMovementTail;
  private MotionSample previous;
  private TrajectoryMatch lastMatch;
  private VerificationResult result = VerificationResult.PENDING;

  public AdaptiveVerificationSession(ChallengeProgram program, PhysicsProfile profile,
                                     int maxSamplesPerPhase, long maxSessionMillis,
                                     LongSupplier clock) {
    this(program, profile, maxSamplesPerPhase, maxSessionMillis, clock, false);
  }

  public AdaptiveVerificationSession(ChallengeProgram program, PhysicsProfile profile,
                                     int maxSamplesPerPhase, long maxSessionMillis,
                                     LongSupplier clock, boolean loadingAnchorTailAllowed) {
    this.program = Objects.requireNonNull(program, "program");
    this.profile = Objects.requireNonNull(profile, "profile");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (maxSamplesPerPhase < 1 || maxSamplesPerPhase > 1_000) {
      throw new IllegalArgumentException("maxSamplesPerPhase must be in range 1..1000");
    }
    if (maxSessionMillis < 1L) {
      throw new IllegalArgumentException("maxSessionMillis must be positive");
    }
    this.maxSamplesPerPhase = maxSamplesPerPhase;
    this.maxSessionMillis = maxSessionMillis;
    this.loadingAnchorTailAllowed = loadingAnchorTailAllowed;
  }

  public ChallengeInstruction start() {
    if (this.started) {
      throw new IllegalStateException("verification session was already started");
    }
    this.started = true;
    return this.currentInstruction();
  }

  public ChallengeInstruction currentInstruction() {
    return this.program.instructions().get(this.phaseIndex);
  }

  public VerificationResult confirmTeleport(int teleportId) {
    if (this.result.terminal()) {
      return this.result;
    }
    if (!this.started) {
      return this.fail(VerificationResult.FAIL_PROTOCOL);
    }
    if (this.motionStarted && this.expired()) {
      return this.fail(VerificationResult.TIMEOUT);
    }
    if (this.teleportConfirmed) {
      return this.fail(VerificationResult.FAIL_REPLAY);
    }

    ChallengeInstruction instruction = this.currentInstruction();
    if (teleportId != instruction.teleportId()) {
      return this.fail(VerificationResult.FAIL_PROTOCOL);
    }

    this.teleportConfirmed = true;
    this.awaitingInitialMotion = true;
    this.initialPositionEchoes = 0;
    this.preTeleportMovementTail = 0;
    this.previous = new MotionSample(0, instruction.start(), instruction.initialVelocity(), false);
    this.lastMatch = null;
    this.result = VerificationResult.PENDING;
    return this.result;
  }

  public VerificationResult move(MotionVector position, boolean onGround) {
    if (this.result.terminal()) {
      return this.result;
    }
    if (!this.started) {
      return this.fail(VerificationResult.FAIL_PROTOCOL);
    }
    if (!this.teleportConfirmed) {
      boolean movementTailAllowed = this.phaseIndex > 0 || this.loadingAnchorTailAllowed;
      if (movementTailAllowed && ++this.preTeleportMovementTail <= this.profile.maxPacketGapTicks()) {
        return this.motionStarted && this.expired()
            ? this.fail(VerificationResult.TIMEOUT) : VerificationResult.PENDING;
      }
      return this.fail(VerificationResult.FAIL_PROTOCOL);
    }
    if (++this.samples > this.maxSamplesPerPhase) {
      return this.fail(VerificationResult.TIMEOUT);
    }
    if (!position.isFinite()) {
      return this.fail(VerificationResult.FAIL_TRAJECTORY);
    }

    ChallengeInstruction instruction = this.currentInstruction();
    if (this.awaitingInitialMotion) {
      if (!onGround && within(position, instruction.start(), this.profile.positionTolerance())) {
        if (++this.initialPositionEchoes > this.profile.maxPacketGapTicks()) {
          return this.fail(VerificationResult.FAIL_TRAJECTORY);
        }
        this.result = VerificationResult.PENDING;
        return this.result;
      }
      this.awaitingInitialMotion = false;
    }
    if (!this.motionStarted) {
      this.motionStarted = true;
      this.startedAt = this.clock.getAsLong();
    } else if (this.expired()) {
      return this.fail(VerificationResult.TIMEOUT);
    }
    TrajectoryMatch match = TrajectoryEnvelope.match(
        this.previous, position, onGround, this.profile, instruction.platformTopY());
    this.lastMatch = match;
    if (!match.matched()) {
      return this.fail(onGround ? VerificationResult.FAIL_COLLISION : VerificationResult.FAIL_TRAJECTORY);
    }

    this.previous = match.predicted();
    if (!match.collision()) {
      this.result = VerificationResult.PENDING;
      return this.result;
    }

    if (!insidePlatform(position, instruction)) {
      return this.fail(VerificationResult.FAIL_COLLISION);
    }

    if (this.phaseIndex == this.program.instructions().size() - 1) {
      this.result = VerificationResult.PASS;
      return this.result;
    }

    ++this.phaseIndex;
    this.samples = 0;
    this.teleportConfirmed = false;
    this.preTeleportMovementTail = 0;
    this.previous = null;
    this.result = VerificationResult.PHASE_PASSED;
    return this.result;
  }

  public VerificationResult result() {
    return this.result;
  }

  public Diagnostics diagnostics() {
    return new Diagnostics(
        this.phaseIndex + 1,
        this.program.instructions().size(),
        this.samples,
        this.teleportConfirmed,
        this.awaitingInitialMotion,
        this.initialPositionEchoes,
        this.previous,
        this.lastMatch,
        this.currentInstruction(),
        this.result
    );
  }

  private boolean expired() {
    return this.clock.getAsLong() - this.startedAt > this.maxSessionMillis;
  }

  private VerificationResult fail(VerificationResult failure) {
    this.result = failure;
    return this.result;
  }

  private static boolean insidePlatform(MotionVector position, ChallengeInstruction instruction) {
    return Math.abs(position.x() - instruction.start().x()) <= instruction.platformHalfWidth()
        && Math.abs(position.z() - instruction.start().z()) <= instruction.platformHalfWidth();
  }

  private static boolean within(MotionVector actual, MotionVector expected, double tolerance) {
    return Math.abs(actual.x() - expected.x()) <= tolerance
        && Math.abs(actual.y() - expected.y()) <= tolerance
        && Math.abs(actual.z() - expected.z()) <= tolerance;
  }

  public record Diagnostics(int phaseNumber, int totalPhases, int samples,
                            boolean teleportConfirmed, boolean awaitingInitialMotion,
                            int initialPositionEchoes, MotionSample previous,
                            TrajectoryMatch lastMatch,
                            ChallengeInstruction instruction, VerificationResult result) {
  }
}
