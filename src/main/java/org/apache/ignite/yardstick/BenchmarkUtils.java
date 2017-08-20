/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.yardstick;

import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterTopologyException;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.transactions.*;
import org.apache.ignite.yardstick.cache.IgnitePutBenchmark;
import org.yardstickframework.BenchmarkConfiguration;
import org.yardstickframework.BenchmarkDriver;
import org.yardstickframework.BenchmarkDriverStartUp;

import javax.cache.CacheException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utils.
 */
public class BenchmarkUtils {

    /** */
    private static final String IGNITE_CFG = "c:\\yard\\ignite\\modules\\yardstick\\config\\ignite-localhost-config.xml";

    /** */
    private static final String GRID_GAIN_CFG = "c:\\yard\\ignite\\modules\\yardstick\\config\\gridgain-localhost-config.xml";

    /** */
    private static final String [] FOLDER_NAMES = {"res_gridgain", "res_ignite"};

    /** */
    private static String IGNITE_NODE_NAME = "IgniteNode";

    /** */
    private static String GRID_GAIN_NODE_NAME = "GridGainNode";

    /** */
    private static List<Class<? extends BenchmarkDriver>> igniteBenchmarks = new ArrayList<>();

    /** */
    private static List<Class<? extends BenchmarkDriver>> gridGainBenchmarks = new ArrayList<>();

    /** */
    private static final int THREADS = 1;

    /** */
    private static final boolean CLIENT_DRIVER_NODE = true;

    /** */
    private static final int EXTRA_NODES = 1;

    /** */
    private static final int WARM_UP = 60;

    /** */
    private static final int DURATION = 120;

    /** */
    private static final int RANGE = 100_000;



    /** */
    private static final boolean THROUGHPUT_LATENCY_PROBE = true;

    /** */
    private static final String GRAPH_PLOTTER_TYPE = "COMPOUND";

