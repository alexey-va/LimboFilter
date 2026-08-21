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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveModeSelectorTest {

  @Test
  void forcesEnforceForAnAllowlistedUsernameIgnoringCaseAndWhitespace() {
    assertEquals(AdaptiveMode.ENFORCE,
        AdaptiveModeSelector.resolve(AdaptiveMode.OFF, List.of("  TestPlayer  "), "testplayer"));
    assertEquals(AdaptiveMode.ENFORCE,
        AdaptiveModeSelector.resolve(AdaptiveMode.SHADOW, List.of("TestPlayer"), "TESTPLAYER"));
  }

  @Test
  void preservesTheGlobalModeForEveryOtherUsername() {
    assertEquals(AdaptiveMode.OFF,
        AdaptiveModeSelector.resolve(AdaptiveMode.OFF, List.of("TestPlayer"), "AnotherPlayer"));
    assertEquals(AdaptiveMode.SHADOW,
        AdaptiveModeSelector.resolve(AdaptiveMode.SHADOW, List.of(), "TestPlayer"));
    assertEquals(AdaptiveMode.ENFORCE,
        AdaptiveModeSelector.resolve(AdaptiveMode.ENFORCE, List.of("SomeoneElse"), "TestPlayer"));
  }

  @Test
  void forcesTheFullPipelineForAFullTestUsername() {
    AdaptiveModeSelector.Policy policy = AdaptiveModeSelector.resolvePolicy(
        AdaptiveMode.OFF, List.of(), List.of("  GrocerMC  "), "grocermc");

    assertEquals(AdaptiveMode.ENFORCE, policy.mode());
    assertTrue(policy.forceCaptcha());
  }

  @Test
  void keepsCaptchaOptionalForAnAdaptiveOnlyTestUsername() {
    AdaptiveModeSelector.Policy policy = AdaptiveModeSelector.resolvePolicy(
        AdaptiveMode.OFF, List.of("CodexQA_728"), List.of("GrocerMC"), "codexqa_728");

    assertEquals(AdaptiveMode.ENFORCE, policy.mode());
    assertFalse(policy.forceCaptcha());
  }

  @Test
  void forcesOnlyJavaFullTestUsersThroughTheFilterEntryGate() {
    assertTrue(AdaptiveModeSelector.shouldForceFilter(
        List.of("  GrocerMC  "), "grocermc", false));
    assertFalse(AdaptiveModeSelector.shouldForceFilter(
        List.of("GrocerMC"), "GrocerMC", true));
    assertFalse(AdaptiveModeSelector.shouldForceFilter(
        List.of("GrocerMC"), "AnotherPlayer", false));
  }
}
