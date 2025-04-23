# Game of Life - Parallel & GPU Implementation

Implementation of Conway’s Game of Life in Java, featuring:

- ✅ Sequential CPU version
- ⚡ Multithreaded (parallel) CPU version
- 🚀 GPU-accelerated version using OpenCL

## 📦 Build Instructions

### Prerequisites
- Java 11

### Build the Project

```bash
mvn clean package
```

This will generate a runnable JAR with all dependencies included at:

```
target/game-of-life-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Usage

```bash
java -jar target/game-of-life-1.0-SNAPSHOT-jar-with-dependencies.jar <mode> <steps> <pattern.rle> [options]
```

### Arguments

- `<mode>`:  
  One of:
    - `sequential` – single-threaded CPU version
    - `parallel` – multithreaded CPU version
    - `gpu` – GPU-accelerated version via OpenCL

- `<steps>`:  
  Number of simulation steps to perform (e.g., `10000`)

- `<pattern.rle>`:  
  Path to `.rle` file (pattern description in Run-Length Encoded format)

### Options

- `-t <threads>`:  
  (Only for `parallel` mode) Number of CPU threads to use.  
  Defaults to `Runtime.getRuntime().availableProcessors()`.

### Example

```bash
java -jar target/game-of-life-1.0-SNAPSHOT-jar-with-dependencies.jar parallel 10000 src/main/resources/patterns/caterpillar.rle -t 16
```

---

## 📊 Performance Statistics

After each run, the program logs performance information into:

```
performance_stats.csv
```

This file is created in the **project root directory** (if it doesn’t already exist).  
It includes the following columns:

| Method    | PatternName           | ExecutionTime(ms) | LiveCells | DeadCells | LiveCellPercentage | Threads | Steps   |
|-----------|-----------------------|-------------------|-----------|-----------|--------------------|---------|---------|
| sequential| pp8primecalculator.rle | 1188135           | 10792     | 2515592   | 0.43               | 0       | 1000000 |
| parallel  | pp8primecalculator.rle | 756644            | 10792     | 2515592   | 0.43               | 4       | 1000000 |
| gpu       | pp8primecalculator.rle | 158773            | 10792     | 2515592   | 0.43               | 0       | 1000000 |

---

## 📁 Patterns

You can find RLE patterns in:

```
src/main/resources/patterns/
```

