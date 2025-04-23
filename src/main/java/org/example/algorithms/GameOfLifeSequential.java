package org.example.algorithms;

import org.example.model.Grid;

import java.util.BitSet;

public class GameOfLifeSequential {

    public void simulate(Grid grid, int steps) {
        for (int i = 0; i < steps; i++) {
            step(grid);
        }
    }

    public void step(Grid grid) {
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

        for (int i = cellsToCheck.nextSetBit(0); i >= 0; i = cellsToCheck.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;
            applyRules(grid, row, col);
        }

        grid.swapGrids();
    }

    private void applyRules(Grid grid, int row, int col) {
        int liveNeighbors = countLiveNeighbors(grid, row, col);
        boolean isAlive = grid.getCell(row, col);
        BitSet next = grid.getNext();

        if (isAlive && (liveNeighbors < 2 || liveNeighbors > 3)) {
            grid.setBit(next, row, col, false);
        } else if (isAlive && (liveNeighbors == 2 || liveNeighbors == 3)) {
            grid.setBit(next, row, col, true);
        } else if (!isAlive && liveNeighbors == 3) {
            grid.setBit(next, row, col, true);
        } else {
            grid.setBit(next, row, col, isAlive);
        }
    }

    private int countLiveNeighbors(Grid grid, int row, int col) {
        int count = 0;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i == 0 && j == 0) continue;
                if (grid.getCell(row + i, col + j)) count++;
            }
        }
        return count;
    }
}