package gpu.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One GPU job. Allowed moves only:
 * Queued -> Running or Cancelled, Running -> Completed or Cancelled.
 * The checks live here (start(), complete(), cancel() throw), not in the GUI.
 * <p>
 * Queue position, predicted times, remaining wait and charges are NOT stored; they are computed
 * from these records and the simulated clock whenever they are shown.
 * <p>
 * There are no setters for the student's inputs: an accepted job cannot be edited.
 * Implements {@link Cancellable} and {@link Storable} (interfaces) and {@link Comparable}
 * in FCFS order (submission time, then job ID).
 */
public class Job implements Cancellable, Storable, Comparable<Job> {

    // Set by the student
    private final String title;
    private final String applicationName;
    private final String description;
    private final TaskSize taskSize;

    // Set by the system when the job is accepted
    private final String jobId;
    private final String studentId;
    private final LocalDateTime submissionTime;

    // Change as the job moves through its life
    private JobStatus status = JobStatus.QUEUED;
    private Integer assignedGpuId;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime cancelTime;
    private String cancelledBy;
    private String cancelReason;

    public Job(String jobId, String studentId, String title, String applicationName,
               String description, TaskSize taskSize, LocalDateTime submissionTime) {
        requireText(jobId, "Job ID");
        requireText(studentId, "Student ID");
        requireText(title, "Job title");
        requireText(applicationName, "Application name");
        requireText(description, "Description");
        if (taskSize == null) {
            throw new IllegalArgumentException("Please select a task size.");
        }
        if (submissionTime == null) {
            throw new IllegalArgumentException("The simulation clock must be set before submitting jobs.");
        }
        this.jobId = jobId.trim();
        this.studentId = studentId.trim();
        this.title = title.trim();
        this.applicationName = applicationName.trim();
        this.description = description.trim();
        this.taskSize = taskSize;
        this.submissionTime = submissionTime;
        parseJobNumber(this.jobId); // fails now if the ID is not like J101
    }

