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

package net.elytrium.limbofilter.captcha.advanced;

public final class MemoryGridChallenge {

  private final InteractiveCaptchaSession sequence;
  private final int previewTeleportId;
  private final int traversalTeleportId;

  private Phase phase = Phase.CREATED;
  private boolean teleportConfirmed;

  private MemoryGridChallenge(InteractiveCaptchaSession sequence, int previewTeleportId, int traversalTeleportId) {
    this.sequence = sequence;
    this.previewTeleportId = previewTeleportId;
    this.traversalTeleportId = traversalTeleportId;
  }

  public static MemoryGridChallenge fromAnswer(String answer, int previewTeleportId, int traversalTeleportId) {
    if (!InteractiveCaptchaSession.isMemoryGridAnswer(answer)) {
      throw new IllegalArgumentException("memory grid answer must contain a strict walking sequence");
    }
    if (previewTeleportId < 1 || traversalTeleportId < 1 || previewTeleportId == traversalTeleportId) {
      throw new IllegalArgumentException("memory grid teleport ids must be positive and different");
    }
    return new MemoryGridChallenge(
        InteractiveCaptchaSession.fromAnswer(answer), previewTeleportId, traversalTeleportId);
  }

  public Traversal beginPreview() {
    if (this.phase != Phase.CREATED) {
      throw new IllegalStateException("memory grid preview was already started");
    }
    this.phase = Phase.PREVIEW;
    return this.traversal(this.previewTeleportId);
  }

  public Traversal activateTraversal() {
    if (this.phase != Phase.PREVIEW) {
      throw new IllegalStateException("memory grid traversal requires an active preview");
    }
    this.phase = Phase.ACTIVE;
    this.teleportConfirmed = false;
    return this.traversal(this.traversalTeleportId);
  }

  private Traversal traversal(int teleportId) {
    return new Traversal(
        teleportId,
        MemoryGridLayout.startX(),
        MemoryGridLayout.startY(),
        MemoryGridLayout.startZ()
    );
  }

  public Result confirmTeleport(int confirmedTeleportId) {
    if (this.phase == Phase.PREVIEW || this.phase == Phase.TERMINAL) {
      return Result.IGNORED;
    }
    if (this.phase != Phase.ACTIVE) {
      return Result.IGNORED;
    }
    if (confirmedTeleportId == this.previewTeleportId) {
      return Result.IGNORED;
    }
    if (this.teleportConfirmed || confirmedTeleportId != this.traversalTeleportId) {
      this.phase = Phase.TERMINAL;
      return Result.FAILED_PROTOCOL;
    }
    this.teleportConfirmed = true;
    return Result.PENDING;
  }

  public Result move(double x, double y, double z, boolean onGround) {
    if (this.phase != Phase.ACTIVE || !this.teleportConfirmed) {
      return Result.IGNORED;
    }

    int tile = MemoryGridLayout.tileAt(x, y, z, onGround);
    if (tile < 0) {
      return Result.IGNORED;
    }

    InteractiveCaptchaSession.SelectionResult selection = this.sequence.select(tile);
    return switch (selection) {
      case IGNORED -> Result.IGNORED;
      case PENDING -> Result.PENDING;
      case PASSED -> {
        this.phase = Phase.TERMINAL;
        yield Result.PASSED;
      }
      case FAILED -> {
        this.phase = Phase.TERMINAL;
        yield Result.FAILED;
      }
    };
  }

  public record Traversal(int teleportId, double x, double y, double z) {
  }

  private enum Phase {
    CREATED,
    PREVIEW,
    ACTIVE,
    TERMINAL
  }

  public enum Result {
    IGNORED,
    PENDING,
    PASSED,
    FAILED,
    FAILED_PROTOCOL
  }
}
