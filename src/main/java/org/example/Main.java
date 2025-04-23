package org.example;

import org.example.algorithms.GameOfLifeGpu;
import org.example.algorithms.GameOfLifeParallel;
import org.example.algorithms.GameOfLifeSequential;
import org.example.model.Grid;
import org.example.rle.RLEParser;
import org.example.utils.StatisticsLogger;
import org.example.utils.Timer;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar game-of-life.jar <mode> <steps> <pattern>");
            System.out.println("  mode: sequential | parallel | gpu");
            System.out.println("  steps: number of iterations");
            System.out.println("  pattern: path to RLE file");
            System.out.println("\nOptional arguments:");
            System.out.println("  -t <threads>: number of threads for parallel mode (default: available processors)");
            return;
        }

        String mode = args[0].toLowerCase();
        int steps = Integer.parseInt(args[1]);
        String patternFile = args[2];

        int threads = Runtime.getRuntime().availableProcessors();

        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("-t") && i + 1 < args.length) {
                threads = Integer.parseInt(args[i + 1]);
                i++;
            }
        }

        StatisticsLogger.logSystemInfo();
        runSimulation(mode, patternFile, steps, threads);
    }

    private static void runSimulation(String mode, String patternFile, int steps, int threads) {
        File file = new File(patternFile);
        String patternName = file.getName();
        System.out.println("Pattern name: " + patternName);

        final Grid grid = RLEParser.parse(file);

        if (mode.equals("parallel")) {
            System.out.println("Using " + threads + " threads");
        }

        long time;

        switch (mode) {
            case "sequential":
                time = Timer.measure(() -> new GameOfLifeSequential().simulate(grid, steps));
                StatisticsLogger.log(mode + "-" + patternName, time, grid, 0, steps);
                break;
            case "parallel":
                time = Timer.measure(() -> new GameOfLifeParallel().simulate(grid, steps, threads));
                StatisticsLogger.log(mode + "-" + patternName, time, grid, threads, steps);
                break;
            case "gpu":
                time = Timer.measure(() -> new GameOfLifeGpu().simulate(grid, steps));
                StatisticsLogger.log(mode + "-" + patternName, time, grid, 0, steps);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }
}