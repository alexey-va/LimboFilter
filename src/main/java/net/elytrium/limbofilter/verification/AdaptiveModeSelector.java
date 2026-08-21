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

import java.util.List;
import java.util.Objects;

public final class AdaptiveModeSelector {

  private AdaptiveModeSelector() {
  }

  public static AdaptiveMode resolve(AdaptiveMode globalMode, List<String> testUsernames, String username) {
    return resolvePolicy(globalMode, testUsernames, List.of(), username).mode();
  }

  public static Policy resolvePolicy(AdaptiveMode globalMode, List<String> testUsernames,
                                     List<String> fullTestUsernames, String username) {
    Objects.requireNonNull(globalMode, "globalMode");
    Objects.requireNonNull(testUsernames, "testUsernames");
    Objects.requireNonNull(fullTestUsernames, "fullTestUsernames");
    Objects.requireNonNull(username, "username");

    boolean fullTestUser = containsUsername(fullTestUsernames, username);
    boolean testUser = fullTestUser || containsUsername(testUsernames, username);
    return new Policy(testUser ? AdaptiveMode.ENFORCE : globalMode, fullTestUser, testUser);
  }

  public static boolean shouldForceFilter(List<String> fullTestUsernames, String username,
                                          boolean geyserConnection) {
    Objects.requireNonNull(fullTestUsernames, "fullTestUsernames");
    Objects.requireNonNull(username, "username");
    return !geyserConnection && containsUsername(fullTestUsernames, username);
  }

  public static boolean shouldTracePackets(boolean enabled, List<String> debugUsernames,
                                           String username) {
    Objects.requireNonNull(debugUsernames, "debugUsernames");
    Objects.requireNonNull(username, "username");
    return enabled && containsUsername(debugUsernames, username);
  }

  public static boolean shouldUseMemoryGrid(List<String> testUsernames, String username) {
    Objects.requireNonNull(testUsernames, "testUsernames");
    Objects.requireNonNull(username, "username");
    return containsUsername(testUsernames, username);
  }

  private static boolean containsUsername(List<String> usernames, String username) {
    return usernames.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .anyMatch(username::equalsIgnoreCase);
  }

  public record Policy(AdaptiveMode mode, boolean forceCaptcha, boolean diagnosticsEnabled) {
  }
}
