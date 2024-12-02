/*
 * ByteDance Volcengine EMR, Copyright 2022.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.examples.terasort.TeraGen;
import org.apache.hadoop.examples.terasort.TeraSort;
import org.apache.hadoop.examples.terasort.TeraSortConfigKeys;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.tosfs.TestEnv;
import org.apache.hadoop.fs.tosfs.object.ObjectInfo;
import org.apache.hadoop.fs.tosfs.object.ObjectStorage;
import org.apache.hadoop.fs.tosfs.object.ObjectStorageFactory;
import org.apache.hadoop.fs.tosfs.object.ObjectUtils;
import org.apache.hadoop.fs.tosfs.util.UUIDUtils;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.WordCount;
import org.apache.hadoop.mapreduce.v2.MiniMRYarnCluster;
import org.apache.hadoop.mapreduce.v2.jobhistory.JHAdminConfig;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class MRJobTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(MRJobTestBase.class);

  private static Configuration conf = new Configuration();
  private static MiniMRYarnCluster yarnCluster;

  private static FileSystem fs;

  private static Path testDataPath;

  public static void setConf(Configuration newConf) {
    conf = newConf;
  }

  @BeforeClass
  public static void beforeClass() throws IOException {
    Assume.assumeTrue(TestEnv.checkTestEnabled());

    conf.setBoolean(JHAdminConfig.MR_HISTORY_CLEANER_ENABLE, false);
    conf.setBoolean(YarnConfiguration.NM_DISK_HEALTH_CHECK_ENABLE, false);
    conf.setInt(YarnConfiguration.NM_MAX_PER_DISK_UTILIZATION_PERCENTAGE, 100);

    conf.set("mapreduce.outputcommitter.factory.scheme.tos",
        CommitterFactory.class.getName()); // 3x newApiCommitter=true.
    conf.set("mapred.output.committer.class",
        Committer.class.getName()); // 2x and 3x newApiCommitter=false.
    conf.set("mapreduce.outputcommitter.class",
        org.apache.hadoop.fs.tosfs.commit.Committer.class.getName()); // 2x newApiCommitter=true.

    // Start the yarn cluster.
    yarnCluster = new MiniMRYarnCluster("yarn-" + System.currentTimeMillis(), 2);
    LOG.info("Default filesystem: {}", conf.get("fs.defaultFS"));
    LOG.info("Default filesystem implementation: {}", conf.get("fs.AbstractFileSystem.tos.impl"));

    yarnCluster.init(conf);
    yarnCluster.start();

    fs = FileSystem.get(conf);
    testDataPath = new Path("/mr-test-" + UUIDUtils.random())
        .makeQualified(fs.getUri(), fs.getWorkingDirectory());
  }

  @AfterClass
  public static void afterClass() throws IOException {
    if (!TestEnv.checkTestEnabled()) {
      return;
    }

    fs.delete(testDataPath, true);
    if (yarnCluster != null) {
      yarnCluster.stop();
    }
  }

  @After
  public void after() throws IOException {
  }

  @Test
  public void testTeraGen() throws Exception {
    Path teraGenPath = new Path(testDataPath, "teraGen").makeQualified(fs.getUri(), fs.getWorkingDirectory());
    Path output = new Path(teraGenPath, "output");
    JobConf jobConf = new JobConf(yarnCluster.getConfig());
    jobConf.addResource(conf);
    jobConf.setInt(TeraSortConfigKeys.SAMPLE_SIZE.key(), 1000);
    jobConf.setInt(TeraSortConfigKeys.NUM_PARTITIONS.key(), 10);
    jobConf.setBoolean(TeraSortConfigKeys.USE_SIMPLE_PARTITIONER.key(), false);

    String[] args = new String[]{Integer.toString(1000), output.toString()};
    int result = ToolRunner.run(jobConf, new TeraGen(), args);
    Assert.assertEquals(String.format("teragen %s", StringUtils.join(" ", args)), 0, result);

    // Verify the success data.
    ObjectStorage storage = ObjectStorageFactory.create(
        output.toUri().getScheme(), output.toUri().getAuthority(), conf);
    int byteSizes = 0;

    Path success = new Path(output, CommitUtils._SUCCESS);
    byte[] serializedData = CommitUtils.load(fs, success);
    SuccessData successData = SuccessData.deserialize(serializedData);
    Assert.assertTrue("Should execute successfully", successData.success());
    // Assert the destination paths.
    Assert.assertEquals(2, successData.filenames().size());
    successData.filenames().sort(String::compareTo);
    Assert.assertEquals(ObjectUtils.pathToKey(new Path(output, "part-m-00000")),
        successData.filenames().get(0));
    Assert.assertEquals(ObjectUtils.pathToKey(new Path(output, "part-m-00001")),
        successData.filenames().get(1));

    for (String partFileKey : successData.filenames()) {
      ObjectInfo objectInfo = storage.head(partFileKey);
      Assert.assertNotNull("Output file should be existing", objectInfo);
      byteSizes += objectInfo.size();
    }

    Assert.assertEquals(byteSizes, 100 /* Each row 100 bytes */ * 1000 /* total 1000 rows */);
  }

  @Test
  public void testTeraSort() throws Exception {
    Path teraGenPath = new Path(testDataPath, "teraGen").makeQualified(fs.getUri(), fs.getWorkingDirectory());
    Path inputPath = new Path(teraGenPath, "output");
    Path outputPath = new Path(teraGenPath, "sortOutput");
    JobConf jobConf = new JobConf(yarnCluster.getConfig());
    jobConf.addResource(conf);
    jobConf.setInt(TeraSortConfigKeys.SAMPLE_SIZE.key(), 1000);
    jobConf.setInt(TeraSortConfigKeys.NUM_PARTITIONS.key(), 10);
    jobConf.setBoolean(TeraSortConfigKeys.USE_SIMPLE_PARTITIONER.key(), false);
    String[] args = new String[]{inputPath.toString(), outputPath.toString()};
    int result = ToolRunner.run(jobConf, new TeraSort(), args);
    Assert.assertEquals(String.format("terasort %s", StringUtils.join(" ", args)), 0, result);

    // Verify the success data.
    ObjectStorage storage = ObjectStorageFactory
        .create(outputPath.toUri().getScheme(), outputPath.toUri().getAuthority(), conf);
    int byteSizes = 0;

    Path success = new Path(outputPath, CommitUtils._SUCCESS);
    byte[] serializedData = CommitUtils.load(fs, success);
    SuccessData successData = SuccessData.deserialize(serializedData);
    Assert.assertTrue("Should execute successfully", successData.success());
    // Assert the destination paths.
    Assert.assertEquals(1, successData.filenames().size());
    successData.filenames().sort(String::compareTo);
    Assert.assertEquals(ObjectUtils.pathToKey(new Path(outputPath, "part-r-00000")), successData.filenames().get(0));

    for (String partFileKey : successData.filenames()) {
      ObjectInfo objectInfo = storage.head(partFileKey);
      Assert.assertNotNull("Output file should be existing", objectInfo);
      byteSizes += objectInfo.size();
    }

    Assert.assertEquals(byteSizes, 100 /* Each row 100 bytes */ * 1000 /* total 1000 rows */);
  }

  @Ignore
  @Test
  public void testWordCount() throws Exception {
    Path wordCountPath = new Path(testDataPath, "wc").makeQualified(fs.getUri(), fs.getWorkingDirectory());
    Path output = new Path(wordCountPath, "output");
    Path input = new Path(wordCountPath, "input");
    JobConf jobConf = new JobConf(yarnCluster.getConfig());
    jobConf.addResource(conf);

    if (!fs.mkdirs(input)) {
      throw new IOException("Mkdirs failed to create " + input.toString());
    }

    DataOutputStream file = fs.create(new Path(input, "part-0"));
    file.writeBytes("a a b c");
    file.close();

    String[] args = new String[]{input.toString(), output.toString()};
    int result = ToolRunner.run(jobConf, new WordCount(), args);
    Assert.assertEquals(String.format("WordCount %s", StringUtils.join(" ", args)), 0, result);

    // Verify the success path.
    Assert.assertTrue(fs.exists(new Path(output, CommitUtils._SUCCESS)));
    Assert.assertTrue(fs.exists(new Path(output, "part-00000")));

    Path success = new Path(output, CommitUtils._SUCCESS);
    Assert.assertTrue("Success file must be not empty", CommitUtils.load(fs, success).length != 0);

    byte[] serializedData = CommitUtils.load(fs, new Path(output, "part-00000"));
    String outputAsStr = new String(serializedData);
    Map<String, Integer> resAsMap = getResultAsMap(outputAsStr);
    Assert.assertEquals(2, (int) resAsMap.get("a"));
    Assert.assertEquals(1, (int) resAsMap.get("b"));
    Assert.assertEquals(1, (int) resAsMap.get("c"));
  }

  private Map<String, Integer> getResultAsMap(String outputAsStr) {
    Map<String, Integer> result = new HashMap<>();
    for (String line : outputAsStr.split("\n")) {
      String[] tokens = line.split("\t");
      Assert.assertTrue(String.format("Not enough tokens in in string %s from output %s", line, outputAsStr),
          tokens.length > 1);
      result.put(tokens[0], Integer.parseInt(tokens[1]));
    }
    return result;
  }
}
