package org.example.utils;

import org.example.model.Grid;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class StatisticsLogger {
    private static final String LOG_FILE = "performance_stats.csv";
    private static boolean headerWritten = false;

    public static void log(String method, long time , Grid grid, int threads, int steps) {
        System.out.println("Method: " + method);
        System.out.println("Execution time: " + time + " ms");

        String baseMethod = method.contains("-") ? method.substring(0, method.indexOf("-")) : method;
        String patternName = method.contains("-") ? method.substring(method.indexOf("-") + 1) : "unknown";

        if (baseMethod.equals("parallel")) {
            System.out.println("Threads: " + threads);
        }

        int liveCellCount = grid.getLiveCellCount();
        int totalCells = grid.getWidth() * grid.getHeight();
        int deadCellCount = totalCells - liveCellCount;
        double liveCellPercentage = (double) liveCellCount / totalCells * 100;

        logToCsv(patternName, baseMethod, time, liveCellCount, deadCellCount, liveCellPercentage, threads, steps);
    }

    private static void logToCsv(String patternName, String baseMethod, long time,
                                 int liveCells, int deadCells, double livePercentage, int threads, int steps) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            if (!headerWritten) {
                writer.println("Method, PatternName, ExecutionTime(ms), LiveCells, DeadCells, LiveCellPercentage, Threads, Steps");
                headerWritten = true;
            }
            writer.println(baseMethod + ", " + patternName + ", " + time + ", " +
                    liveCells + ", " + deadCells + ", " +
                    String.format("%.2f", livePercentage) + ", " +
                    (threads == -1 ? "" : threads) + ", " +
                    (steps == -1 ? "" : steps));
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public static void logSystemInfo() {
        System.out.println("\nSystem Information:");
        System.out.println("------------------");
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        System.out.println("------------------");
    }
}