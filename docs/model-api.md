# Model API (package `gpu.model`)

Contract between Khalifa (model), Abdulkarim (logic) and Farouk (GUI and storage).
The model never imports `javax.swing`. If you need a method that is not listed, ask me or add it in a small commit and tell the group.

All times are `java.time.LocalDateTime` taken from the simulation clock. Never call `LocalDateTime.now()`.
Show times with `TimeFormat.format(time)` (dd/MM/yyyy HH:mm).
Rule violations throw `IllegalStateException` (move not allowed, not enough time) or
`IllegalArgumentException` (bad input). The message is written for the user, so the GUI can show `e.getMessage()` in its read-only message field.

## Enums

| Enum | Values | Useful methods |
|---|---|---|
| `TaskSize` | SMALL, MEDIUM, LARGE, VERY_LARGE | `getLabel()`, `getWorkloadUnits()`, `getDurationMinutes()`, `parse(text)`; `toString()` = "Large (30 units)" (displayed on the task-size buttons) |
| `JobStatus` | QUEUED, RUNNING, COMPLETED, CANCELLED | `getLabel()`, `isFinal()`, `parse(text)` |
| `RequestStatus` | PENDING, APPROVED, REJECTED | `getLabel()`, `parse(text)` |

## Interfaces

| Interface | Methods | Implemented by |
|---|---|---|
| `Cancellable` | `isCancellable()`, `cancel(by, reason, when)` | `Job` |
| `Storable` | `toLine()`, plus helpers `Storable.join(...)`, `Storable.split(line)` | `Job`, `Student`, `AccessRequest`, `TimeAllowance.Grant` |

Each Storable class also has a static `fromLine(String line)` that throws `IllegalArgumentException` with a readable message
(for example `bad date "16-11-26" (expected dd/MM/yyyy HH:mm)`). The storage code should add the file name and line number to that message.

## Classes

### `User` (abstract) -> `Student`, `Administrator`
- `getUsername()`, `getFullName()`, `checkPassword(String)`, `getRole()`

### `Student extends User` (Storable)
- `new Student(universityId, fullName, email, phone, password, initialMinutes)`
- `getUniversityId()`, `getEmail()`, `getPhone()`, `getAllowance()`
- `toLine()`, `Student.fromLine(line)`

### `Administrator extends User`
- `new Administrator()`; constants `USERNAME = "admin"`, `PASSWORD = "gpu2026"`
- `approve(AccessRequest, minutes)` returns the new `Student` (throws if already processed); `approve(AccessRequest)` uses 100
- `reject(AccessRequest)`
- `grantTime(Student, minutes, reason, when)` returns the recorded `TimeAllowance.Grant`
- Duplicate-account checks need the full list of students, so they belong in `AccountService` (Member 2).
- Cancelling any job is done through the Scheduler with `by = "admin"`.

### `AccessRequest` (Storable)
- `new AccessRequest(universityId, fullName, email, phone, password)` (status PENDING)
- getters, `getStatus()`, `isPending()`, `approve()`, `reject()`, `toStudent(minutes)`
- Email/phone format checks and "ID already registered" are validation in `AccountService` / the GUI.

### `TimeAllowance`
- `new TimeAllowance(ownerId, allocated)`; `DEFAULT_MINUTES = 100`
- `getAllocated()`, `getUsed()`, `getReserved()`, `available()`
- `canReserve(minutes)`, `reserve(minutes)` (throws if more than available; exact match is OK)
- `release(minutes)` (queued cancel, no charge)
- `settle(reservedMinutes, chargedMinutes)` (use this when a job completes or a running job is cancelled; releases the reservation and charges once)
- `grant(minutes, reason, by, when)`, `setAllocated(n)`, `getGrants()`

### `Job` (Cancellable, Storable, Comparable)
- `new Job(jobId, studentId, title, applicationName, description, taskSize, submissionTime)`: status QUEUED. The Scheduler generates `jobId` ("J101", "J102", ...).
- Getters: `getJobId()`, `getStudentId()`, `getTitle()`, `getApplicationName()`, `getDescription()`, `getTaskSize()`, `getWorkloadUnits()`, `getEstimatedDuration()`, `getSubmissionTime()`, `getStatus()`, `getAssignedGpuId()`, `getStartTime()`, `getFinishTime()`, `getCancelTime()`, `getCancelledBy()`, `getCancelReason()`
- State changes: `start(gpuId, now)`, `complete(now)`, `cancel(by, reason, when)`
- Calculated (never stored): `getRuntimeMinutes(now)`, `getRemainingRuntime(now)`, `getExpectedFinish()`, `getChargedMinutes()`, `getQueueWaitMinutes()` (-1 if never started), `hasStarted()`, `isCancellable()`
- `compareTo` is FCFS order (submission time, then job number), so `Collections.sort(list)` or a `PriorityQueue<Job>` works.

### `GPU`
- `new GPU(id)`; `getId()`, `isFree()`, `getCurrentJob()`
- `assign(job, now)` (calls `job.start`), `release()` returns the job that was on the GPU
- `getRemainingRuntime(now)`, `getExpectedFinish()`, `getFreeAt(now)` (now if free, else finish time; useful for predictions)
- `restore(runningJob)` for loading saved data

## Settlement recipe for Member 2

| Event | Calls |
|---|---|
| Accept job | `allowance.reserve(duration)`, add job to the queue |
| Job starts | `gpu.assign(job, now)` (lowest free GPU ID, first job in FCFS order) |
| Job finishes | `job.complete(now)`, `allowance.settle(duration, job.getChargedMinutes())`, `gpu.release()` |
| Cancel queued | `job.cancel(by, why, now)`, `allowance.release(duration)` |
| Cancel running | `job.cancel(by, why, now)`, `allowance.settle(duration, job.getChargedMinutes())`, `gpu.release()` |