    private static int parseJobNumber(String id) {
        try {
            return Integer.parseInt(id.substring(1));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Job ID must look like J101, but was \"" + id + "\".");
        }
    }

    // ------------------------------------------------------------------ simple getters

    public String getJobId() {
        return jobId;
    }

    /** Numeric part of the ID ("J101" -> 101). Used to break FCFS ties. */
    public int getJobNumber() {
        return parseJobNumber(jobId);
    }

    public String getStudentId() {
        return studentId;
    }

    public String getTitle() {
        return title;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getDescription() {
        return description;
    }

    public TaskSize getTaskSize() {
        return taskSize;
    }

    public int getWorkloadUnits() {
        return taskSize.getWorkloadUnits();
    }

    /** Minutes; equals the workload units. */
    public int getEstimatedDuration() {
        return taskSize.getDurationMinutes();
    }

    public LocalDateTime getSubmissionTime() {
        return submissionTime;
    }

    public JobStatus getStatus() {
        return status;
    }

    /** GPU ID, or null if the job never started. Kept after the job ends. */
    public Integer getAssignedGpuId() {
        return assignedGpuId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** Completion time, or the time a RUNNING job was cancelled. Null otherwise. */
    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    // ------------------------------------------------------------------ state changes

    /** Queued -> Running on the given GPU at the given simulated time. */
    public void start(int gpuId, LocalDateTime now) {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException("Job " + jobId + " is " + status + " and cannot start.");
        }
        if (gpuId < 1) {
            throw new IllegalArgumentException("GPU ID must be 1 or more.");
        }
        requireNotBefore(now, submissionTime, "start");
        status = JobStatus.RUNNING;
        assignedGpuId = gpuId;
        startTime = now;
    }

    /** Running -> Completed. Only allowed once the full duration has run. */
    public void complete(LocalDateTime now) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Job " + jobId + " is " + status + " and cannot complete.");
        }
        requireNotBefore(now, startTime, "complete");
        long ran = Duration.between(startTime, now).toMinutes();
        if (ran < getEstimatedDuration()) {
            throw new IllegalStateException("Job " + jobId + " has run " + ran + " of "
                    + getEstimatedDuration() + " min and is not finished.");
        }
        status = JobStatus.COMPLETED;
        finishTime = now;
    }

    @Override
    public boolean isCancellable() {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }

    /**
     * Queued or Running -> Cancelled. Records who, when and why.
     * A running job also gets its finishTime (the time it stopped); a queued one keeps none.
     */
    @Override
    public void cancel(String cancelledBy, String reason, LocalDateTime when) {
        if (!isCancellable()) {
            throw new IllegalStateException("Job " + jobId + " is " + status + " and cannot be cancelled.");
        }
        requireText(cancelledBy, "Cancelling user");
        requireText(reason, "Cancellation reason");
        requireNotBefore(when, startTime != null ? startTime : submissionTime, "cancel");
        if (status == JobStatus.RUNNING) {
            finishTime = when;
        }
        status = JobStatus.CANCELLED;
        cancelTime = when;
        this.cancelledBy = cancelledBy.trim();
        this.cancelReason = reason.trim();
    }

    // ------------------------------------------------------------------ calculated values

    /** True once the job has ever run on a GPU (Running, Completed, or cancelled while running). */
    public boolean hasStarted() {
        return startTime != null;
    }

    /**
     * Minutes actually executed on a GPU, never counting waiting.
     * Queued: 0. Running: now - start. Completed or cancelled-while-running: finish - start.
     */
    public int getRuntimeMinutes(LocalDateTime now) {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime end = (status == JobStatus.RUNNING) ? now : finishTime;
        if (end == null) {
            return 0;
        }
        long minutes = Duration.between(startTime, end).toMinutes();
        return (int) Math.max(0, Math.min(minutes, getEstimatedDuration()));
    }

    /** Minutes still to run for a RUNNING job; 0 for every other status. */
    public int getRemainingRuntime(LocalDateTime now) {
        if (status != JobStatus.RUNNING) {
            return 0;
        }
        return getEstimatedDuration() - getRuntimeMinutes(now);
    }

    /** start + duration for a job that has started; null for a job that never ran. */
    public LocalDateTime getExpectedFinish() {
        return startTime == null ? null : startTime.plusMinutes(getEstimatedDuration());
    }

    /**
     * Minutes charged to the student's allowance once the job is settled:
     * Completed = full runtime, cancelled while running = elapsed runtime,
     * cancelled while queued = 0. Queued and Running jobs are not settled yet, so 0.
     */
    public int getChargedMinutes() {
        if (status == JobStatus.COMPLETED || (status == JobStatus.CANCELLED && startTime != null)) {
            return (int) Duration.between(startTime, finishTime).toMinutes();
        }
        return 0;
    }

    /** Total queue wait = start - submission, in minutes; -1 if the job never started. */
    public int getQueueWaitMinutes() {
        if (startTime == null) {
            return -1;
        }
        return (int) Duration.between(submissionTime, startTime).toMinutes();
    }

    // ------------------------------------------------------------------ FCFS order, equality

    /** First Come First Served: by submission time, then by job ID number. */
    @Override
    public int compareTo(Job other) {
        int byTime = submissionTime.compareTo(other.submissionTime);
        return byTime != 0 ? byTime : Integer.compare(getJobNumber(), other.getJobNumber());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Job && ((Job) o).jobId.equals(jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }

    @Override
    public String toString() {
        return jobId + " [" + status + "] " + title;
    }

    // ------------------------------------------------------------------ saving and loading

    @Override
    public String toLine() {
        return Storable.join(jobId, studentId, title, applicationName, description,
                taskSize.name(), TimeFormat.format(submissionTime), status.name(),
                assignedGpuId == null ? "" : String.valueOf(assignedGpuId),
                TimeFormat.format(startTime), TimeFormat.format(finishTime),
                TimeFormat.format(cancelTime), cancelledBy, cancelReason);
    }

    /** Rebuilds a job from a saved line and checks that its fields make sense together. */
    public static Job fromLine(String line) {
        List<String> f = Storable.split(line);
        if (f.size() != 14) {
            throw new IllegalArgumentException("expected 14 fields but found " + f.size());
        }
        Job job = new Job(f.get(0), f.get(1), f.get(2), f.get(3), f.get(4),
                TaskSize.parse(f.get(5)), TimeFormat.parse(f.get(6)));
        job.status = JobStatus.parse(f.get(7));
        job.assignedGpuId = f.get(8).isEmpty() ? null : Integer.valueOf(f.get(8));
        job.startTime = TimeFormat.parse(f.get(9));
        job.finishTime = TimeFormat.parse(f.get(10));
        job.cancelTime = TimeFormat.parse(f.get(11));
        job.cancelledBy = f.get(12).isEmpty() ? null : f.get(12);
        job.cancelReason = f.get(13).isEmpty() ? null : f.get(13);
        job.checkSavedState();
        return job;
    }

    private void checkSavedState() {
        boolean ran = startTime != null;
        boolean ok;
        switch (status) {
            case QUEUED:
                ok = assignedGpuId == null && !ran && finishTime == null && cancelTime == null;
                break;
            case RUNNING:
                ok = assignedGpuId != null && ran && finishTime == null && cancelTime == null;
                break;
            case COMPLETED:
                ok = assignedGpuId != null && ran && finishTime != null && cancelTime == null;
                break;
            default: // CANCELLED
                ok = cancelTime != null && cancelledBy != null && cancelReason != null
                        && (ran ? (assignedGpuId != null && finishTime != null)
                                : (assignedGpuId == null && finishTime == null));
        }
        if (!ok) {
            throw new IllegalArgumentException("job " + jobId + " has fields that do not match status " + status);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be empty.");
        }
    }

    private static void requireNotBefore(LocalDateTime time, LocalDateTime limit, String action) {
        if (time == null) {
            throw new IllegalArgumentException("The simulated time is required to " + action + " a job.");
        }
        if (time.isBefore(limit)) {
            throw new IllegalArgumentException("Cannot " + action + " a job before "
                    + TimeFormat.format(limit) + ".");
        }
    }
}
