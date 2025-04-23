package org.example.algorithms;

import org.example.model.Grid;

import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GameOfLifeParallel {
    private static ExecutorService threadPool;
    private static int lastThreadCount = -1;
    private static final Object threadPoolLock = new Object();
    private static boolean isShuttingDown = false;

    public void simulate(Grid grid, int steps, int threads) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        int actualThreads = Math.min(threads, maxThreads);

        if (actualThreads != threads) {
            System.out.println("Warning: Requested " + threads + " threads, but only using "
                    + actualThreads + " (system maximum)");
        }

        initializeThreadPool(actualThreads);

        for (int i = 0; i < steps; i++) {
            step(grid, actualThreads);
        }
    }

    private void initializeThreadPool(int threads) {
        synchronized (threadPoolLock) {
            if (threadPool == null || lastThreadCount != threads || threadPool.isShutdown() || threadPool.isTerminated()) {
                if (threadPool != null && !threadPool.isShutdown()) {
                    // Don't shutdown
                }

                ThreadFactory threadFactory = new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "GameOfLife-Worker-" + threadNumber.getAndIncrement());
                        t.setPriority(Thread.MAX_PRIORITY);
                        t.setDaemon(true);
                        return t;
                    }
                };

                threadPool = Executors.newFixedThreadPool(threads, threadFactory);
                lastThreadCount = threads;
                System.out.println("Created new thread pool with " + threads + " threads");
            }
        }
    }

    public void step(Grid grid, int threads) {
        BitSet current = grid.getCurrent();
        BitSet next = grid.getNext();
        int width = grid.getWidth();
        int height = grid.getHeight();

        next.clear();

        BitSet cellsToCheck = new BitSet(width * height);

        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;

            for (int r = Math.max(0, row - 1); r <= Math.min(height - 1, row + 1); r++) {
                for (int c = Math.max(0, col - 1); c <= Math.min(width - 1, col + 1); c++) {
                    cellsToCheck.set(grid.getIndex(r, c));
                }
            }
        }

        int cellCount = cellsToCheck.cardinality();
        int effectiveThreads = Math.min(threads, Math.max(1, cellCount / 1000));

        if (cellCount == 0) {
            grid.swapGrids();
            return;
        }

        final int[] cellIndices = new int[cellCount];
        int idx = 0;
        for (int i = cellsToCheck.nextSetBit(0); i >= 0; i = cellsToCheck.nextSetBit(i + 1)) {
            cellIndices[idx++] = i;
        }

        final int cellsPerThread = Math.max(1, cellCount / effectiveThreads);
        final CountDownLatch latch = new CountDownLatch(effectiveThreads);

        final BitSet[] localNextSets = new BitSet[effectiveThreads];
        for (int i = 0; i < effectiveThreads; i++) {
            localNextSets[i] = new BitSet(width * height);
        }

        for (int i = 0; i < effectiveThreads; i++) {
            final int threadIdx = i;
            final int startIdx = i * cellsPerThread;
            final int endIdx = (i == effectiveThreads - 1) ? cellCount : (i + 1) * cellsPerThread;

            if (startIdx >= cellCount) {
                latch.countDown();
                continue;
            }

            synchronized (threadPoolLock) {
                if (!isShuttingDown && threadPool != null && !threadPool.isShutdown()) {
                    threadPool.execute(() -> {
                        try {
                            BitSet localNext = localNextSets[threadIdx];

                            for (int j = startIdx; j < endIdx; j++) {
                                int cellIdx = cellIndices[j];
                                int row = cellIdx / width;
                                int col = cellIdx % width;
                                applyRules(grid, row, col, localNext);
                            }

                            synchronized (next) {
                                next.or(localNext);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                } else {
                    latch.countDown();
                }
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted during parallel step: " + e.getMessage());
        }

        grid.swapGrids();
    }

    private void applyRules(Grid grid, int row, int col, BitSet threadLocalNext) {
        int liveNeighbors = countLiveNeighbors(grid, row, col);
        boolean isAlive = grid.getCell(row, col);

        boolean newState;
        if (isAlive) {
            newState = (liveNeighbors == 2 || liveNeighbors == 3);
        } else {
            newState = (liveNeighbors == 3);
        }
        if (newState) {
            int index = grid.getIndex(row, col);
            threadLocalNext.set(index);
        }
    }

    private int countLiveNeighbors(Grid grid, int row, int col) {
        int count = 0;
        BitSet current = grid.getCurrent();
        int width = grid.getWidth();

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;

                int neighborRow = row + i;
                int neighborCol = col + j;

                if (neighborRow < 0 || neighborRow >= grid.getHeight() ||
                        neighborCol < 0 || neighborCol >= width) {
                    continue;
                }

                int index = grid.getIndex(neighborRow, neighborCol);
                if (current.get(index)) {
                    count++;
                }
            }
        }
        return count;
    }

    public void cleanup() {
        synchronized (threadPoolLock) {
            isShuttingDown = true;
            if (threadPool != null && !threadPool.isShutdown()) {
                System.out.println("Shutting down thread pool");
                threadPool.shutdown();
            }
        }
    }
}