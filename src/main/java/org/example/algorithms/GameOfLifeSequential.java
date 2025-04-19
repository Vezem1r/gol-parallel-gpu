package org.example.algorithms;

import org.example.model.Grid;

public class GameOfLifeSequential {

    public void simulate(Grid grid, int steps) {
        for (int i = 0; i < steps; i++) {
            grid.stepSequential();
        }
    }
}
