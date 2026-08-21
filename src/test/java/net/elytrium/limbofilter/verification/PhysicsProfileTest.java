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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicsProfileTest {

  @Test
  void createsVersionAwareVanillaProfiles() {
    PhysicsProfile legacy = PhysicsProfile.javaLegacy(0.035, 0.0625, 4);
    PhysicsProfile modern = PhysicsProfile.javaModern(0.035, 0.0625, 4);

    assertAll(
        () -> assertEquals(0.08, modern.gravity()),
        () -> assertEquals(0.98, modern.verticalDrag()),
        () -> assertEquals(0.91, modern.horizontalDrag()),
        () -> assertFalse(legacy.impulseSupported()),
        () -> assertTrue(modern.impulseSupported())
    );
  }

  @Test
  void rejectsUnsafeOrUnboundedProfiles() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class,
            () -> new PhysicsProfile(0.08, 0.98, 0.91, 0.0, 0.0625, 4, true)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> new PhysicsProfile(0.08, 0.98, 0.91, 0.035, Double.NaN, 4, true)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> new PhysicsProfile(0.08, 1.1, 0.91, 0.035, 0.0625, 4, true)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> new PhysicsProfile(0.08, 0.98, 0.91, 0.035, 0.0625, 0, true))
    );
  }
}
