
package gpu.logic;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import gpu.model.GPU;
import gpu.model.Job;
import gpu.model.JobStatus;
import gpu.model.TaskSize;
import gpu.model.TimeAllowance;

/**
 * Controls four simulated GPUs, one shared FCFS queue, job execution,
 * predictions, cancellations and utilization.
 * All times come from SimulationClock; this class never uses the computer clock.
 */
public class Scheduler {

    public static final int GPU_COUNT = 4;

    private final SimulationClock clock;
    private final GPU[] gpus;
    private final PriorityQueue<Job> waitingJobs;
    private final List<Job> allJobs;
    private final Map<String, TimeAllowance> allowances;

    private int nextJobNumber = 101;

    public Scheduler(SimulationClock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("A simulation clock is required.");
        }

        this.clock = clock;
        this.gpus = new GPU[GPU_COUNT];

        for (int i = 0; i < GPU_COUNT; i++) {
            gpus[i] = new GPU(i + 1);
        }

        this.waitingJobs = new PriorityQueue<>();
        this.allJobs = new ArrayList<>();
        this.allowances = new HashMap<>();
    }

    public SimulationClock getClock() {
        return clock;
    }

    public List<GPU> getGpus() {
        List<GPU> result = new ArrayList<>();
        Collections.addAll(result, gpus);
        return Collections.unmodifiableList(result);
    }

    public GPU getGpu(int gpuId) {
        if (gpuId < 1 || gpuId > GPU_COUNT) {
            throw new IllegalArgumentException("GPU ID must be between 1 and 4.");
        }

        return gpus[gpuId - 1];
    }

    public int getFreeGpuCount() {
        int count = 0;

        for (GPU gpu : gpus) {
            if (gpu.isFree()) {
                count++;
            }
        }

        return count;
    }

    /** Registers an existing student's allowance, normally during storage restoration. */
    public void registerAllowance(TimeAllowance allowance) {
        if (allowance == null) {
            throw new IllegalArgumentException("Student allowance is required.");
        }

        String studentId = allowance.getOwnerId();
        TimeAllowance existing = allowances.get(studentId);

        if (existing != null && existing != allowance) {
            throw new IllegalStateException(
                "A different allowance is already registered for " + studentId + "."
            );
        }

        allowances.put(studentId, allowance);
    }

    /**
     * Reserves the whole job duration, adds the job to the FCFS queue,
     * and immediately starts it if a GPU is free.
     */
    public Job submitJob(TimeAllowance allowance,
                         String title,
                         String applicationName,
                         String description,
                         TaskSize taskSize) {

        if (!clock.isSet()) {
            throw new IllegalStateException(
                "The administrator must set the simulation clock first."
            );
        }

        if (allowance == null) {
            throw new IllegalArgumentException("Student allowance is required.");
        }

        String studentId = allowance.getOwnerId();
        TimeAllowance existing = allowances.get(studentId);

        if (existing != null && existing != allowance) {
            throw new IllegalStateException(
                "A different allowance is already registered for " + studentId + "."
            );
        }

        String jobId = "J" + nextJobNumber;

        Job job = new Job(
            jobId,
            studentId,
            title,
            applicationName,
            description,
            taskSize,
            clock.getCurrentTime()
        );

        // TimeAllowance rejects jobs above the available balance; exact matches are accepted.
        allowance.reserve(job.getEstimatedDuration());

        allowances.put(studentId, allowance);
        allJobs.add(job);
        waitingJobs.offer(job);

        nextJobNumber++;

        startWaitingJobs();

        return job;
    }

    /**
     * Adds a previously saved job without charging its allowance again.
     * Register allowances first, restore all jobs, then call startQueuedJobs().
     */
    public void restoreJob(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Job is required.");
        }

        if (getJobById(job.getJobId()) != null) {
            throw new IllegalStateException(
                "Job " + job.getJobId() + " has already been restored."
            );
        }

        if (job.getStatus() == JobStatus.QUEUED
                || job.getStatus() == JobStatus.RUNNING) {
            requireAllowance(job.getStudentId());
        }

        if (job.getStatus() == JobStatus.QUEUED) {
            waitingJobs.offer(job);

        } else if (job.getStatus() == JobStatus.RUNNING) {
            Integer gpuId = job.getAssignedGpuId();

            if (gpuId == null) {
                throw new IllegalArgumentException(
                    "Running job " + job.getJobId() + " has no GPU ID."
                );
            }

            getGpu(gpuId).restore(job);
        }

        allJobs.add(job);
        nextJobNumber = Math.max(nextJobNumber, job.getJobNumber() + 1);
    }

    /** Starts restored queued jobs after all saved running jobs have been restored. */
    public void startQueuedJobs() {
        if (!clock.isSet()) {
            throw new IllegalStateException("The simulation clock has not been set.");
        }

        startWaitingJobs();
    }

    /** Assigns queued jobs in FCFS order to free GPUs in increasing GPU ID order. */
    private void startWaitingJobs() {
        for (GPU gpu : gpus) {
            if (gpu.isFree() && !waitingJobs.isEmpty()) {
                Job nextJob = waitingJobs.poll();
                gpu.assign(nextJob, clock.getCurrentTime());
            }
        }
    }

    /**
     * Advances the clock one minute, settles jobs that finish, frees their GPUs,
     * and immediately starts the next jobs in the FCFS queue.
     *
     * @return immutable list of jobs completed during this minute
     */
    public List<Job> advanceMinute() {
        if (!clock.isSet()) {
            throw new IllegalStateException("The simulation clock has not been set.");
        }

        clock.advance();

        LocalDateTime now = clock.getCurrentTime();
        List<Job> completedJobs = new ArrayList<>();

        for (GPU gpu : gpus) {
            Job runningJob = gpu.getCurrentJob();

            if (runningJob != null
                    && runningJob.getRemainingRuntime(now) == 0) {

                runningJob.complete(now);

                TimeAllowance allowance =
                    requireAllowance(runningJob.getStudentId());

                allowance.settle(
                    runningJob.getEstimatedDuration(),
                    runningJob.getChargedMinutes()
                );

                gpu.release();
                completedJobs.add(runningJob);
            }
        }

        startWaitingJobs();

        return Collections.unmodifiableList(completedJobs);
    }

    /**
     * Cancels a queued or running job.
     *
     * Administrator identity is "admin"; a student must supply their own university ID.
     * The GUI/controller must pass the username of the authenticated session, not typed input.
     */
    public Job cancelJob(String jobId, String cancelledBy, String reason) {
        if (!clock.isSet()) {
            throw new IllegalStateException("The simulation clock has not been set.");
        }

        Job job = getJobById(jobId);

        if (job == null) {
            throw new IllegalArgumentException("Job " + jobId + " was not found.");
        }

        if (cancelledBy == null || cancelledBy.isBlank()) {
            throw new IllegalArgumentException(
                "The person cancelling the job must be identified."
            );
        }

        String actor = cancelledBy.trim();

        if (!"admin".equals(actor)
                && !job.getStudentId().equals(actor)) {
            throw new IllegalStateException(
                "You can only cancel your own jobs."
            );
        }

        if (!job.isCancellable()) {
            throw new IllegalStateException(
                "Job " + jobId + " can no longer be cancelled."
            );
        }

        TimeAllowance allowance = requireAllowance(job.getStudentId());

        boolean wasRunning = job.getStatus() == JobStatus.RUNNING;
        GPU runningGpu = null;

        if (wasRunning) {
            Integer gpuId = job.getAssignedGpuId();

            if (gpuId == null) {
                throw new IllegalStateException("The running job has no GPU ID.");
            }

            runningGpu = getGpu(gpuId);

            if (runningGpu.getCurrentJob() != job) {
                throw new IllegalStateException(
                    "The job is not assigned to its recorded GPU."
                );
            }

        } else if (!waitingJobs.contains(job)) {
            throw new IllegalStateException(
                "The queued job is missing from the waiting queue."
            );
        }

        job.cancel(actor, reason, clock.getCurrentTime());

        if (wasRunning) {
            allowance.settle(
                job.getEstimatedDuration(),
                job.getChargedMinutes()
            );

            runningGpu.release();

        } else {
            waitingJobs.remove(job);
            allowance.release(job.getEstimatedDuration());
        }

        // A freed GPU can now start the next waiting job.
        startWaitingJobs();

        return job;
    }

    public Job getJobById(String jobId) {
        if (jobId == null) {
            return null;
        }

        for (Job job : allJobs) {
            if (job.getJobId().equals(jobId)) {
                return job;
            }
        }

        return null;
    }

    public List<Job> getAllJobs() {
        return Collections.unmodifiableList(new ArrayList<>(allJobs));
    }

    public List<Job> getQueuedJobs() {
        List<Job> result = new ArrayList<>(waitingJobs);
        Collections.sort(result);

        return Collections.unmodifiableList(result);
    }

    public List<Job> getRunningJobs() {
        List<Job> result = new ArrayList<>();

        for (GPU gpu : gpus) {
            if (!gpu.isFree()) {
                result.add(gpu.getCurrentJob());
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<Job> getCompletedJobs() {
        List<Job> result = new ArrayList<>();

        for (Job job : allJobs) {
            if (job.getStatus() == JobStatus.COMPLETED) {
                result.add(job);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<Job> getCancelledJobs() {
        List<Job> result = new ArrayList<>();

        for (Job job : allJobs) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                result.add(job);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public int getQueuePosition(String jobId) {
        List<Job> queue = getQueuedJobs();

        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getJobId().equals(jobId)) {
                return i + 1;
            }
        }

        return -1;
    }

    /** Creates a forecast on copied GPU availability times; live GPU assignments are untouched. */
    private Map<String, LocalDateTime> calculatePredictedStarts() {
        Map<String, LocalDateTime> predictions = new HashMap<>();

        if (!clock.isSet()) {
            return predictions;
        }

        LocalDateTime[] freeAt = new LocalDateTime[GPU_COUNT];
        LocalDateTime now = clock.getCurrentTime();

        for (int i = 0; i < GPU_COUNT; i++) {
            freeAt[i] = gpus[i].getFreeAt(now);
        }

        for (Job job : getQueuedJobs()) {
            // Strictly-before keeps the lower GPU ID when availability times tie.
            int selectedGpu = 0;

            for (int i = 1; i < GPU_COUNT; i++) {
                if (freeAt[i].isBefore(freeAt[selectedGpu])) {
                    selectedGpu = i;
                }
            }

            LocalDateTime predictedStart = freeAt[selectedGpu];

            if (predictedStart.isBefore(job.getSubmissionTime())) {
                predictedStart = job.getSubmissionTime();
            }

            predictions.put(job.getJobId(), predictedStart);

            freeAt[selectedGpu] =
                predictedStart.plusMinutes(job.getEstimatedDuration());
        }

        return predictions;
    }

    public LocalDateTime getPredictedStartTime(String jobId) {
        Job job = getJobById(jobId);

        if (job == null) {
            return null;
        }

        if (job.hasStarted()) {
            return job.getStartTime();
        }

        if (job.getStatus() != JobStatus.QUEUED) {
            return null;
        }

        return calculatePredictedStarts().get(jobId);
    }

    public LocalDateTime getPredictedFinishTime(String jobId) {
        Job job = getJobById(jobId);

        if (job == null) {
            return null;
        }

        if (job.getStatus() == JobStatus.RUNNING) {
            return job.getExpectedFinish();
        }

        if (job.getStatus() == JobStatus.COMPLETED
                || (job.getStatus() == JobStatus.CANCELLED && job.hasStarted())) {
            return job.getFinishTime();
        }

        LocalDateTime start = getPredictedStartTime(jobId);

        return start == null
            ? null
            : start.plusMinutes(job.getEstimatedDuration());
    }

    /**
     * For a queued job, returns predicted start minus CURRENT simulation time.
     * For a job that has started, returns its actual total time spent waiting in the queue.
     * Returns -1 when no wait can be calculated.
     */
    public long getPredictedWaitMinutes(String jobId) {
        Job job = getJobById(jobId);

        if (job == null) {
            return -1;
        }

        if (job.hasStarted()) {
            return job.getQueueWaitMinutes();
        }

        if (job.getStatus() != JobStatus.QUEUED || !clock.isSet()) {
            return -1;
        }

        LocalDateTime predictedStart = getPredictedStartTime(jobId);

        if (predictedStart == null) {
            return -1;
        }

        return Math.max(
            0L,
            Duration.between(clock.getCurrentTime(), predictedStart).toMinutes()
        );
    }

    private long getBusyMinutesForGpu(int gpuId) {
        long total = 0;
        LocalDateTime now = clock.getCurrentTime();

        for (Job job : allJobs) {
            Integer assignedGpuId = job.getAssignedGpuId();

            if (assignedGpuId == null || assignedGpuId.intValue() != gpuId) {
                continue;
            }

            if (job.getStatus() == JobStatus.RUNNING) {
                total += job.getRuntimeMinutes(now);

            } else if (job.getStatus() == JobStatus.COMPLETED
                    || (job.getStatus() == JobStatus.CANCELLED && job.hasStarted())) {
                total += job.getChargedMinutes();
            }
        }

        return total;
    }

    public long getTotalBusyGpuMinutes() {
        long total = 0;

        for (int i = 1; i <= GPU_COUNT; i++) {
            total += getBusyMinutesForGpu(i);
        }

        return total;
    }

    /**
     * Returns one GPU's utilization percentage. Returns Double.NaN before one minute has elapsed;
     * the UI should display that value as "N/A".
     */
    public double getGpuUtilizationPercent(int gpuId) {
        getGpu(gpuId);

        long elapsed = clock.getElapsedMinutes();

        if (elapsed <= 0) {
            return Double.NaN;
        }

        return getBusyMinutesForGpu(gpuId) * 100.0 / elapsed;
    }

    /**
     * Returns overall utilization percentage. Returns Double.NaN at time zero;
     * the UI should display that value as "N/A".
     */
    public double getOverallUtilizationPercent() {
        long elapsed = clock.getElapsedMinutes();

        if (elapsed <= 0) {
            return Double.NaN;
        }

        double totalCapacity = elapsed * GPU_COUNT;

        return getTotalBusyGpuMinutes() * 100.0 / totalCapacity;
    }

    private TimeAllowance requireAllowance(String studentId) {
        TimeAllowance allowance = allowances.get(studentId);

        if (allowance == null) {
            throw new IllegalStateException(
                "No time allowance is registered for student " + studentId + "."
            );
        }

        return allowance;
    }
}

