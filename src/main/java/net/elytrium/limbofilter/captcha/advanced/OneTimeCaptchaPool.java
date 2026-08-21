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

package net.elytrium.limbofilter.captcha.advanced;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

public final class OneTimeCaptchaPool<T> {

  private final ArrayBlockingQueue<T> queue;
  private final int capacity;
  private final int lowWaterMark;

  public OneTimeCaptchaPool(int capacity, int lowWaterMark) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    if (lowWaterMark < 0 || lowWaterMark >= capacity) {
      throw new IllegalArgumentException("lowWaterMark must be in range 0..capacity-1");
    }
    this.capacity = capacity;
    this.lowWaterMark = lowWaterMark;
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  public boolean offer(T challenge) {
    return this.queue.offer(Objects.requireNonNull(challenge, "challenge"));
  }

  public T acquire() {
    return this.queue.poll();
  }

  public boolean needsRefill() {
    return this.queue.size() <= this.lowWaterMark;
  }

  public int size() {
    return this.queue.size();
  }

  public int capacity() {
    return this.capacity;
  }

  public int remainingCapacity() {
    return this.queue.remainingCapacity();
  }

  public void clear(Consumer<T> disposer) {
    Objects.requireNonNull(disposer, "disposer");
    T challenge;
    while ((challenge = this.queue.poll()) != null) {
      disposer.accept(challenge);
    }
  }
}
