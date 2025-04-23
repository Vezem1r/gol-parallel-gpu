package org.example.rle;

import org.example.model.Grid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RLEParser {

    private static final int MAX_DIMENSION = 10000;

    public static Grid parse(File rleFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(rleFile))) {
            String line;
            int width = 0, height = 0;

            Pattern dimensionPattern = Pattern.compile("x\\s*=\\s*(\\d+)\\s*,\\s*y\\s*=\\s*(\\d+)");

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
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

            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                System.out.println("WARNING: Pattern dimensions exceed recommended maximum (" +
                        width + "x" + height + "). Limiting to " + MAX_DIMENSION + "x" + MAX_DIMENSION);
                width = Math.min(width, MAX_DIMENSION);
                height = Math.min(height, MAX_DIMENSION);
            }

            System.out.println("Loading pattern with dimensions: " + width + "x" + height);

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

            int paddedWidth = Math.min(Math.max(width * 2, 100), MAX_DIMENSION);
            int paddedHeight = Math.min(Math.max(height * 2, 100), MAX_DIMENSION);

            Grid grid = new Grid(paddedWidth, paddedHeight);

            int offsetX = (paddedWidth - width) / 2;
            int offsetY = (paddedHeight - height) / 2;

            String pattern = rleData.toString();
            int row = 0;
            int col = 0;
            int count = 0;
            int i = 0;

            while (i < pattern.length()) {
                char c = pattern.charAt(i);

                if (Character.isDigit(c)) {
                    count = count * 10 + (c - '0');
                } else if (c == 'b') {
                    col += (count == 0) ? 1 : count;
                    count = 0;
                } else if (c == 'o') {
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
                    int rowCount = (count == 0) ? 1 : count;
                    row += rowCount;
                    col = 0;
                    count = 0;

                    if (offsetY + row >= paddedHeight) {
                        System.out.println("WARNING: Pattern exceeds grid height. Truncating.");
                        break;
                    }
                } else if (c == '!') {
                    break;
                }

                i++;
            }

            System.out.println("Loaded pattern with " + grid.getLiveCellCount() + " live cells");

            return grid;
        } catch (IOException e) {
            throw new RuntimeException("Error reading RLE file: " + e.getMessage(), e);
        }
    }
}