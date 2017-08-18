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
import org.apache.ignite.yardstick.cache.IgnitePutGetBenchmark;
import org.yardstickframework.*;
import org.yardstickframework.BenchmarkUtils;
import org.yardstickframework.gridgain.GridGainAbstractBenchmark;
import org.yardstickframework.gridgain.cache.*;
import org.yardstickframework.gridgain.compute.GridGainAffinityCallBenchmark;
import org.yardstickframework.gridgain.compute.GridGainApplyBenchmark;

import javax.cache.CacheException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utils.
 */
public class GridGainBenchmarkUtils {
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
    private GridGainBenchmarkUtils() {
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
        final String cfg = "c:\\yard\\ignite\\modules\\yardstick\\config\\gridgain-localhost-config.xml";

        final Class<? extends BenchmarkDriver> benchmark = GridGainPutGetBenchmark.class;

        final int threads = 1;

        final boolean clientDriverNode = true;

        final int extraNodes = 1;

        final int warmUp = 60;
        final int duration = 120;

        final int range = 100_000;

        final boolean throughputLatencyProbe = true;

        ArrayList<String> args0 = new ArrayList<>();

        addArg(args0, "-t", threads);
        addArg(args0, "-w", warmUp);
        addArg(args0, "-d", duration);
        addArg(args0, "-r", range);
        addArg(args0, "-dn", benchmark.getSimpleName());
        addArg(args0, "-sn", "GridGainNode");
        addArg(args0, "-ggcfg", cfg);
        addArg(args0, "-wom", "PRIMARY");

        if (throughputLatencyProbe)
            addArg(args0, "-pr", "ThroughputLatencyProbe");

        if (clientDriverNode)
            args0.add("-cl");

        BenchmarkDriverStartUp.main(args0.toArray(new String[args0.size()]));
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

        BenchmarkUtils.println(cfg, "Preload logger was started.");

        return lgr;
    }
}
