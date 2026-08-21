/*
 * Copyright (C) 2021 - 2026 Elytrium
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

public enum VerificationResult {
  PENDING(false),
  PHASE_PASSED(false),
  PASS(true),
  FAIL_PROTOCOL(true),
  FAIL_TRAJECTORY(true),
  FAIL_COLLISION(true),
  FAIL_REPLAY(true),
  TIMEOUT(true),
  UNSUPPORTED(true);

  private final boolean terminal;

  VerificationResult(boolean terminal) {
    this.terminal = terminal;
  }

  public boolean terminal() {
    return this.terminal;
  }
}
