
package gpu.logic;

import java.time.Duration;
import java.time.LocalDateTime;

/** Controls the simulated time used by the GPU scheduler. */
public class SimulationClock {

    private LocalDateTime startTime;
    private LocalDateTime currentTime;

    public SimulationClock() {
        this.startTime = null;
        this.currentTime = null;
    }

    /** Sets the initial simulation time once. */
    public void setStartTime(LocalDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException("Start time cannot be null.");
        }

        if (isSet()) {
            throw new IllegalStateException(
                "The simulation clock has already been started."
            );
        }

        this.startTime = time;
        this.currentTime = time;
    }

    /** Advances simulated time by exactly one minute. */
    public void advance() {
        if (!isSet()) {
            throw new IllegalStateException(
                "Set the simulation start time before advancing the clock."
            );
        }

        currentTime = currentTime.plusMinutes(1);
    }

    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public boolean isSet() {
        return currentTime != null;
    }

    /** Returns elapsed simulated minutes, or zero before initialization. */
    public long getElapsedMinutes() {
        if (!isSet()) {
            return 0;
        }

        return Duration.between(startTime, currentTime).toMinutes();
    }

    /** Restores a previously saved simulation clock. */
    public void restore(LocalDateTime start, LocalDateTime current) {
        if (start == null || current == null) {
            throw new IllegalArgumentException(
                "Saved start and current times cannot be null."
            );
        }

        if (current.isBefore(start)) {
            throw new IllegalArgumentException(
                "Current simulation time cannot be before its start time."
            );
        }

        this.startTime = start;
        this.currentTime = current;
    }
}

