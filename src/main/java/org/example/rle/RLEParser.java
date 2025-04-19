package org.example.rle;

import org.example.model.Grid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RLEParser {

    // Maximum supported grid dimensions
    private static final int MAX_DIMENSION = 10000;

    public static Grid parse(File rleFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(rleFile))) {
            String line;
            int width = 0, height = 0;

            Pattern dimensionPattern = Pattern.compile("x\\s*=\\s*(\\d+)\\s*,\\s*y\\s*=\\s*(\\d+)");

            // First pass: determine dimensions
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue; // Skip comments
                }

                Matcher matcher = dimensionPattern.matcher(line);
                if (matcher.find()) {
                    width = Integer.parseInt(matcher.group(1));
                    height = Integer.parseInt(matcher.group(2));
                    break;
                }
            }

            if (width == 0 || height == 0) {
                throw new IllegalArgumentException("Could not find valid dimensions in RLE file");
            }

            // Check if dimensions are reasonable
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                System.out.println("WARNING: Pattern dimensions exceed recommended maximum (" +
                        width + "x" + height + "). Limiting to " + MAX_DIMENSION + "x" + MAX_DIMENSION);
                width = Math.min(width, MAX_DIMENSION);
                height = Math.min(height, MAX_DIMENSION);
            }

            // Print pattern dimensions
            System.out.println("Loading pattern with dimensions: " + width + "x" + height);

            // For large patterns, create a compact grid
            // First, we'll collect the RLE data
            StringBuilder rleData = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.contains("rule") || line.contains("=")) {
                    continue;
                }

                rleData.append(line.trim());

                if (line.endsWith("!")) {
                    break;
                }
            }

            // Calculate padding - ensure we don't exceed safe limits
            int paddedWidth = Math.min(Math.max(width * 2, 100), MAX_DIMENSION);
            int paddedHeight = Math.min(Math.max(height * 2, 100), MAX_DIMENSION);

            // Create a new grid with the proper size
            Grid grid = new Grid(paddedWidth, paddedHeight);

            // Calculate offset to center the pattern
            int offsetX = (paddedWidth - width) / 2;
            int offsetY = (paddedHeight - height) / 2;

            // Parse RLE pattern
            String pattern = rleData.toString();
            int row = 0;
            int col = 0;
            int count = 0;
            int i = 0;

            // Main RLE parsing loop - optimized for large patterns
            while (i < pattern.length()) {
                char c = pattern.charAt(i);

                if (Character.isDigit(c)) {
                    count = count * 10 + (c - '0');
                } else if (c == 'b') {
                    // Dead cell - just move the column pointer
                    col += (count == 0) ? 1 : count;
                    count = 0;
                } else if (c == 'o') {
                    // Live cell - set the bits in the grid
                    int cellCount = (count == 0) ? 1 : count;
                    for (int j = 0; j < cellCount && (offsetY + row < paddedHeight) && (offsetX + col + j < paddedWidth); j++) {
                        if (offsetY + row >= 0 && offsetX + col + j >= 0) {
                            try {
                                grid.setCell(offsetY + row, offsetX + col + j, true);
                            } catch (Exception e) {
                                System.err.println("Error setting cell at " + (offsetY + row) + "," + (offsetX + col + j) +
                                        ": " + e.getMessage());
                            }
                        }
                    }
                    col += cellCount;
                    count = 0;
                } else if (c == '$') {
                    // New line
                    int rowCount = (count == 0) ? 1 : count;
                    row += rowCount;
                    col = 0;
                    count = 0;

                    // Stop if we exceed the maximum height
                    if (offsetY + row >= paddedHeight) {
                        System.out.println("WARNING: Pattern exceeds grid height. Truncating.");
                        break;
                    }
                } else if (c == '!') {
                    // End of pattern
                    break;
                }

                i++;
            }

            // Print some statistics
            System.out.println("Loaded pattern with " + grid.getLiveCellCount() + " live cells");
            System.out.println("Grid size: " + paddedWidth + "x" + paddedHeight);
            System.out.println("Memory usage information:");
            Runtime runtime = Runtime.getRuntime();
            long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            System.out.println("Memory in use: " + (memoryUsed / (1024 * 1024)) + " MB");

            return grid;
        } catch (IOException e) {
            throw new RuntimeException("Error reading RLE file: " + e.getMessage(), e);
        }
    }
}