    /**
     * Scheduler executor.
     */
    private static final ScheduledExecutorService exec =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable run) {
                Thread thread = Executors.defaultThreadFactory().newThread(run);

                thread.setDaemon(true);

                return thread;
            }
        });

    /**
     * Utility class constructor.
     */
    private BenchmarkUtils() {
        // No-op.
    }

    /**
     * @param igniteTx Ignite transaction.
     * @param txConcurrency Transaction concurrency.
     * @param clo Closure.
     * @return Result of closure execution.
     * @throws Exception If failed.
     */
    public static <T> T doInTransaction(IgniteTransactions igniteTx, TransactionConcurrency txConcurrency,
        TransactionIsolation txIsolation, Callable<T> clo) throws Exception {
        while (true) {
            try (Transaction tx = igniteTx.txStart(txConcurrency, txIsolation)) {
                T res = clo.call();

                tx.commit();

                return res;
            }
            catch (CacheException e) {
                if (e.getCause() instanceof ClusterTopologyException) {
                    ClusterTopologyException topEx = (ClusterTopologyException)e.getCause();

                    topEx.retryReadyFuture().get();
                }
                else
                    throw e;
            }
            catch (ClusterTopologyException e) {
                e.retryReadyFuture().get();
            }
            catch (TransactionRollbackException | TransactionOptimisticException ignore) {
                // Safe to ight away.
            }
        }
    }

    /**
     * Starts nodes/driver in single JVM for quick benchmarks testing.
     *
     * @param args Command line arguments.
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {

        String gridGainBenchmarks = "GridGainPutBenchmark,GridGainPutGetBenchmark";

        gridGainBenchmarks.concat("oridGainPutBenchmark");
//        gridGainBenchmarks.add(GridGainPutGetBenchmark.class);
//        gridGainBenchmarks.add(GridGainPutTxBenchmark.class);
//        gridGainBenchmarks.add(GridGainPutGetTxBenchmark.class);
//        gridGainBenchmarks.add(GridGainSqlQueryBenchmark.class);
//        gridGainBenchmarks.add(GridGainSqlQueryJoinBenchmark.class);
//        gridGainBenchmarks.add(GridGainSqlQueryPutBenchmark.class);
//        gridGainBenchmarks.add(GridGainAffinityCallBenchmark.class);
//        gridGainBenchmarks.add(GridGainApplyBenchmark.class);
//        gridGainBenchmarks.add(GridGainBroadcastBenchmark.class);
//        gridGainBenchmarks.add(GridGainExecuteBenchmark.class);
//        gridGainBenchmarks.add(GridGainRunBenchmark.class);

        benchmarkDriverStartUp("-ggcfg", GRID_GAIN_CFG, gridGainBenchmarks, GRID_GAIN_NODE_NAME);

        igniteBenchmarks.add(IgnitePutBenchmark.class);
//        igniteBenchmarks.add(IgnitePutGetBenchmark.class);
//        igniteBenchmarks.add(IgnitePutTxBenchmark.class);
//        igniteBenchmarks.add(IgnitePutGetTxBenchmark.class);
//        igniteBenchmarks.add(IgniteSqlQueryBenchmark.class);
//        igniteBenchmarks.add(IgniteSqlQueryJoinBenchmark.class);
//        igniteBenchmarks.add(IgniteSqlQueryPutBenchmark.class);
//        igniteBenchmarks.add(IgniteAffinityCallBenchmark.class);
//        igniteBenchmarks.add(IgniteApplyBenchmark.class);
//        igniteBenchmarks.add(IgniteBroadcastBenchmark.class);
//        igniteBenchmarks.add(IgniteExecuteBenchmark.class);
//        igniteBenchmarks.add(IgniteRunBenchmark.class);

          benchmarkDriverStartUp("-cfg", IGNITE_CFG, "IgnitePutBenchmark,IgnitePutGetBenchmark", IGNITE_NODE_NAME);

        jFreeChartGraphPlotter();
    }

    public static void benchmarkDriverStartUp(String cfgParameter, String cfg, String benchmarks, String nodeName)
            throws Exception {

            IgniteConfiguration nodeCfg = Ignition.loadSpringBean(cfg, "grid.cfg");

            nodeCfg.setIgniteInstanceName("node-0");
            nodeCfg.setMetricsLogFrequency(0);

            Ignition.start(nodeCfg);

//        for (Class<? extends BenchmarkDriver> benchmark : benchmarks) {
            ArrayList<String> args0 = new ArrayList<>();

            addArg(args0, "-t", THREADS);
            addArg(args0, "-w", WARM_UP);
            addArg(args0, "-d", DURATION);
            addArg(args0, "-r", RANGE);
            addArg(args0, "-dn", benchmarks);
            addArg(args0, "-sn", nodeName);
            addArg(args0, cfgParameter, cfg);
            addArg(args0, "-wom", "PRIMARY");

            if (THROUGHPUT_LATENCY_PROBE)
                addArg(args0, "-pr", "ThroughputLatencyProbe");

            if (CLIENT_DRIVER_NODE)
                args0.add("-cl");

            BenchmarkDriverStartUp.main(args0.toArray(new String[args0.size()]));
//        }
    }

    public static void jFreeChartGraphPlotter () {
        ArrayList<String> args1 = new ArrayList<>();

        addArg(args1, "-gm", GRAPH_PLOTTER_TYPE);
        addArg(args1, "-i", "20170820-095054-GridGainPutBenchmark-GridGainPutGetBenchmark");
        addArg(args1, "-i", "20170820-103822-IgnitePutBenchmark-IgnitePutGetBenchmark");

        JFreeChartGraphPlotterBenchmark.main(args1.toArray(new String[args1.size()]));
    }

    /**
     * @param args Arguments.
     * @param arg Argument name.
     * @param val Argument value.
     */
    private static void addArg(List<String> args, String arg, Object val) {
        args.add(arg);
        args.add(val.toString());
    }

    /**
     * Prints non-system cache sizes during preload.
     *
     * @param node Ignite node.
     * @param cfg Benchmark configuration.
     * @param logsInterval Time interval in milliseconds between printing logs.
     */
    public static PreloadLogger startPreloadLogger(IgniteNode node, BenchmarkConfiguration cfg, long logsInterval) {
        PreloadLogger lgr = new PreloadLogger(node, cfg);

        ScheduledFuture<?> fut = exec.scheduleWithFixedDelay(lgr, 0L, logsInterval, TimeUnit.MILLISECONDS);

        lgr.setFuture(fut);

        org.yardstickframework.BenchmarkUtils.println(cfg, "Preload logger was started.");

        return lgr;
    }
}
