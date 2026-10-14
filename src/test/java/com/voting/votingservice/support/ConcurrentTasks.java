package com.voting.votingservice.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs many tasks so they start at the same instant (one shared latch) and waits for all of them.
 * Used by the race-condition tests on H2 and on PostgreSQL.
 */
public final class ConcurrentTasks {

    public interface TaskFactory {
        Callable<Boolean> create(int index);
    }

    private ConcurrentTasks() {
    }

    public static List<Boolean> run(int threads, TaskFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = factory.create(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
