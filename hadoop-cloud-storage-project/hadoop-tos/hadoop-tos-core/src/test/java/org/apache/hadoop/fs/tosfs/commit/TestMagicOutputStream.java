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

package org.apache.hadoop.fs.tosfs.commit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.tosfs.common.ThreadPools;
import org.apache.hadoop.fs.tosfs.object.MultipartUpload;
import org.apache.hadoop.fs.tosfs.object.ObjectStorageTestBase;
import org.apache.hadoop.fs.tosfs.object.ObjectUtils;
import org.apache.hadoop.fs.tosfs.object.Part;
import org.apache.hadoop.fs.tosfs.object.staging.StagingPart;
import org.apache.hadoop.fs.tosfs.object.staging.State;
import org.apache.hadoop.fs.tosfs.util.TestUtility;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TestMagicOutputStream extends ObjectStorageTestBase {

  private static ExecutorService threadPool;

  @BeforeClass
  public static void beforeClass() {
    threadPool = ThreadPools.newWorkerPool("TestMagicOutputStream-pool");
  }

  @AfterClass
  public static void afterClass() {
    if (!threadPool.isShutdown()) {
      threadPool.shutdown();
    }
  }

  private static Path path(String p) {
    return new Path(p);
  }

  private static Path path(Path parent, String child) {
    return new Path(parent, child);
  }

  @Test
  public void testCreateDestKey() {
    Object[][] testCases = new Object[][]{
        new Object[]{path("tos://bucket/__magic/a.txt"), "a.txt"},
        new Object[]{path("tos://bucket/output/__magic/job-1/tasks/tasks-attempt-0/a.txt"), "output/a.txt"},
        new Object[]{path("tos://bucket/__magic/job0/task0/__base/a.txt"), "a.txt"},
        new Object[]{path("tos://bucket/output/__magic/job0/task0/__base/part/part-m-1000"), "output/part/part-m-1000"},
        new Object[]{path("tos://bucket/a/b/c/__magic/__base/d/e/f"), "a/b/c/d/e/f"},
        new Object[]{path("tos://bucket/a/b/c/__magic/d/e/f"), "a/b/c/f"},
    };

    for (Object[] input : testCases) {
      String actualDestKey = MagicOutputStream.toDestKey((Path) input[0]);
      Assert.assertEquals("Unexpected destination key.", actualDestKey, input[1]);
    }
  }

  @Test
  public void testNonMagicPath() {
    try (MagicOutputStream ignored = new TestingMagicOutputStream(path(testDir, "non-magic"))) {
      Assert.fail("Cannot create magic output stream for non-magic path");
    } catch (Exception ignored) {
    }
  }

  @Test
  public void testWriteZeroByte() throws IOException {
    Path magic = path(path(testDir, CommitUtils.MAGIC), "zero-byte.txt");
    MagicOutputStream out = new TestingMagicOutputStream(magic);
    // write zero-byte and close.
    out.close();
    assertStagingFiles(0, out.stagingParts());

    // Read and validate the .pending contents
    try (InputStream in = storage.get(out.pendingKey()).stream()) {
      byte[] data = IOUtils.toByteArray(in);
      Pending commit = Pending.deserialize(data);
      Assert.assertEquals(storage.bucket().name(), commit.bucket());
      Assert.assertEquals(out.destKey(), commit.destKey());
      Assert.assertTrue(StringUtils.isNoneEmpty(commit.uploadId()));
      Assert.assertTrue(commit.createdTimestamp() > 0);
      Assert.assertEquals(1, commit.parts().size());
      Assert.assertEquals(0, commit.length());
      Assert.assertEquals(out.upload().uploadId(), commit.uploadId());
    }
  }

  public void testWrite(int len) throws IOException {
    Path magic = path(path(testDir, CommitUtils.MAGIC), len + ".txt");
    int uploadPartSize = 8 << 20;
    int partNum = (len - 1) / (8 << 20) + 1;

    MagicOutputStream out = new TestingMagicOutputStream(magic);
    byte[] data = TestUtility.rand(len);
    out.write(data);
    out.close();

    assertStagingFiles(partNum, out.stagingParts());
    Assert.assertEquals(ObjectUtils.pathToKey(magic) + CommitUtils.PENDING_SUFFIX, out.pendingKey());

    Pending commit;
    try (InputStream in = storage.get(out.pendingKey()).stream()) {
      byte[] serializedData = IOUtils.toByteArray(in);
      commit = Pending.deserialize(serializedData);
      Assert.assertEquals(storage.bucket().name(), commit.bucket());
      Assert.assertEquals(out.destKey(), commit.destKey());
      Assert.assertTrue(commit.createdTimestamp() > 0);
      Assert.assertEquals(len, commit.length());
      Assert.assertEquals(out.upload().uploadId(), commit.uploadId());
      // Verify the upload part list.
      Assert.assertEquals(partNum, commit.parts().size());
      if (!commit.parts().isEmpty()) {
        for (int i = 0; i < partNum - 1; i += 1) {
          Assert.assertEquals(uploadPartSize, commit.parts().get(i).size());
        }
        Part lastPart = commit.parts().get(partNum - 1);
        Assert.assertTrue(lastPart.size() > 0 && lastPart.size() <= uploadPartSize);
      }
    }

    // List multipart uploads
    int uploadsNum = 0;
    for (MultipartUpload upload : storage.listUploads(out.destKey())) {
      uploadsNum += 1;
      Assert.assertEquals(out.upload(), upload);
    }
    Assert.assertEquals(1L, uploadsNum);

    // The target object is still not visible for object storage.
    Assert.assertNull(storage.head(out.destKey()));

    // Complete the upload and validate the content.
    storage.completeUpload(out.destKey(), out.upload().uploadId(), commit.parts());
    try (InputStream in = storage.get(out.destKey()).stream()) {
      Assert.assertArrayEquals(data, IOUtils.toByteArray(in));
    }
  }

  @Test
  public void testWrite1MB() throws IOException {
    testWrite(1 << 20);
  }

  @Test
  public void testWrite24MB() throws IOException {
    testWrite(24 << 20);
  }

  @Test
  public void testWrite100MB() throws IOException {
    testWrite(100 << 20);
  }

  private static void assertStagingFiles(int expectedNum, List<StagingPart> stagings) {
    Assert.assertEquals(expectedNum, stagings.size());
    for (StagingPart staging : stagings) {
      Assert.assertEquals(State.CLEANED, staging.state());
    }
  }

  private class TestingMagicOutputStream extends MagicOutputStream {

    TestingMagicOutputStream(Path magic) {
      super(fs, storage, threadPool, tosConf, magic);
    }

    protected void persist(Path p, byte[] data) {
      storage().put(ObjectUtils.pathToKey(p), data);
    }
  }
}
