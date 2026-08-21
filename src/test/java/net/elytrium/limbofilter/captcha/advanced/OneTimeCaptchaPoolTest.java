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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OneTimeCaptchaPoolTest {

  @Test
  void enforcesCapacityAndConsumesEntriesExactlyOnce() {
    OneTimeCaptchaPool<String> pool = new OneTimeCaptchaPool<>(3, 1);

    assertTrue(pool.offer("first"));
    assertTrue(pool.offer("second"));
    assertTrue(pool.offer("third"));
    assertFalse(pool.offer("overflow"));
    assertEquals("first", pool.acquire());
    assertEquals("second", pool.acquire());
    assertTrue(pool.needsRefill());
    assertEquals("third", pool.acquire());
    assertNull(pool.acquire());
    assertEquals(0, pool.size());
  }

  @Test
  void clearsAndDisposesEveryRemainingEntry() {
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(4, 1);
    pool.offer(1);
    pool.offer(2);
    pool.offer(3);
    List<Integer> disposed = new ArrayList<>();

    pool.clear(disposed::add);

    assertEquals(List.of(1, 2, 3), disposed);
    assertEquals(0, pool.size());
  }

  @Test
  void closingThePoolRejectsLateGeneratedChallenges() {
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(4, 1);
    pool.offer(1);
    List<Integer> disposed = new ArrayList<>();

    pool.close(disposed::add);

    assertEquals(List.of(1), disposed);
    assertFalse(pool.offer(2));
    assertTrue(pool.isClosed());
  }

  @Test
  void concurrentConsumersNeverAcquireTheSameChallenge() throws InterruptedException {
    int count = 1_000;
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(count, 100);
    for (int value = 0; value < count; ++value) {
      assertTrue(pool.offer(value));
    }

    Set<Integer> acquired = Collections.synchronizedSet(new HashSet<>());
    ExecutorService executor = Executors.newFixedThreadPool(8);
    for (int worker = 0; worker < 8; ++worker) {
      executor.execute(() -> {
        Integer value;
        while ((value = pool.acquire()) != null) {
          acquired.add(value);
        }
      });
    }
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    assertEquals(count, acquired.size());
    assertEquals(0, pool.size());
  }
}
