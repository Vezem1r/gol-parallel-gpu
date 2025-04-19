package org.example.algorithms;

import org.example.model.Grid;

public class GameOfLifeParallel {

    public void simulate(Grid grid, int steps, int threads) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        int actualThreads = Math.min(threads, maxThreads);

        if (actualThreads != threads) {
            System.out.println("Warning: Requested " + threads + " threads, but only using "
                    + actualThreads + " (system maximum)");
        }

        for (int i = 0; i < steps; i++) {
            grid.stepParallel(actualThreads);
        }
    }
}
