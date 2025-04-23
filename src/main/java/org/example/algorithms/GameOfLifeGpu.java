package org.example.algorithms;

import org.example.model.Grid;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_context;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_command_queue;
import org.jocl.CLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.BitSet;
import java.util.stream.Collectors;

import static org.jocl.CL.*;

public class GameOfLifeGpu {
    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel gameOfLifeStepKernel;
    private cl_kernel clearGridKernel;
    private cl_mem[] memObjects;
    private boolean gpuInitialized = false;

    private int[] inputGrid;
    private int[] outputGrid;
    private Pointer srcPointer;
    private Pointer dstPointer;

    private static final int BATCH_SIZE = 100;

    public void simulate(Grid grid, int steps) {
        if (!gpuInitialized) {
            initializeGpu(grid);
        }

        prepareInputData(grid);

        int currentBuffer = 0;
        int remainingSteps = steps;

        while (remainingSteps > 0) {
            int batchSteps = Math.min(remainingSteps, BATCH_SIZE);

            for (int i = 0; i < batchSteps; i++) {
                int inputIdx = currentBuffer;
                int outputIdx = 1 - currentBuffer;

                clearBuffer(memObjects[outputIdx], inputGrid.length);
                runSingleStepOnGpu(grid, memObjects[inputIdx], memObjects[outputIdx]);
                currentBuffer = outputIdx;
            }

            remainingSteps -= batchSteps;
        }
        downloadFinalResult(currentBuffer);
        updateGridFromGpuResult(grid);
    }

    private String loadKernelFromResource() {
        try {
            URL resource = getClass().getClassLoader().getResource("kernels/gameOfLife.cl");
            if (resource == null) {
                throw new IOException("Kernel resource not found: kernels/gameOfLife.cl");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load kernel code: " + e.getMessage(), e);
        }
    }

    private void initializeGpu(Grid grid) {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_GPU;
        final int deviceIndex = 0;
        final int width = grid.getWidth();
        final int height = grid.getHeight();
        final int bufferSize = width * height / 32 + 1;

        CL.setExceptionsEnabled(true);
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        byte[] nameBytes = new byte[256];
        clGetDeviceInfo(device, CL_DEVICE_NAME, nameBytes.length, Pointer.to(nameBytes), null);
        String deviceName = new String(nameBytes).trim();
        System.out.println("Using GPU device: " + deviceName);

        context = clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
        commandQueue = clCreateCommandQueue(context, device, CL_QUEUE_PROFILING_ENABLE, null);

        memObjects = new cl_mem[2];
        memObjects[0] = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_int * bufferSize, null, null);
        memObjects[1] = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_int * bufferSize, null, null);

        String kernelSource = loadKernelFromResource();
        program = clCreateProgramWithSource(context, 1, new String[]{kernelSource}, null, null);

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

        gameOfLifeStepKernel = clCreateKernel(program, "gameOfLifeStep", null);
        clearGridKernel = clCreateKernel(program, "clearGrid", null);

        inputGrid = new int[bufferSize];
        outputGrid = new int[bufferSize];
        srcPointer = Pointer.to(inputGrid);
        dstPointer = Pointer.to(outputGrid);

        gpuInitialized = true;
    }

    private void prepareInputData(Grid grid) {
        BitSet current = grid.getCurrent();
        java.util.Arrays.fill(inputGrid, 0);

        for (int i = current.nextSetBit(0); i >= 0; i = current.nextSetBit(i + 1)) {
            inputGrid[i / 32] |= (1 << (i % 32));
        }
        clEnqueueWriteBuffer(
                commandQueue, memObjects[0], CL_TRUE, 0,
                (long) Sizeof.cl_int * inputGrid.length, srcPointer, 0, null, null);

        clearBuffer(memObjects[1], inputGrid.length);
    }

    private void clearBuffer(cl_mem buffer, int size) {
        clSetKernelArg(clearGridKernel, 0, Sizeof.cl_mem, Pointer.to(buffer));
        clSetKernelArg(clearGridKernel, 1, Sizeof.cl_int, Pointer.to(new int[]{size}));

        long[] globalWorkSize = new long[]{calculateOptimalSize(size)};
        long[] localWorkSize = new long[]{256};

        clEnqueueNDRangeKernel(
                commandQueue, clearGridKernel, 1, null, globalWorkSize, localWorkSize, 0, null, null);

        clFinish(commandQueue);
    }

    private void runSingleStepOnGpu(Grid grid, cl_mem inputBuffer, cl_mem outputBuffer) {
        int width = grid.getWidth();
        int height = grid.getHeight();

        clSetKernelArg(gameOfLifeStepKernel, 0, Sizeof.cl_mem, Pointer.to(inputBuffer));
        clSetKernelArg(gameOfLifeStepKernel, 1, Sizeof.cl_mem, Pointer.to(outputBuffer));
        clSetKernelArg(gameOfLifeStepKernel, 2, Sizeof.cl_int, Pointer.to(new int[]{width}));
        clSetKernelArg(gameOfLifeStepKernel, 3, Sizeof.cl_int, Pointer.to(new int[]{height}));

        long[] globalWorkSize = new long[]{
                calculateOptimalSize(width),
                calculateOptimalSize(height)
        };
        long[] localWorkSize = new long[]{16, 16};

        clEnqueueNDRangeKernel(
                commandQueue, gameOfLifeStepKernel, 2, null, globalWorkSize, localWorkSize, 0, null, null);

        clFinish(commandQueue);
    }

    private void downloadFinalResult(int bufferIndex) {
        clEnqueueReadBuffer(
                commandQueue, memObjects[bufferIndex], CL_TRUE, 0,
                (long) Sizeof.cl_int * outputGrid.length, dstPointer, 0, null, null);
    }

    private void updateGridFromGpuResult(Grid grid) {
        BitSet next = grid.getNext();
        int width = grid.getWidth();
        int height = grid.getHeight();

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

        grid.swapGrids();
    }

    private int calculateOptimalSize(int size) {
        return ((size + 255) / 256) * 256;
    }

    public void cleanup() {
        if (gpuInitialized) {
            clReleaseMemObject(memObjects[0]);
            clReleaseMemObject(memObjects[1]);
            clReleaseKernel(gameOfLifeStepKernel);
            clReleaseKernel(clearGridKernel);
            clReleaseProgram(program);
            clReleaseCommandQueue(commandQueue);
            clReleaseContext(context);
            gpuInitialized = false;
        }
    }
}