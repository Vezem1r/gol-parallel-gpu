int getCellState(__global int *grid, int x, int y, int width, int height) {
    if (x < 0 || x >= width || y < 0 || y >= height) return 0;
    int index = y * width + x;
    int blockIdx = index / 32;
    int bitPos = index % 32;
    return (grid[blockIdx] >> bitPos) & 1;
}

__kernel void clearGrid(__global int *grid, const int gridSize) {
    int idx = get_global_id(0);
    if (idx < gridSize) {
        grid[idx] = 0;
    }
}

__kernel void gameOfLifeStep(
    __global int *input,
    __global int *output,
    const int width,
    const int height
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) return;

    int cellState = getCellState(input, x, y, width, height);

    int liveCount = 0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) continue;
            liveCount += getCellState(input, x+dx, y+dy, width, height);
        }
    }

    int newState = 0;
    if (cellState == 1) {
        newState = (liveCount == 2 || liveCount == 3) ? 1 : 0;
    } else {
        newState = (liveCount == 3) ? 1 : 0;
    }

    int index = y * width + x;
    int blockIdx = index / 32;
    int bitPos = index % 32;

    if (newState) {
        atomic_or(&output[blockIdx], 1 << bitPos);
    }
}