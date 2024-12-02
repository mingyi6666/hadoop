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

package org.apache.hadoop.fs.tosfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.fs.tosfs.util.TestUtility;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertThrows;

public class TestTosFileSystem {

  @BeforeClass
  public static void before() {
    Assume.assumeTrue(TestEnv.checkTestEnabled());
  }

  @Test
  public void testUriVerification() throws URISyntaxException, IOException {
    Configuration conf = new Configuration(false);
    conf.set(CommonConfigurationKeys.FS_DEFAULT_NAME_KEY, "hdfs://cluster-0/");

    TosFileSystem tfs = new TosFileSystem();
    assertThrows("Expect invalid uri error.", IllegalArgumentException.class,
        () -> tfs.initialize(new URI("hdfs://cluster/"), conf));
    assertThrows("Expect invalid uri error.", IllegalArgumentException.class,
        () -> tfs.initialize(new URI("/path"), conf));
    tfs.initialize(new URI(String.format("tos://%s/", TestUtility.bucket())), conf);
  }
}