## Saved line formats (fields separated by `|`, special characters escaped)

| Class | Fields |
|---|---|
| `Student` | id, name, email, phone, password, allocated, used, reserved |
| `AccessRequest` | id, name, email, phone, password, status |
| `Job` | jobId, studentId, title, app, description, size, submitted, status, gpuId, start, finish, cancelTime, cancelledBy, reason |
| `TimeAllowance.Grant` | studentId, minutes, reason, grantedBy, time |

The clock is saved by the storage code as `dd/MM/yyyy HH:mm` text.

## Storage snapshot and screen integration

`DataStore` saves `data/state.txt` as text using the JVM default character set. The first line is
`GPU-UTILIZATION-STATE|1`, followed by sections in this order:

1. `[clock]`: one line, either `unset` or `set|start time|current time`.
2. `[students]`: each student's `toLine()`, including allocated/used/reserved balances.
3. `[requests]`: each request's `toLine()`, including processed history.
4. `[grants]`: each `TimeAllowance.Grant.toLine()`.
5. `[jobs]`: each job's `toLine()`, including its state and GPU assignment.

Both clock timestamps use `dd/MM/yyyy HH:mm`; the original start time is needed for utilization.
The section markers wrap the existing model records without changing their field formats.
Do not insert blank records. Rejected historical requests may share an ID with a later request.

`DataStore.load()` returns `DataStore.State(accounts, scheduler)` with a shared clock available
through `scheduler.getClock()`. It restores student allowances before jobs and reattaches
grants without allocating their minutes again. A missing file inside an existing directory
returns an empty state with an unset clock. Missing directories, invalid save paths and
unreadable existing files throw `IOException`; malformed records report the filename and line
number. `DataStore(String directory)` takes an existing directory. The optional empty snapshot
is a fixture, not a first-launch requirement.

Storage uses `Scanner`, `File`, `PrintWriter`, `ArrayList`, string operations, loops and
exception handlers. `File.exists()`/`File.isFile()` checks
distinguish missing snapshots from invalid paths at first use. Storage validates references, timestamps and job/balance
consistency in memory before opening the destination. It then writes directly, closes the
writer and compares the saved lines with the requested lines. Unsupported Unicode record
separators are rejected before writing; ordinary CR/LF remain escaped by `Storable`.

Saving does not provide atomic replacement, guaranteed detection of every I/O failure or an
explicit snapshot charset. A failed or interrupted write can damage an earlier save.
Keep a separate backup. `Scanner` may treat an underlying read failure as end of input, and
`PrintWriter` may suppress errors; read-back is a basic check, not a stronger guarantee.
Restored clock records must round-trip through `TimeFormat` unchanged; date ordering
uses formatted four-digit years and minute precision, matching the simulation's input format.

`AppController` owns these shared services, the signed-in user, status and requested screen
destination. It imports no Swing classes and does not reference any concrete screen class.
Its `login(...)` method authenticates through AccountService and distinguishes incorrect
credentials, pending approval and rejected registration in the feedback.
Named `ActionListener` classes call its ordinary methods such as `submitJob(...)`,
`approveRequest(...)` or `advanceMinute()`. Each method calls the existing logic service,
saves successful changes and records validation or save errors. Business rules remain in
`gpu.logic` and `gpu.model`.

Each screen refreshes its own read-only fields after a button action, then forwards the
original event to an optional navigation listener. `Main.ScreenHandler` receives that event;
Main reads `screen()` and `view()`, opens the requested window and refreshes all created
screens. `showLogin()`, `showRegistration()`, `showStudent(...)`, `showAdmin(...)` and
`showClockSetup()` only update the requested destination. Main alone creates concrete screen
objects, so the dependency order is DataStore, AppController, each screen, then Main.
Code calling controller methods directly can call `refresh()` on the existing Main instance
afterward to update its windows. The controller itself does not refresh GUI components.

The controller exposes `signedIn()` for the current `User`, `status()` for feedback,
`hasUnsavedChanges()` for close protection, and these destination values:

| `screen()` | `view()` | Window or panel Main displays |
|---|---|---|
| `"login"` | 0 | `LoginFrame` |
| `"registration"` | 0 | `RegisterPanel` in its window |
| `"clock"` | 0 | `ClockSetupFrame` |
| `"student"` | 0, 1, 2 | Home, Submit job, My jobs |
| `"admin"` | 0, 1, 2, 3 | Dashboard, Requests, Students, All jobs |

Student and administrator navigation methods check the signed-in role before changing the
destination. Clock setup can be requested by the administrator while the clock is unset.
Each screen also has a constructor without a navigation listener: its actions and local
refresh work independently, while opening the requested destination requires Main's listener.

The GUI uses frames, labels, text fields, buttons, panels and Grid/Flow/Border layouts.
`JPasswordField` hides password entry. `JSpinner`/`SpinnerDateModel` with `DateEditor`
provides the clock picker. Lists use five-record pages; search/filter controls use text
fields and buttons. Submission and cancellation use confirmation dialogs; only Yes applies
the action. Feedback appears in red labels.
When saving fails, Main gives the active window the `DO_NOTHING_ON_CLOSE` option;
the Save and Exit buttons retry saving before the application can close. A failure during a
manual save also marks the in-memory snapshot as unsaved, even if no new action was performed.
