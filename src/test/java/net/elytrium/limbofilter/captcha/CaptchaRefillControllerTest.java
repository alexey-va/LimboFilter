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

package net.elytrium.limbofilter.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.elytrium.limbofilter.captcha.advanced.OneTimeCaptchaPool;
import org.junit.jupiter.api.Test;

class CaptchaRefillControllerTest {

  @Test
  void fillsThePoolAndRefillsAfterConsumption() throws InterruptedException {
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(8, 2);
    AtomicInteger sequence = new AtomicInteger();
    CountDownLatch firstFill = new CountDownLatch(8);
    CaptchaRefillController<Integer> controller = new CaptchaRefillController<>(
        pool, 1, () -> {
          firstFill.countDown();
          return sequence.incrementAndGet();
        }, failure -> {
          throw new AssertionError(failure);
        });

    controller.start();
    assertTrue(firstFill.await(5, TimeUnit.SECONDS));
    assertEquals(8, controller.size());
    for (int index = 0; index < 6; ++index) {
      assertNotNull(controller.acquire());
    }

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (controller.size() < 8 && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertEquals(8, controller.size());
    controller.shutdown(ignored -> { });
    assertTrue(controller.isStopped());
  }

  @Test
  void reportsGenerationFailureAndDisposesRemainingChallenges() throws InterruptedException {
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(4, 1);
    AtomicReference<Throwable> observed = new AtomicReference<>();
    CountDownLatch failureSeen = new CountDownLatch(1);
    CaptchaRefillController<Integer> controller = new CaptchaRefillController<>(
        pool, 1, () -> {
          if (pool.size() == 2) {
            throw new IllegalStateException("render failed");
          }
          return pool.size() + 1;
        }, failure -> {
          observed.set(failure);
          failureSeen.countDown();
        });

    controller.start();
    assertTrue(failureSeen.await(5, TimeUnit.SECONDS));
    assertEquals("render failed", observed.get().getMessage());
    assertEquals(2, controller.size());

    List<Integer> disposed = new ArrayList<>();
    controller.shutdown(disposed::add);
    assertEquals(List.of(1, 2), disposed);
    assertFalse(controller.requestRefill());
  }

  @Test
  void disposesAChallengeThatFinishesAfterShutdown() throws InterruptedException {
    OneTimeCaptchaPool<Integer> pool = new OneTimeCaptchaPool<>(4, 1);
    CountDownLatch generatorEntered = new CountDownLatch(1);
    CountDownLatch releaseGenerator = new CountDownLatch(1);
    CountDownLatch disposed = new CountDownLatch(1);
    AtomicInteger disposedValue = new AtomicInteger();
    CaptchaRefillController<Integer> controller = new CaptchaRefillController<>(
        pool, 1, () -> {
          generatorEntered.countDown();
          boolean released = false;
          while (!released) {
            try {
              released = releaseGenerator.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
              // A renderer may finish a CPU-bound image after executor interruption.
            }
          }
          return 99;
        }, failure -> {
          throw new AssertionError(failure);
        });

    controller.start();
    assertTrue(generatorEntered.await(5, TimeUnit.SECONDS));
    controller.shutdown(value -> {
      disposedValue.set(value);
      disposed.countDown();
    });
    releaseGenerator.countDown();

    assertTrue(disposed.await(5, TimeUnit.SECONDS));
    assertEquals(99, disposedValue.get());
    assertEquals(0, controller.size());
  }
}
