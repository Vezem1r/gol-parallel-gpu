package org.example.model;

import org.example.algorithms.GameOfLifeGpu;
import org.example.algorithms.GameOfLifeParallel;

import java.util.BitSet;

public class Grid {
    private final int width;
    private final int height;

    private BitSet current;
    private BitSet next;

    private final GameOfLifeParallel parallelAlgorithm;
    private final GameOfLifeGpu gpuAlgorithm;

    private static long peakMemoryUsage = 0;
    private static final int MAX_SAFE_SIZE = Integer.MAX_VALUE - 10;

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;

        validateGridSize(width, height);

        this.current = new BitSet(width * height);
        this.next = new BitSet(width * height);

        this.parallelAlgorithm = new GameOfLifeParallel();
        this.gpuAlgorithm = new GameOfLifeGpu();

        updateMemoryUsage();
    }

    private void validateGridSize(int width, int height) {
        long size = (long) width * (long) height;
        if (size > MAX_SAFE_SIZE) {
            throw new IllegalArgumentException(
                    "Grid size (" + width + "x" + height + ") is too large. " +
                            "Maximum supported size is " + (MAX_SAFE_SIZE / 1000000) + " million cells."
            );
        }
    }

    public int getIndex(int row, int col) {
        long index = (long) row * (long) width + (long) col;

        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Index out of range: " + index +
                    " (row=" + row + ", col=" + col + ", width=" + width + ")");
        }

        return (int) index;
    }

    public void setBit(BitSet bitSet, int row, int col, boolean value) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            return;
        }

        bitSet.set(getIndex(row, col), value);
    }

    public boolean getBit(BitSet bitSet, int row, int col) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            return false;
        }

        return bitSet.get(getIndex(row, col));
    }

    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLiveCellCount() {
        return current.cardinality();
    }

    public void setCell(int row, int col, boolean value) {
        if (row >= 0 && row < height && col >= 0 && col < width) {
            setBit(current, row, col, value);
        }
    }

    public boolean getCell(int row, int col) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            return false;
        }
        return getBit(current, row, col);
    }

    public BitSet getCurrent() {
        return current;
    }

    public BitSet getNext() {
        return next;
    }

    public void swapGrids() {
        BitSet temp = current;
        current = next;
        next = temp;
    }

    public void cleanup() {
        gpuAlgorithm.cleanup();
        parallelAlgorithm.cleanup();
        updateMemoryUsage();
    }

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                sb.append(getBit(current, i, j) ? "█" : " ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}