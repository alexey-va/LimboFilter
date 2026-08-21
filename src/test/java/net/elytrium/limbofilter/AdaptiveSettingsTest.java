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

package net.elytrium.limbofilter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.elytrium.limbofilter.captcha.advanced.CaptchaFamily;
import org.junit.jupiter.api.Test;

class AdaptiveSettingsTest {

  @Test
  void acceptsDocumentedDefaults() {
    assertDoesNotThrow(() -> Settings.validateAdvancedSettings(
        new Settings.MAIN.ADAPTIVE_VERIFICATION(), new Settings.MAIN.ONE_TIME_CAPTCHA(), false));
  }

  @Test
  void rejectsUnboundedPhysicsSettings() {
    Settings.MAIN.ADAPTIVE_VERIFICATION adaptive = new Settings.MAIN.ADAPTIVE_VERIFICATION();
    adaptive.MAX_PACKET_GAP_TICKS = 0;
    assertInvalid(adaptive, new Settings.MAIN.ONE_TIME_CAPTCHA(), false);

    adaptive = new Settings.MAIN.ADAPTIVE_VERIFICATION();
    adaptive.MAX_SAMPLES_PER_PHASE = 0;
    assertInvalid(adaptive, new Settings.MAIN.ONE_TIME_CAPTCHA(), false);

    adaptive = new Settings.MAIN.ADAPTIVE_VERIFICATION();
    adaptive.MAX_SESSION_MILLIS = 0;
    assertInvalid(adaptive, new Settings.MAIN.ONE_TIME_CAPTCHA(), false);

    adaptive = new Settings.MAIN.ADAPTIVE_VERIFICATION();
    adaptive.PHASES_PER_SESSION = 4;
    assertInvalid(adaptive, new Settings.MAIN.ONE_TIME_CAPTCHA(), false);

    adaptive = new Settings.MAIN.ADAPTIVE_VERIFICATION();
    adaptive.POSITION_TOLERANCE = Double.NaN;
    assertInvalid(adaptive, new Settings.MAIN.ONE_TIME_CAPTCHA(), false);
  }

  @Test
  void rejectsUnsafeCaptchaPoolSettingsAndPreparedPackets() {
    Settings.MAIN.ONE_TIME_CAPTCHA captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    captcha.POOL_SIZE = 15;
    assertInvalid(new Settings.MAIN.ADAPTIVE_VERIFICATION(), captcha, false);

    captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    captcha.REFILL_LOW_WATER_MARK = captcha.POOL_SIZE;
    assertInvalid(new Settings.MAIN.ADAPTIVE_VERIFICATION(), captcha, false);

    captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    captcha.GENERATOR_THREADS = 3;
    assertInvalid(new Settings.MAIN.ADAPTIVE_VERIFICATION(), captcha, false);

    captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    captcha.FAMILIES = List.of();
    assertInvalid(new Settings.MAIN.ADAPTIVE_VERIFICATION(), captcha, false);

    captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    assertInvalid(new Settings.MAIN.ADAPTIVE_VERIFICATION(), captcha, true);

    captcha = new Settings.MAIN.ONE_TIME_CAPTCHA();
    captcha.ENABLED = false;
    captcha.FAMILIES = List.of(CaptchaFamily.TEXT);
    Settings.MAIN.ONE_TIME_CAPTCHA finalCaptcha = captcha;
    assertDoesNotThrow(() -> Settings.validateAdvancedSettings(
        new Settings.MAIN.ADAPTIVE_VERIFICATION(), finalCaptcha, true));
  }

  private static void assertInvalid(Settings.MAIN.ADAPTIVE_VERIFICATION adaptive,
                                    Settings.MAIN.ONE_TIME_CAPTCHA captcha,
                                    boolean preparePackets) {
    assertThrows(IllegalArgumentException.class,
        () -> Settings.validateAdvancedSettings(adaptive, captcha, preparePackets));
  }
}
