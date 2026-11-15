# Model API (package `gpu.model`)

Contract between Khalifa (model), Abdulkarim (logic) and Farouk (GUI and storage).
The model never imports `javax.swing`. If you need a method that is not listed, ask me or add it in a small commit and tell the group.

All times are `java.time.LocalDateTime` taken from the simulation clock. Never call `LocalDateTime.now()`.
Show times with `TimeFormat.format(time)` (dd/MM/yyyy HH:mm).
Rule violations throw `IllegalStateException` (move not allowed, not enough time) or
`IllegalArgumentException` (bad input). The message is written for the user, so the GUI can show `e.getMessage()` in a dialog.

## Enums

| Enum | Values | Useful methods |
|---|---|---|
| `TaskSize` | SMALL, MEDIUM, LARGE, VERY_LARGE | `getLabel()`, `getWorkloadUnits()`, `getDurationMinutes()`, `parse(text)`; `toString()` = "Large (30 units)" (use directly in a JComboBox) |
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
