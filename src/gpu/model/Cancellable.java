package gpu.model;

import java.time.LocalDateTime;

/**
 * Contract for something that can be cancelled with a recorded user, time and reason.
 * Implemented by {@link Job}.
 */
public interface Cancellable {

    /** True while cancellation is still allowed (for a job: Queued or Running). */
    boolean isCancellable();

    /**
     * Cancels the object and records who did it, when (simulated time) and why.
     *
     * @throws IllegalStateException    if it can no longer be cancelled
     * @throws IllegalArgumentException if the user or reason is blank, or the time is null
     */
    void cancel(String cancelledBy, String reason, LocalDateTime when);
}
