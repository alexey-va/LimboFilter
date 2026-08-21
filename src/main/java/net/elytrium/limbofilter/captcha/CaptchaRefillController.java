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

package net.elytrium.limbofilter.captcha;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.elytrium.limbofilter.captcha.advanced.OneTimeCaptchaPool;

public final class CaptchaRefillController<T> {

  private final OneTimeCaptchaPool<T> pool;
  private final Supplier<T> generator;
  private final Consumer<Throwable> errorHandler;
  private final ExecutorService executor;
  private final AtomicBoolean refillScheduled = new AtomicBoolean();
  private final AtomicBoolean stopped = new AtomicBoolean();

  public CaptchaRefillController(OneTimeCaptchaPool<T> pool, int generatorThreads,
                                 Supplier<T> generator, Consumer<Throwable> errorHandler) {
    this.pool = Objects.requireNonNull(pool, "pool");
    this.generator = Objects.requireNonNull(generator, "generator");
    this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    if (generatorThreads < 1 || generatorThreads > 2) {
      throw new IllegalArgumentException("generatorThreads must be in range 1..2");
    }
    this.executor = Executors.newFixedThreadPool(generatorThreads, new CaptchaThreadFactory());
  }

  public void start() {
    this.requestRefill();
  }

  public T acquire() {
    T challenge = this.pool.acquire();
    if (this.pool.needsRefill()) {
      this.requestRefill();
    }
    return challenge;
  }

  public boolean requestRefill() {
    if (this.stopped.get() || !this.pool.needsRefill()
        || !this.refillScheduled.compareAndSet(false, true)) {
      return false;
    }
    this.executor.execute(this::refill);
    return true;
  }

  public int size() {
    return this.pool.size();
  }

  public boolean isStopped() {
    return this.stopped.get();
  }

  public void shutdown(Consumer<T> disposer) {
    if (this.stopped.compareAndSet(false, true)) {
      this.executor.shutdownNow();
    }
    this.pool.clear(disposer);
  }

  private void refill() {
    boolean failed = false;
    try {
      while (!this.stopped.get() && this.pool.remainingCapacity() > 0) {
        T challenge = this.generator.get();
        if (challenge == null || !this.pool.offer(challenge)) {
          break;
        }
      }
    } catch (Throwable failure) {
      failed = true;
      this.errorHandler.accept(failure);
    } finally {
      this.refillScheduled.set(false);
      if (!failed && !this.stopped.get() && this.pool.needsRefill()) {
        this.requestRefill();
      }
    }
  }

  private static final class CaptchaThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "OneTimeCaptchaGenerator-" + this.counter.incrementAndGet());
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
    }
  }
}
