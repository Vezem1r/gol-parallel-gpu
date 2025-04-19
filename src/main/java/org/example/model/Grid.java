package org.example.model;

import org.jocl.*;

import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jocl.CL.*;

public class Grid {
    private final int width;
    private final int height;

    private BitSet current;
    private BitSet next;

    private static ExecutorService threadPool;
    private static int lastThreadCount = -1;
    private static final Object threadPoolLock = new Object();

    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;
    private cl_mem memObjects[];
    private boolean gpuInitialized = false;

    private int[] inputGrid;
    private int[] outputGrid;
    private Pointer srcPointer;
    private Pointer dstPointer;

    private int batchSteps = 0;

    private static long peakMemoryUsage = 0;

    private static final int MAX_SAFE_SIZE = Integer.MAX_VALUE - 10;

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;

        validateGridSize(width, height);

        this.current = new BitSet(width * height);
        this.next = new BitSet(width * height);

        updateMemoryUsage();
    }

    public Grid(boolean[][] initialState) {
        this.height = initialState.length;
        this.width = initialState[0].length;

        validateGridSize(width, height);

        this.current = new BitSet(width * height);
        this.next = new BitSet(width * height);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                if (initialState[i][j]) {
                    setBit(current, i, j, true);
                }
            }
        }

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

    private int getIndex(int row, int col) {
        long index = (long) row * (long) width + (long) col;

        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Index out of range: " + index +
                    " (row=" + row + ", col=" + col + ", width=" + width + ")");
        }

        return (int) index;
    }

    private void setBit(BitSet bitSet, int row, int col, boolean value) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            return;
        }

        bitSet.set(getIndex(row, col), value);
    }

    private boolean getBit(BitSet bitSet, int row, int col) {
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

        System.out.println("Memory usage: " + (usedMemory / (1024 * 1024)) + " MB");
        System.out.println("Peak memory usage: " + (peakMemoryUsage / (1024 * 1024)) + " MB");
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean[][] getCurrentState() {
        boolean[][] result = new boolean[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                result[i][j] = getBit(current, i, j);
            }
        }
        return result;
    }

    public int[][] getLiveCells() {
        int liveCount = current.cardinality();
        int[][] liveCells = new int[liveCount][2];

        int index = 0;
        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;
            liveCells[index][0] = row;
            liveCells[index][1] = col;
            index++;
        }

        return liveCells;
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

    private int countLiveNeighbors(int row, int col) {
        int count = 0;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i == 0 && j == 0) continue;
                if (getCell(row + i, col + j)) count++;
            }
        }
        return count;
    }

    private void applyRules(int row, int col) {
        int liveNeighbors = countLiveNeighbors(row, col);
        boolean isAlive = getCell(row, col);

        if (isAlive && (liveNeighbors < 2 || liveNeighbors > 3)) {
            setBit(next, row, col, false);
        } else if (isAlive && (liveNeighbors == 2 || liveNeighbors == 3)) {
            setBit(next, row, col, true);
        } else if (!isAlive && liveNeighbors == 3) {
            setBit(next, row, col, true);
        } else {
            setBit(next, row, col, isAlive);
        }
    }

    public void stepSequential() {
        next.clear();

        BitSet cellsToCheck = new BitSet(width * height);

        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;

            for (int r = Math.max(0, row - 1); r <= Math.min(height - 1, row + 1); r++) {
                for (int c = Math.max(0, col - 1); c <= Math.min(width - 1, col + 1); c++) {
                    cellsToCheck.set(getIndex(r, c));
                }
            }
        }

        for (int i = cellsToCheck.nextSetBit(0); i >= 0; i = cellsToCheck.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;
            applyRules(row, col);
        }

        BitSet temp = current;
        current = next;
        next = temp;
    }

    public void stepParallel(int threads) {
        next.clear();

        BitSet cellsToCheck = new BitSet(width * height);

        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            int row = i / width;
            int col = i % width;

            for (int r = Math.max(0, row - 1); r <= Math.min(height - 1, row + 1); r++) {
                for (int c = Math.max(0, col - 1); c <= Math.min(width - 1, col + 1); c++) {
                    cellsToCheck.set(getIndex(r, c));
                }
            }
        }

        int maxEffectiveThreads = Math.min(
                Runtime.getRuntime().availableProcessors(),
                Math.max(1, cellsToCheck.cardinality() / 1000)
        );
        threads = Math.min(threads, maxEffectiveThreads);

        synchronized (threadPoolLock) {
            if (threadPool == null || lastThreadCount != threads || threadPool.isShutdown() || threadPool.isTerminated()) {
                if (threadPool != null && !threadPool.isShutdown()) {
                    threadPool.shutdown();
                }

                ThreadFactory threadFactory = new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "GameOfLife-Worker-" + threadNumber.getAndIncrement());
                        t.setPriority(Thread.MAX_PRIORITY);
                        t.setDaemon(true);
                        return t;
                    }
                };

                threadPool = Executors.newFixedThreadPool(threads, threadFactory);
                lastThreadCount = threads;
            }
        }

        final CountDownLatch latch = new CountDownLatch(threads);

        final int[] cellIndices = new int[cellsToCheck.cardinality()];
        int index = 0;
        for (int i = cellsToCheck.nextSetBit(0); i >= 0; i = cellsToCheck.nextSetBit(i + 1)) {
            cellIndices[index++] = i;
        }

        final int cellsPerThread = Math.max(1, cellIndices.length / threads);

        for (int i = 0; i < threads; i++) {
            final int startIndex = i * cellsPerThread;
            final int endIndex = (i == threads - 1) ? cellIndices.length : (i + 1) * cellsPerThread;

            if (startIndex >= cellIndices.length) {
                latch.countDown();
                continue;
            }

            try {
                synchronized (threadPoolLock) {
                    if (!threadPool.isShutdown()) {
                        threadPool.execute(() -> {
                            try {
                                for (int j = startIndex; j < endIndex; j++) {
                                    int idx = cellIndices[j];
                                    int row = idx / width;
                                    int col = idx % width;
                                    applyRules(row, col);
                                }
                            } finally {
                                latch.countDown();
                            }
                        });
                    } else {
                        latch.countDown();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error submitting task to thread pool: " + e.getMessage());
                latch.countDown();
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BitSet temp = current;
        current = next;
        next = temp;
    }

    public void stepGpu() {
        if (!gpuInitialized) {
            initializeGpu();
        }

        if (batchSteps > 0) {
            processBatchSteps();
            return;
        }

        if (inputGrid == null) {
            inputGrid = new int[width * height / 32 + 1];
            outputGrid = new int[width * height / 32 + 1];
            srcPointer = Pointer.to(inputGrid);
            dstPointer = Pointer.to(outputGrid);
        } else {
            java.util.Arrays.fill(outputGrid, 0);
        }

        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            inputGrid[i / 32] |= (1 << (i % 32));
        }

        clEnqueueWriteBuffer(
                commandQueue, memObjects[0], CL_TRUE, 0,
                Sizeof.cl_int * inputGrid.length, srcPointer, 0, null, null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
        clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{width}));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{height}));

        long[] globalWorkSize = new long[]{
                calculateOptimalSize(width),
                calculateOptimalSize(height)
        };

        long[] localWorkSize = new long[]{16, 16};

        clEnqueueNDRangeKernel(
                commandQueue, kernel, 2, null, globalWorkSize, localWorkSize, 0, null, null);

        clEnqueueReadBuffer(
                commandQueue, memObjects[1], CL_TRUE, 0,
                Sizeof.cl_int * outputGrid.length, dstPointer, 0, null, null);

        next.clear();
        for (int i = 0; i < outputGrid.length; i++) {
            int value = outputGrid[i];
            if (value != 0) {
                for (int bit = 0; bit < 32; bit++) {
                    if ((value & (1 << bit)) != 0) {
                        int index = i * 32 + bit;
                        if (index < width * height) {
                            next.set(index);
                        }
                    }
                }
            }
        }

        BitSet temp = current;
        current = next;
        next = temp;
    }

    private int calculateOptimalSize(int size) {
        return ((size + 63) / 64) * 64;
    }

    private void processBatchSteps() {
        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            inputGrid[i / 32] |= (1 << (i % 32));
        }

        clEnqueueWriteBuffer(
                commandQueue, memObjects[0], CL_TRUE, 0,
                Sizeof.cl_int * inputGrid.length, srcPointer, 0, null, null);

        for (int step = 0; step < batchSteps; step++) {
            int srcIdx = step % 2;
            int dstIdx = (step + 1) % 2;

            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memObjects[srcIdx]));
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memObjects[dstIdx]));
            clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{width}));
            clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{height}));

            long[] globalWorkSize = new long[]{
                    calculateOptimalSize(width),
                    calculateOptimalSize(height)
            };
            long[] localWorkSize = new long[]{16, 16};

            clEnqueueNDRangeKernel(
                    commandQueue, kernel, 2, null, globalWorkSize, localWorkSize, 0, null, null);
        }

        int finalBufIdx = batchSteps % 2;
        clEnqueueReadBuffer(
                commandQueue, memObjects[finalBufIdx], CL_TRUE, 0,
                Sizeof.cl_int * outputGrid.length, dstPointer, 0, null, null);

        next.clear();
        for (int i = 0; i < outputGrid.length; i++) {
            int value = outputGrid[i];
            if (value != 0) {
                for (int bit = 0; bit < 32; bit++) {
                    if ((value & (1 << bit)) != 0) {
                        int index = i * 32 + bit;
                        if (index < width * height) {
                            next.set(index);
                        }
                    }
                }
            }
        }

        BitSet temp = current;
        current = next;
        next = temp;
    }

    public void setBatchSteps(int steps) {
        this.batchSteps = steps;
    }

    private int nextPowerOfTwo(int n) {
        int result = 1;
        while (result < n) {
            result <<= 1;
        }
        return result;
    }

    private void initializeGpu() {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_GPU;
        final int deviceIndex = 0;

        CL.setExceptionsEnabled(true);
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        byte[] nameBytes = new byte[256];
        clGetDeviceInfo(device, CL_DEVICE_NAME, nameBytes.length, Pointer.to(nameBytes), null);
        String deviceName = new String(nameBytes, 0, nameBytes.length).trim();
        System.out.println("Using GPU device: " + deviceName);

        context = clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
        commandQueue = clCreateCommandQueue(context, device, CL_QUEUE_PROFILING_ENABLE, null);

        memObjects = new cl_mem[2];
        memObjects[0] = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_int * (width * height / 32 + 1), null, null);
        memObjects[1] = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_int * (width * height / 32 + 1), null, null);

        String programSource =
                "__kernel void gameOfLife(__global const int *input, __global int *output, const int width, const int height) {\n" +
                        "    int x = get_global_id(0);\n" +
                        "    int y = get_global_id(1);\n" +
                        "    \n" +
                        "    if (x >= width || y >= height) return;\n" +
                        "    \n" +
                        "    int index = y * width + x;\n" +
                        "    int blockIdx = index / 32;\n" +
                        "    int bitPos = index % 32;\n" +
                        "    \n" +
                        "    int cell_state = (input[blockIdx] >> bitPos) & 1;\n" +
                        "    \n" +
                        "    int liveCount = 0;\n" +
                        "    for (int dy = -1; dy <= 1; dy++) {\n" +
                        "        for (int dx = -1; dx <= 1; dx++) {\n" +
                        "            if (dx == 0 && dy == 0) continue;\n" +
                        "            \n" +
                        "            int nx = x + dx;\n" +
                        "            int ny = y + dy;\n" +
                        "            \n" +
                        "            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {\n" +
                        "                continue;\n" +
                        "            }\n" +
                        "            \n" +
                        "            int nIndex = ny * width + nx;\n" +
                        "            int nBlockIdx = nIndex / 32;\n" +
                        "            int nBitPos = nIndex % 32;\n" +
                        "            int neighborState = (input[nBlockIdx] >> nBitPos) & 1;\n" +
                        "            liveCount += neighborState;\n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    int newState = 0;\n" +
                        "    if (cell_state == 1) {\n" +
                        "        if (liveCount == 2 || liveCount == 3) {\n" +
                        "            newState = 1;\n" +
                        "        }\n" +
                        "    } else {\n" +
                        "        if (liveCount == 3) {\n" +
                        "            newState = 1;\n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    if (newState == 1) {\n" +
                        "        atomic_or(&output[blockIdx], 1 << bitPos);\n" +
                        "    }\n" +
                        "}";

        program = clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);

        try {
            clBuildProgram(program, 0, null, "-cl-mad-enable -cl-fast-relaxed-math", null, null);
        } catch (CLException e) {
            long[] logSize = new long[1];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize);
            byte[] log = new byte[(int)logSize[0]];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, log.length, Pointer.to(log), null);
            System.err.println("OpenCL Build Log:\n" + new String(log));
            throw e;
        }

        kernel = clCreateKernel(program, "gameOfLife", null);

        inputGrid = new int[width * height / 32 + 1];
        outputGrid = new int[width * height / 32 + 1];
        srcPointer = Pointer.to(inputGrid);
        dstPointer = Pointer.to(outputGrid);

        gpuInitialized = true;
    }

    public void cleanup() {
        if (gpuInitialized) {
            clReleaseMemObject(memObjects[0]);
            clReleaseMemObject(memObjects[1]);
            clReleaseKernel(kernel);
            clReleaseProgram(program);
            clReleaseCommandQueue(commandQueue);
            clReleaseContext(context);
            gpuInitialized = false;
        }

        synchronized (threadPoolLock) {
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
                threadPool = null;
            }
        }

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