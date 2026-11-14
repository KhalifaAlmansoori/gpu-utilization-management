# GPU Utilization Management (Java Swing)

COSC230 Object-Oriented Programming, Fall 2026, Khalifa University

A desktop Java Swing application that shares four simulated NVIDIA H100 GPUs among students.
Students register and submit jobs; the administrator approves accounts, allocates GPU minutes,
monitors a dashboard and advances a shared simulated clock one minute at a time.
All GPU execution is simulated.

## Team

| Name | University ID | GitHub username | Main responsibility |
|------|---------------|-----------------|---------------------|
|Khalifa Almansoori|100061087|KhalifaAlmansoori|                     |
|      |               |                 |                     |
|      |               |                 |                     |

## Requirements

- Java JDK 17 or newer (no external libraries)
- Any IDE (IntelliJ IDEA, Eclipse, NetBeans, VS Code) or the command line

## How to run

### From the command line

From the repository root:

```bash
# compile
javac -d out $(find src -name "*.java")        # macOS / Linux
# Windows (PowerShell):
# javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName

# run
java -cp out gpu.Main
```

### From an IDE

1. Open the repository folder as a project.
2. Mark `src` as the source root.
3. Run `gpu.Main`.

Run the program from the repository root so it finds the `data/` folder.

## Default accounts

| Role          | Username        | Password                  |
|---------------|-----------------|---------------------------|
| Administrator | `admin`         | `gpu2026`                 |
| Student       | university ID   | password chosen at signup |

## Loading the sample test data

The `test-data/` folder holds a prepared demo state (5 approved students, pending and rejected
requests, about 20 jobs in every state). To start from it, copy its files into `data/` before
running:

```bash
cp test-data/* data/      # macOS / Linux
# copy test-data\* data\  # Windows
```

To start from an empty system, empty the `data/` folder. On first run the administrator is asked
to set the simulation clock.

## Project structure

```
src/gpu/
  Main.java      entry point
  model/         domain classes: User, Student, Administrator, Job, GPU, TimeAllowance, ...
  logic/         rules: Scheduler, SimulationClock, account handling (no javax.swing here)
  storage/       reading and writing the data files
  ui/            Swing windows and panels (no business rules here)
data/            files written by the running program
test-data/       sample data for testing and the demonstration
docs/            report, class diagram, screenshots
```

## Team workflow

See [CONTRIBUTING.md](CONTRIBUTING.md).
