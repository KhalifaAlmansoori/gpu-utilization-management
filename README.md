# GPU Utilization Management (Java Swing)

COSC230 Object-Oriented Programming, Fall 2026, Khalifa University

A desktop Java Swing application that shares four simulated NVIDIA H100 GPUs among students.
Students register and submit jobs; the administrator approves accounts, allocates GPU minutes,
monitors a dashboard and advances a shared simulated clock one minute at a time.
All GPU execution is simulated.

Shared repository: [KhalifaAlmansoori/gpu-utilization-management](https://github.com/KhalifaAlmansoori/gpu-utilization-management).

## Team

| Name | University ID | GitHub username | Main responsibility |
|------|---------------|-----------------|---------------------|
|Khalifa Almansoori|100061087|KhalifaAlmansoori|Model Classes|
|Farouk Zahran|100065717|Fero-Zahran|GUI and Storage|
|Abdulkarim Alawadhi|100066139|Abdulkarim2222|Scheduler and Logic|

## Requirements

- Java JDK 17 or newer (no external libraries)
- Any IDE (IntelliJ IDEA, Eclipse, NetBeans, VS Code) or the command line

## How to run

### From the command line

Run from the repository root. The included `data/` directory must exist. When it has no
`state.txt`, the application starts with no students or jobs and an unset simulation clock.
An existing save is loaded automatically. Compile and run:

```bash
# compile
javac -encoding UTF-8 -d out $(find src -name "*.java")        # macOS / Linux
# Windows (PowerShell):
# javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName

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
cp test-data/state.txt data/state.txt      # macOS / Linux
# Copy-Item test-data\state.txt data\state.txt  # Windows PowerShell
```

After the administrator signs into an empty system, the application asks for the simulation
clock. The optional `test-data/empty-state.txt` provides the same empty starting state.
Unreadable or malformed existing saves produce an error; they are not replaced automatically.

Demo students use IDs `100000001` through `100000005`, with password `student123`.
Copying the demo snapshot replaces the current saved simulation; back up `data/state.txt`
first if you want to keep it.

## Using the screens

- Register a student account, then log in as `admin` / `gpu2026` to approve its request.
- Set the simulation start with the date/time picker. Displayed times use `dd/MM/yyyy HH:mm`.
- Students can view their allowance, search/filter their own jobs, confirm a submission's
  duration and review its charge before confirming cancellation.
- The administrator can approve/reject registrations, grant extra time, inspect all jobs and
  search for a student by university ID to inspect their balances and jobs. Job lists have
  search and status controls. The dashboard includes counts for all job states and busy GPUs.
  Advance the clock one minute at a time; real time does not run the simulation.
- Successful changes save automatically and refresh the information on each screen.
  The **Save** button retries a failed save, and **Log out** returns to login.
- Use the navigation buttons to open Home, Submit job, My jobs, Dashboard, Requests,
  Students or All jobs, depending on the signed-in user's role.
- Lists show five records per page. Use **Previous** and **Next**, enter the displayed
  global **Record number**, then click **View record** to read the details or select an action.
- Choose a task size with the four buttons on the job form. Requests and time grants use
  ordinary text fields for entering minutes and reasons. Minutes must be whole numbers
  without surrounding spaces.
- Read-only text fields display long descriptions, predictions and cancellation reasons.
  Click inside a field and use the arrow keys to read text beyond its visible width.

## Reviewing and committing the GUI and storage

The dependency order is **storage, controller, screens, then Main**. `AppController` holds
the services, signed-in user, status and requested destination. It never creates a screen.
Each screen uses only this controller and the existing model classes. Its named button
handler refreshes the screen, then passes the original event to Main's `ScreenHandler`.
Main creates the windows and opens the requested destination.

For example, the login button checks the entered credentials and asks the controller to
record the signed-in user and destination. The screen then notifies Main, which opens the
student home, administrator dashboard or initial clock setup window. Opening another screen
does not require a reference to that screen inside `LoginFrame` or `AppController`.

Review one production file at a time in this order. Storage and AppController already have
earlier commits; their current changes are ordinary follow-up commits, with no history rewrite:

| Order | File | Suggested commit message |
|---|---|---|
| 1 | `src/gpu/storage/DataStore.java` | `Handle first launch without saved data` |
| 2 | `src/gpu/ui/AppController.java` | `Add registration status feedback during login` |
| 3 | `src/gpu/ui/LoginFrame.java` | `Add login screen` |
| 4 | `src/gpu/ui/RegisterPanel.java` | `Add student registration screen` |
| 5 | `src/gpu/ui/ClockSetupFrame.java` | `Add simulation clock setup screen` |
| 6 | `src/gpu/ui/StudentPanel.java` | `Add student profile and job screens` |
| 7 | `src/gpu/ui/AdminDashboard.java` | `Add administrator dashboard and management screens` |
| 8 | `src/gpu/Main.java` | `Connect application screens in Main` |

Each cumulative step compiles with the existing model and logic sources. Until the final
Main change is included, the committed entry point remains
the original placeholder window. Screens have constructor overloads without a navigation
listener so their actions and local refresh can also be used before Main is connected.

Stage the specific production file you have reviewed. Documentation and the demo snapshot
can follow in separate commits. `out/` and runtime `data/state.txt` remain ignored.

## Saving and loading

`data/state.txt` contains the clock, students and balances, registration history, time grants,
jobs, cancellations and GPU assignments. The section order and model record formats remain
compatible with the prepared demo. `DataStore(String directory)` accepts an existing directory;
the default is `data`. A missing snapshot starts an empty system. A missing data directory,
a directory named `state.txt`, or an unreadable existing snapshot reports an opening error.

Saving builds and validates the records in memory before opening a `PrintWriter`. It writes
directly to the save file, closes it, then reads back and compares its lines. Invalid in-memory
records are rejected before opening the file. Invalid loaded records report filename and line.
Detected save failures retain the session's data and block logout/exit until saving succeeds.

Writing is not atomic: an interrupted or failed write can damage the previous snapshot.
`Scanner`/`PrintWriter` do not report every underlying I/O failure through the methods used. Keep a separate copy of important saves. Readers/writers
use the JVM default character set; the supplied snapshots contain ASCII text. UTF-8 is still
used to compile Java source files, but is no longer guaranteed for runtime snapshots.

## Project structure

```
src/gpu/
  Main.java      entry point, window creation, navigation and refresh
  model/         domain classes: User, Student, Administrator, Job, GPU, TimeAllowance, ...
  logic/         rules: Scheduler, SimulationClock, account handling (no javax.swing here)
  storage/       DataStore snapshot saving and loading
  ui/            AppController state/actions and independent Swing screens
data/            files written by the running program
test-data/       sample data for testing and the demonstration
docs/            report, class diagram, screenshots
```

## Team workflow

See [CONTRIBUTING.md](CONTRIBUTING.md).
