package org.example.rle;

import org.example.model.Grid;

import java.util.ArrayList;
import java.util.List;

public class PatternDetector {

    private static final boolean[][] BLOCK_PATTERN = {
            {true, true},
            {true, true}
    };

    private static final boolean[][] BLINKER_PATTERN_1 = {
            {true, true, true}
    };

    private static final boolean[][] BLINKER_PATTERN_2 = {
            {true},
            {true},
            {true}
    };

    private static final boolean[][] GLIDER_PATTERN_1 = {
            {false, true, false},
            {false, false, true},
            {true, true, true}
    };

    private static final boolean[][] GLIDER_PATTERN_2 = {
            {true, false, true},
            {false, true, true},
            {false, true, false}
    };

    private static final boolean[][] GLIDER_PATTERN_3 = {
            {false, false, true},
            {true, false, true},
            {false, true, true}
    };

    private static final boolean[][] GLIDER_PATTERN_4 = {
            {true, false, false},
            {false, true, true},
            {true, true, false}
    };

    private static final boolean[][] BEEHIVE_PATTERN = {
            {false, true, true, false},
            {true, false, false, true},
            {false, true, true, false}
    };

    private static final boolean[][] LOAF_PATTERN = {
            {false, true, true, false},
            {true, false, false, true},
            {false, true, false, true},
            {false, false, true, false}
    };

    public static List<String> detectPatterns(Grid grid) {
        List<String> detectedPatterns = new ArrayList<>();
        boolean[][] currentState = grid.getCurrentState();
        int height = grid.getHeight();
        int width = grid.getWidth();

        int blockCount = 0;
        int blinkerCount = 0;
        int gliderCount = 0;
        int beehiveCount = 0;
        int loafCount = 0;

        for (int i = 0; i < height - 2; i++) {
            for (int j = 0; j < width - 2; j++) {
                if (j < width - 1 && i < height - 1 && matchesPattern(currentState, i, j, BLOCK_PATTERN)) {
                    blockCount++;
                }

                if (j < width - 2 && matchesPattern(currentState, i, j, BLINKER_PATTERN_1)) {
                    blinkerCount++;
                }
                if (i < height - 2 && matchesPattern(currentState, i, j, BLINKER_PATTERN_2)) {
                    blinkerCount++;
                }

                if (j < width - 2 && i < height - 2) {
                    if (matchesPattern(currentState, i, j, GLIDER_PATTERN_1) ||
                            matchesPattern(currentState, i, j, GLIDER_PATTERN_2) ||
                            matchesPattern(currentState, i, j, GLIDER_PATTERN_3) ||
                            matchesPattern(currentState, i, j, GLIDER_PATTERN_4)) {
                        gliderCount++;
                    }
                }

                if (j < width - 3 && i < height - 2 && matchesPattern(currentState, i, j, BEEHIVE_PATTERN)) {
                    beehiveCount++;
                }

                if (j < width - 3 && i < height - 3 && matchesPattern(currentState, i, j, LOAF_PATTERN)) {
                    loafCount++;
                }
            }
        }

        if (blockCount > 0) {
            detectedPatterns.add("Block: " + blockCount);
        }
        if (blinkerCount > 0) {
            detectedPatterns.add("Blinker: " + blinkerCount);
        }
        if (gliderCount > 0) {
            detectedPatterns.add("Glider: " + gliderCount);
        }
        if (beehiveCount > 0) {
            detectedPatterns.add("Beehive: " + beehiveCount);
        }
        if (loafCount > 0) {
            detectedPatterns.add("Loaf: " + loafCount);
        }

        return detectedPatterns;
    }

    private static boolean matchesPattern(boolean[][] grid, int startRow, int startCol, boolean[][] pattern) {
        int patternHeight = pattern.length;
        int patternWidth = pattern[0].length;

        for (int i = 0; i < patternHeight; i++) {
            for (int j = 0; j < patternWidth; j++) {
                if (grid[startRow + i][startCol + j] != pattern[i][j]) {
                    return false;
                }
            }
        }

        return true;
    }
}
