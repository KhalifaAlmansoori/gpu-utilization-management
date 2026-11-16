package gpu.model;

import java.time.LocalDateTime;

/**
 * One simulated H100 GPU. Runs one job at a time, 24/7.
 * The Scheduler owns the four GPU objects; predictions work on copies of the free-at times,
 * never on these objects.
 */
public class GPU {

    private final int id;
    private Job currentJob;

    public GPU(int id) {
        if (id < 1) {
            throw new IllegalArgumentException("GPU ID must be 1 or more.");
        }
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean isFree() {
        return currentJob == null;
    }

    /** The job running now, or null if the GPU is free. */
    public Job getCurrentJob() {
        return currentJob;
    }

    /** Starts a queued job on this GPU at the given simulated time. */
    public void assign(Job job, LocalDateTime now) {
        if (!isFree()) {
            throw new IllegalStateException("GPU" + id + " is busy with " + currentJob.getJobId() + ".");
        }
        job.start(id, now); // checks the job is Queued; GPU stays free if this throws
        currentJob = job;
    }

    /** Frees the GPU and returns the job that was on it (null if it was already free). */
    public Job release() {
        Job finished = currentJob;
        currentJob = null;
        return finished;
    }

    /** Puts a job that was Running when the program was closed back on its GPU (loading data). */
    public void restore(Job runningJob) {
        if (!isFree()) {
            throw new IllegalStateException("GPU" + id + " already has a job.");
        }
        if (runningJob.getStatus() != JobStatus.RUNNING
                || runningJob.getAssignedGpuId() == null || runningJob.getAssignedGpuId() != id) {
            throw new IllegalArgumentException("Job " + runningJob.getJobId() + " is not running on GPU" + id + ".");
        }
        currentJob = runningJob;
    }

    /** Minutes left on the current job; 0 if free. */
    public int getRemainingRuntime(LocalDateTime now) {
        return isFree() ? 0 : currentJob.getRemainingRuntime(now);
    }

    /** When the current job will finish, or null if free. */
    public LocalDateTime getExpectedFinish() {
        return isFree() ? null : currentJob.getExpectedFinish();
    }

    /** The time this GPU can take a new job: {@code now} if free, else the running job's finish. */
    public LocalDateTime getFreeAt(LocalDateTime now) {
        return isFree() ? now : currentJob.getExpectedFinish();
    }

    @Override
    public String toString() {
        return "GPU" + id + (isFree() ? " (free)" : " (" + currentJob.getJobId() + ")");
    }
}
