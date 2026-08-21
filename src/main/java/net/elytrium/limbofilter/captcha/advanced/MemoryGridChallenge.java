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
  private final int teleportId;

  private boolean traversalStarted;
  private boolean teleportConfirmed;
  private boolean terminal;

  private MemoryGridChallenge(InteractiveCaptchaSession sequence, int teleportId) {
    this.sequence = sequence;
    this.teleportId = teleportId;
  }

  public static MemoryGridChallenge fromAnswer(String answer, int teleportId) {
    if (!InteractiveCaptchaSession.isMemoryGridAnswer(answer)) {
      throw new IllegalArgumentException("memory grid answer must contain a strict walking sequence");
    }
    if (teleportId < 1) {
      throw new IllegalArgumentException("memory grid teleport id must be positive");
    }
    return new MemoryGridChallenge(InteractiveCaptchaSession.fromAnswer(answer), teleportId);
  }

  public Traversal beginTraversal() {
    if (this.traversalStarted || this.terminal) {
      throw new IllegalStateException("memory grid traversal was already started");
    }
    this.traversalStarted = true;
    return new Traversal(
        this.teleportId,
        MemoryGridLayout.startX(),
        MemoryGridLayout.startY(),
        MemoryGridLayout.startZ()
    );
  }

  public Result confirmTeleport(int confirmedTeleportId) {
    if (this.terminal) {
      return Result.IGNORED;
    }
    if (!this.traversalStarted || this.teleportConfirmed || confirmedTeleportId != this.teleportId) {
      this.terminal = true;
      return Result.FAILED_PROTOCOL;
    }
    this.teleportConfirmed = true;
    return Result.PENDING;
  }

  public Result move(double x, double y, double z, boolean onGround) {
    if (this.terminal || !this.teleportConfirmed) {
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
        this.terminal = true;
        yield Result.PASSED;
      }
      case FAILED -> {
        this.terminal = true;
        yield Result.FAILED;
      }
    };
  }

  public record Traversal(int teleportId, double x, double y, double z) {
  }

  public enum Result {
    IGNORED,
    PENDING,
    PASSED,
    FAILED,
    FAILED_PROTOCOL
  }
}
