/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.tosfs.util;

import java.util.concurrent.ThreadLocalRandom;

public class RetryableUtils {
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  private RetryableUtils() {
  }

  /**
   * Copy from {@link org.apache.hadoop.fs.tosfs.shaded.com.volcengine.tos.internal.util.TosUtils#backoff(int)}
   *
   * @param attempt the attempt count
   * @return backoff milliseconds
   */
  public static long backoff(int attempt) {
    if (attempt == 0) {
      return 1000L;
    } else {
      double backoff = 1.0;

      double max;
      for (max = 10.0; backoff < max && attempt > 0; --attempt) {
        backoff *= 1.6;
      }

      if (backoff > max) {
        backoff = max;
      }

      backoff *= 1.0 + 0.2 * (RANDOM.nextDouble(1.0) * 2.0 - 1.0);
      return backoff < 0.0 ? 0L : (long) (backoff * 1000.0);
    }
  }
}
