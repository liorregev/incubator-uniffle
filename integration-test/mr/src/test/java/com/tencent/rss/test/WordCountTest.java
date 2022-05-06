/*
 * Tencent is pleased to support the open source community by making
 * Firestorm-Spark remote shuffle server available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.tencent.rss.test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tencent.rss.coordinator.CoordinatorConf;
import com.tencent.rss.server.ShuffleServerConf;
import com.tencent.rss.storage.util.StorageType;
import com.tencent.rss.test.MRIntegrationTestBase;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.WordCount;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.Tool;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class WordCountTest extends MRIntegrationTestBase {

  String inputPath = "word_count_input";
  List<String> wordTable = Lists.newArrayList("apple",
      "banana", "fruit", "cherry", "Chinese", "America", "Japan",
      "tomato");

  @BeforeClass
  public static void setupServers() throws Exception {
    CoordinatorConf coordinatorConf = getCoordinatorConf();
    Map<String, String> dynamicConf = Maps.newHashMap();
    dynamicConf.put(CoordinatorConf.COORDINATOR_REMOTE_STORAGE_PATH.key(), HDFS_URI + "rss/test");
    dynamicConf.put("mapreduce.rss.storage.type", StorageType.HDFS.name());
    addDynamicConf(coordinatorConf, dynamicConf);
    createCoordinatorServer(coordinatorConf);
    ShuffleServerConf shuffleServerConf = getShuffleServerConf();
    createShuffleServer(shuffleServerConf);
    startServers();
  }

  @Test
  public void wordCountTest() throws Exception {
    generateInputFile();
    run();
  }

  @Override
  protected Tool getTestTool() {
    return new TestTool();
  }

  private void generateInputFile() throws Exception {
    FSDataOutputStream outputStream = fs.create(new Path(inputPath));
    Random random = new Random();
    for (int i = 0; i < 100; i++) {
      int index = random.nextInt(wordTable.size());
      String str = wordTable.get(index) + "\n";
      outputStream.writeBytes(str);
    }
    outputStream.close();
  }

  private class TestTool extends WordCount {

    String outputPath = "word_count_output/" + System.currentTimeMillis();

    @Override
    public int run(String[] args) throws Exception {
      JobConf conf = (JobConf) getConf();
      conf.setJobName("wordcount");

      // the keys are words (strings)
      conf.setOutputKeyClass(Text.class);
      // the values are counts (ints)
      conf.setOutputValueClass(IntWritable.class);

      conf.setMapperClass(MapClass.class);
      conf.setCombinerClass(Reduce.class);
      conf.setReducerClass(Reduce.class);
      conf.setNumMapTasks(1);
      conf.setNumReduceTasks(1);
      FileInputFormat.setInputPaths(conf, new Path(inputPath));
      FileOutputFormat.setOutputPath(conf, new Path(outputPath));
      Job job = new Job(conf);
      return job.waitForCompletion(true) ? 0 : 1;
    }
  }
}