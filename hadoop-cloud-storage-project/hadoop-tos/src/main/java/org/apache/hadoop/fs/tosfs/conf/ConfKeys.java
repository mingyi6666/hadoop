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

package org.apache.hadoop.fs.tosfs.conf;

import org.apache.hadoop.thirdparty.com.google.common.base.Preconditions;

import java.util.List;

public class ConfKeys {

  /**
   * Object storage endpoint to connect to, which should include both region and object domain name.
   * e.g. 'fs.tos.endpoint'='tos-cn-beijing.volces.com'.
   */
  public static final ArgumentKey FS_TOS_ENDPOINT = new ArgumentKey("fs.%s.endpoint");

  /**
   * The region of the object storage, e.g. fs.tos.region. Parsing template "fs.%s.endpoint" to
   * know the region.
   */
  public static final ArgumentKey FS_TOS_REGION = new ArgumentKey("fs.%s.region");

  /**
   * The object storage implementation for the defined scheme. For example, we can delegate the
   * scheme 'abc' to TOS (or other object storage),and access the TOS object storage as
   * 'abc://bucket/path/to/key'
   */
  public static final ArgumentKey FS_OBJECT_STORAGE_IMPL =
      new ArgumentKey("fs.objectstorage.%s.impl");

  /**
   * The batch size of deleting multiple objects per request for the given object storage.
   * e.g. fs.tos.delete.batch-size
   */
  public static final ArgumentKey FS_BATCH_DELETE_SIZE = new ArgumentKey("fs.%s.delete.batch-size");
  public static final int FS_BATCH_DELETE_SIZE_DEFAULT = 250;

  /**
   * The multipart upload part size of the given object storage, e.g. fs.tos.multipart.size.
   */
  public static final String MULTIPART_SIZE = "fs.tos.multipart.size";
  public static final long MULTIPART_SIZE_DEFAULT = 8L << 20;

  /**
   * The threshold (larger than this value) to enable multipart upload during copying objects
   * in the given object storage. If the copied data size is less than threshold, will copy data via
   * executing copyObject instead of uploadPartCopy. E.g. fs.tos.multipart.copy-threshold
   */
  public static final String MULTIPART_COPY_THRESHOLD = "fs.tos.multipart.copy-threshold";
  public static final long MULTIPART_COPY_THRESHOLD_DEFAULT = 5L << 20;

  /**
   * The threshold which control whether enable multipart upload during writing data to the given
   * object storage, if the write data size is less than threshold, will write data via simple put
   * instead of multipart upload. E.g. fs.tos.multipart.threshold.
   */
  public static final String MULTIPART_THRESHOLD = "fs.tos.multipart.threshold";
  public static final long MULTIPART_THRESHOLD_DEFAULT = 10 << 20;

  /**
   * The max byte size which will buffer the staging data in-memory before flushing to the staging
   * file. It will decrease the random write in local staging disk dramatically if writing plenty of
   * small files.
   */
  public static final String MULTIPART_STAGING_BUFFER_SIZE = "fs.tos.multipart.staging-buffer-size";
  public static final int MULTIPART_STAGING_BUFFER_SIZE_DEFAULT = 4 << 10;

  /**
   * The multipart upload part staging dir(s) of the given object storage.
   * e.g. fs.tos.multipart.staging-dir.
   * Separate the staging dirs with comma if there are many staging dir paths.
   */
  public static final String MULTIPART_STAGING_DIR = "fs.tos.multipart.staging-dir";
  public static final String MULTIPART_STAGING_DIR_DEFAULT = defaultDir("multipart-staging-dir");

  /**
   * The batch size of deleting multiple objects per request for the given object storage.
   * e.g. fs.tos.delete.batch-size
   */
  public static final String BATCH_DELETE_SIZE = "fs.tos.delete.batch-size";
  public static final int BATCH_DELETE_SIZE_DEFAULT = 250;

  /**
   * True to create the missed parent dir asynchronously during deleting or renaming a file or dir.
   */
  public static final String ASYNC_CREATE_MISSED_PARENT = "fs.tos.missed.parent.dir.async-create";
  public static final boolean ASYNC_CREATE_MISSED_PARENT_DEFAULT = true;

  /**
   * Whether using rename semantic of object storage during rename files, otherwise using
   * copy + delete.
   * Please ensure that the object storage support and enable rename semantic and before enable it,
   * and also ensure grant rename permission to the requester.
   * If you are using TOS, you have to send putBucketRename request before sending rename request,
   * otherwise MethodNotAllowed exception will be thrown.
   */
  public static final String OBJECT_RENAME_ENABLED = "fs.tos.rename.enabled";
  public static final boolean OBJECT_RENAME_ENABLED_DEFAULT = false;

  /**
   * The range size when open object storage input stream. Value must be positive.
   */
  public static final String OBJECT_STREAM_RANGE_SIZE = "proton.objectstorage.stream.range-size";
  public static final long OBJECT_STREAM_RANGE_SIZE_DEFAULT = Long.MAX_VALUE;

  public static String defaultDir(String basename) {
    String tmpdir = System.getProperty("java.io.tmpdir");
    Preconditions.checkNotNull(tmpdir, "System property 'java.io.tmpdir' cannot be null");

    if (tmpdir.endsWith("/")) {
      return String.format("%s%s", tmpdir, basename);
    } else {
      return String.format("%s/%s", tmpdir, basename);
    }
  }
}
