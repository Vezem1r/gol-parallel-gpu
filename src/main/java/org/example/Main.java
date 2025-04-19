package org.example;

import org.example.algorithms.GameOfLifeGpu;
import org.example.algorithms.GameOfLifeParallel;
import org.example.algorithms.GameOfLifeSequential;
import org.example.gui.GameOfLifeGUI;
import org.example.model.Grid;
import org.example.rle.PatternDetector;
import org.example.rle.RLEParser;
import org.example.utils.StatisticsLogger;
import org.example.utils.Timer;

import javax.swing.*;
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
            System.out.println("  -c: run comparison of all modes");
            System.out.println("  -gui: run with graphical user interface");
            return;
        }

        String mode = args[0].toLowerCase();
        int steps = Integer.parseInt(args[1]);
        String patternFile = args[2];

        int threads = Runtime.getRuntime().availableProcessors();
        boolean compare = false;
        boolean useGUI = false;

        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("-t") && i + 1 < args.length) {
                threads = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("-c")) {
                compare = true;
            } else if (args[i].equals("-gui")) {
                useGUI = true;
            }
        }

        StatisticsLogger.logSystemInfo();

        if (compare) {
            runComparison(patternFile, steps, threads);
        } else {
            runSimulation(mode, patternFile, steps, threads, useGUI);
        }
    }

    private static void runSimulation(String mode, String patternFile, int steps, int threads, boolean useGUI) {
        System.out.println("\nLoading pattern from: " + patternFile);
        final Grid grid = RLEParser.parse(new File(patternFile));

        System.out.println("Grid size: " + grid.getWidth() + "x" + grid.getHeight());
        System.out.println("Running " + steps + " iterations in " + mode + " mode");

        if (mode.equals("parallel")) {
            System.out.println("Using " + threads + " threads");
        }

        if (useGUI) {
            // Launch GUI version
            SwingUtilities.invokeLater(() -> {
                GameOfLifeGUI gui = GameOfLifeGUI.createAndShow(grid, mode, threads);
                System.out.println("GUI launched. Close the window to exit the application.");
            });
        } else {
            // Run in non-GUI mode without visualization
            long time;
            double speedup = 0;

            switch (mode) {
                case "sequential":
                    time = Timer.measure(() -> new GameOfLifeSequential().simulate(grid, steps));
                    break;
                case "parallel":
                    time = Timer.measure(() -> new GameOfLifeParallel().simulate(grid, steps, threads));
                    break;
                case "gpu":
                    time = Timer.measure(() -> new GameOfLifeGpu().simulate(grid, steps));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown mode: " + mode);
            }

            StatisticsLogger.log(mode, time, speedup);

            System.out.println("\nDetected patterns:");
            PatternDetector.detectPatterns(grid).forEach(System.out::println);
        }
    }

    private static void runComparison(String patternFile, int steps, int threads) {
        Grid gridSeq = RLEParser.parse(new File(patternFile));
        System.out.println("\nRunning sequential mode...");
        long timeSeq = Timer.measure(() -> new GameOfLifeSequential().simulate(gridSeq, steps));
        StatisticsLogger.log("sequential", timeSeq, 0);

        System.out.println("\nRunning parallel mode with varying thread counts...");
        for (int t = 1; t <= threads; t *= 2) {
            Grid gridPar = RLEParser.parse(new File(patternFile));
            int finalT = t;
            long timePar = Timer.measure(() -> new GameOfLifeParallel().simulate(gridPar, steps, finalT));
            double speedup = (double) timeSeq / timePar;
            StatisticsLogger.log("parallel-" + t, timePar, speedup);
            System.out.println("Efficiency: " +
                    String.format("%.2f", StatisticsLogger.calculateEfficiency(speedup, t)) + "%");
        }

        try {
            Grid gridGpu = RLEParser.parse(new File(patternFile));
            System.out.println("\nRunning GPU mode...");
            long timeGpu = Timer.measure(() -> new GameOfLifeGpu().simulate(gridGpu, steps));
            double speedup = (double) timeSeq / timeGpu;
            StatisticsLogger.log("gpu", timeGpu, speedup);
        } catch (Exception e) {
            System.err.println("GPU simulation failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\nComparison complete. Results written to performance_stats.csv");
    }
}