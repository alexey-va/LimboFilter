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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InteractiveCaptchaSessionTest {

  @Test
  void acceptsTheRequestedFramesOnlyInOrder() {
    InteractiveCaptchaSession session = InteractiveCaptchaSession.fromAnswer("CLICK:15,13,10");

    assertEquals(InteractiveCaptchaSession.SelectionResult.PENDING, session.select(15));
    assertEquals(InteractiveCaptchaSession.SelectionResult.IGNORED, session.select(15));
    assertEquals(InteractiveCaptchaSession.SelectionResult.PENDING, session.select(13));
    assertEquals(InteractiveCaptchaSession.SelectionResult.PASSED, session.select(10));
  }

  @Test
  void failsOnAnotherSelectableFrameAndStaysTerminal() {
    InteractiveCaptchaSession session = InteractiveCaptchaSession.fromAnswer("CLICK:15,13,10");

    assertEquals(InteractiveCaptchaSession.SelectionResult.FAILED, session.select(12));
    assertEquals(InteractiveCaptchaSession.SelectionResult.IGNORED, session.select(15));
  }

  @Test
  void ignoresInstructionFramesWithoutAdvancingTheSequence() {
    InteractiveCaptchaSession session = InteractiveCaptchaSession.fromAnswer("CLICK:15,13,10");

    assertEquals(InteractiveCaptchaSession.SelectionResult.IGNORED, session.select(18));
    assertEquals(InteractiveCaptchaSession.SelectionResult.PENDING, session.select(15));
  }

  @Test
  void recognizesOnlyStrictThreeStepInteractiveAnswers() {
    assertTrue(InteractiveCaptchaSession.isInteractiveAnswer("CLICK:15,13,10"));
    assertFalse(InteractiveCaptchaSession.isInteractiveAnswer("ABCD"));
    assertFalse(InteractiveCaptchaSession.isInteractiveAnswer("CLICK:15,13"));
    assertThrows(IllegalArgumentException.class,
        () -> InteractiveCaptchaSession.fromAnswer("CLICK:15,15,10"));
  }

  @Test
  void validatesWalkingTilesInTheRememberedOrder() {
    InteractiveCaptchaSession session = InteractiveCaptchaSession.fromAnswer("WALK:1,4,7");

    assertEquals(InteractiveCaptchaSession.SelectionResult.PENDING, session.select(1));
    assertEquals(InteractiveCaptchaSession.SelectionResult.IGNORED, session.select(1));
    assertEquals(InteractiveCaptchaSession.SelectionResult.PENDING, session.select(4));
    assertEquals(InteractiveCaptchaSession.SelectionResult.PASSED, session.select(7));
  }

  @Test
  void failsWalkingAttemptOnAnotherGridTile() {
    InteractiveCaptchaSession session = InteractiveCaptchaSession.fromAnswer("WALK:1,4,7");

    assertEquals(InteractiveCaptchaSession.SelectionResult.FAILED, session.select(2));
    assertEquals(InteractiveCaptchaSession.SelectionResult.IGNORED, session.select(1));
  }

  @Test
  void recognizesMemoryGridAnswersAsInteractive() {
    assertTrue(InteractiveCaptchaSession.isInteractiveAnswer("WALK:1,4,7"));
    assertFalse(InteractiveCaptchaSession.isInteractiveAnswer("WALK:1,1,4"));
  }
}
