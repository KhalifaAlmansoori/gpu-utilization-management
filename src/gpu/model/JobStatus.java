package gpu.model;

/**
 * Job life cycle: QUEUED -> RUNNING or CANCELLED; RUNNING -> COMPLETED or CANCELLED.
 * COMPLETED and CANCELLED are final.
 */
public enum JobStatus {
    QUEUED("Queued"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True for COMPLETED and CANCELLED: such a job can never change again. */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public static JobStatus parse(String text) {
        if (text != null) {
            for (JobStatus status : values()) {
                if (status.label.equalsIgnoreCase(text.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("unknown job status \"" + text + "\"");
    }

    @Override
    public String toString() {
        return label;
    }
}
