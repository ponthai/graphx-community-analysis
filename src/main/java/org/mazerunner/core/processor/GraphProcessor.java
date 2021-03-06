package org.mazerunner.core.processor;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.mazerunner.core.algorithms;
import org.mazerunner.core.config.ConfigurationLoader;
import org.mazerunner.core.models.ProcessorMessage;
import org.mazerunner.core.models.ProcessorMode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;

import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;


/**
 * Copyright (C) 2014 Kenny Bastani
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
public class GraphProcessor {

    public static final String PROPERTY_GRAPH_UPDATE_PATH = "/neo4j/mazerunner/propertyUpdateList.txt";
    public static final String PARTITIONED_PROPERTY_GRAPH_UPDATE_PATH = "/neo4j/mazerunner/update/jobs/{job_name}/propertyUpdateList.txt";
    //March3 : Checkpoint Directory
    public static final String CHECKPOINT_DIR = "/neo4j/mazerunner/";

    public static final String TRIANGLE_COUNT = "triangle_count";
    public static final String CONNECTED_COMPONENTS = "connected_components";
    public static final String PAGERANK = "pagerank";
    public static final String STRONGLY_CONNECTED_COMPONENTS = "strongly_connected_components";
    public static final String CLOSENESS_CENTRALITY = "CC";
    public static final String BETWEENNESS_CENTRALITY = "BC";
    public static final String EDGE_BETWEENNESS = "edge_betweenness";
    public static final String COLLABORATIVE_FILTERING = "collaborative_filtering";
    
    //Feb24 : Louvain Community Detection
    public static final String LOUVAIN_COMMUNITY = "LC";

    public static JavaSparkContext javaSparkContext = null;

    public static void processEdgeList(ProcessorMessage processorMessage) throws IOException, URISyntaxException {
        if(javaSparkContext == null) {
            initializeSparkContext();
        }

        Iterable<String> results = new ArrayList<>();

        // Routing
        switch (processorMessage.getAnalysis()) {
            case PAGERANK:
                // Route to PageRank
                results = algorithms.pageRank(javaSparkContext.sc(), processorMessage.getPath());
                break;
            case CONNECTED_COMPONENTS:
                // Route to ConnectedComponents
                results = algorithms.connectedComponents(javaSparkContext.sc(), processorMessage.getPath());
                break;
            case TRIANGLE_COUNT:
                // Route to TriangleCount
                results = algorithms.triangleCount(javaSparkContext.sc(), processorMessage.getPath());
                break;
            case STRONGLY_CONNECTED_COMPONENTS:
                // Route to StronglyConnectedComponents
                results = algorithms.stronglyConnectedComponents(javaSparkContext.sc(), processorMessage.getPath());
                break;
            case CLOSENESS_CENTRALITY:
                // Route to ClosenessCentrality
                results = algorithms.closenessCentrality(javaSparkContext.sc(), 1, processorMessage.getPath());
                break;
            case BETWEENNESS_CENTRALITY:
                // Route to BetweennessCentrality
                results = algorithms.betweennessCentrality(javaSparkContext.sc(), 1, processorMessage.getPath());
                break;
            case EDGE_BETWEENNESS:
                // Route to BetweennessCentrality
                results = algorithms.edgeBetweenness(javaSparkContext.sc(), processorMessage.getPath());
                break;
            case COLLABORATIVE_FILTERING:
                results = algorithms.collaborativeFiltering(javaSparkContext.sc(), processorMessage.getPath());
                break;
            //Feb24 - Louvain Community
            case LOUVAIN_COMMUNITY:
                results = algorithms.louvainCommunityDetection(javaSparkContext.sc(), processorMessage.getPath());
                break;
            default:
                // Analysis does not exist
                System.out.println("Did not recognize analysis key: " + processorMessage.getAnalysis());
        }

        if(processorMessage.getMode() == ProcessorMode.Partitioned) {
            processorMessage.setPath(ConfigurationLoader.getInstance().getHadoopHdfsUri() + PARTITIONED_PROPERTY_GRAPH_UPDATE_PATH.replace("{job_name}", processorMessage.getPartitionDescription().getPartitionId().toString()));
        } else {
            // Set the output path
            processorMessage.setPath(ConfigurationLoader.getInstance().getHadoopHdfsUri() + PROPERTY_GRAPH_UPDATE_PATH);
        }

        // Write results to HDFS
        org.mazerunner.core.hdfs.FileUtil.writePropertyGraphUpdate(processorMessage, results);
    }

    public static JavaSparkContext initializeSparkContext() {
        SparkConf conf = new SparkConf().setAppName(ConfigurationLoader.getInstance().getAppName())
                .setMaster(ConfigurationLoader.getInstance().getSparkHost())
                .set("spark.executor.memory", ConfigurationLoader.getInstance().getExecutorMemory())
                .set("spark.driver.memory", ConfigurationLoader.getInstance().getDriverMemory())
                .set("spark.executor.instances", ConfigurationLoader.getInstance().getExecutorInstances())
                .set("spark.storage.memoryFraction", ConfigurationLoader.getInstance().getMemoryFraction())
                .set("spark.driver.extraJavaOptions", ConfigurationLoader.getInstance().getDriverExtraJava())
                .set("spark.executor.extraJavaOptions", ConfigurationLoader.getInstance().getExecutorExtraJava())
                .set("spark.serializer", ConfigurationLoader.getInstance().getSparkSerializer())
                .set("spark.kryo.registrationRequired", ConfigurationLoader.getInstance().getRegistRequired())
                .set("spark.kryoserializer.buffer", ConfigurationLoader.getInstance().getKryoBuffer())
                .set("spark.rdd.compress", ConfigurationLoader.getInstance().getRddCompress())
                .set("spark.task.maxFailures", ConfigurationLoader.getInstance().getTaskMaxFailure())
                .set("spark.akka.frameSize", ConfigurationLoader.getInstance().getAkkaFrameSize());
                

        javaSparkContext = new JavaSparkContext(conf);
		//javaSparkContext.addJar("/home/not/mazerunner/spark-1.1.5-RELEASE-driver.jar");
        String path = GraphProcessor.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = "";
        try{
            decodedPath = URLDecoder.decode(path, "UTF-8");
        }catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        javaSparkContext.addJar(decodedPath);
        javaSparkContext.setCheckpointDir(CHECKPOINT_DIR);

        return javaSparkContext;
    }
}
