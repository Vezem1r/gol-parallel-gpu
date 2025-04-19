package org.example.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StatisticsLogger {
    private static final Map<String, Long> baselineTimes = new HashMap<>();
    private static final String LOG_FILE = "performance_stats.csv";
    private static boolean headerWritten = false;


    public static void log(String method, long time, double speedup) {
        System.out.println("Method: " + method);
        System.out.println("Execution time: " + time + " ms");

        if (speedup == 0 && method.equals("sequential")) {
            baselineTimes.put("sequential", time);
            speedup = 1.0;
        } else if (speedup == 0) {
            Long baselineTime = baselineTimes.get("sequential");
            if (baselineTime != null && baselineTime > 0) {
                speedup = (double) baselineTime / time;
            }
        }

        if (speedup != 0) {
            System.out.println("Speedup: " + String.format("%.2f", speedup) + "x");
        }

        logToCsv(method, time, speedup);
    }

    private static void logToCsv(String method, long time, double speedup) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            if (!headerWritten) {
                writer.println("Date,Time,Method,ExecutionTime(ms),Speedup");
                headerWritten = true;
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
            String dateTime = dateFormat.format(new Date());

            writer.println(dateTime + "," + method + "," + time + "," + String.format("%.2f", speedup));

        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public static double calculateEfficiency(double speedup, int units) {
        return (speedup / units) * 100.0;
    }

    public static void logSystemInfo() {
        System.out.println("\nSystem Information:");
        System.out.println("------------------");
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("------------------");
    }
}
