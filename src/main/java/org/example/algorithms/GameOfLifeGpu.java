package org.example.algorithms;

import org.example.model.Grid;

public class GameOfLifeGpu {

    public void simulate(Grid grid, int steps) {
        try {
            for (int i = 0; i < steps; i++) {
                grid.stepGpu();
            }
        } finally {
            grid.cleanup();
        }
    }
}
