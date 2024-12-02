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

package org.apache.hadoop.fs.tosfs.object.tos.auth;

import org.apache.hadoop.fs.tosfs.shaded.com.volcengine.tos.auth.Credentials;
import org.apache.hadoop.conf.Configuration;

import javax.annotation.Nullable;

public interface CredentialsProvider extends Credentials {

  /**
   * Initialize the credential provider.
   *
   * @param conf   the {@link Configuration} used for building credential provider
   * @param bucket the binding bucket, it can be null.
   */
  void initialize(Configuration conf, @Nullable String bucket);
}
