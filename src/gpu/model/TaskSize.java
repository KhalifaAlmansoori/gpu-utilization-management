package gpu.model;

/**
 * The four task sizes. One workload unit takes one simulated GPU minute,
 * so the duration always equals the workload units.
 */
public enum TaskSize {
    SMALL("Small", 5),
    MEDIUM("Medium", 15),
    LARGE("Large", 30),
    VERY_LARGE("Very large", 60);

    private final String label;
    private final int workloadUnits;

    TaskSize(String label, int workloadUnits) {
        this.label = label;
        this.workloadUnits = workloadUnits;
    }

    public String getLabel() {
        return label;
    }

    public int getWorkloadUnits() {
        return workloadUnits;
    }

    /** Duration in minutes; equals the workload units. */
    public int getDurationMinutes() {
        return workloadUnits;
    }

    /** Accepts "LARGE", "Large", "Very large" or "VERY_LARGE". */
    public static TaskSize parse(String text) {
        if (text != null) {
            String t = text.trim().replace('_', ' ');
            for (TaskSize size : values()) {
                if (size.label.equalsIgnoreCase(t)) {
                    return size;
                }
            }
        }
        throw new IllegalArgumentException("unknown task size \"" + text + "\"");
    }

    /** Text for combo boxes, e.g. "Large (30 units)". */
    @Override
    public String toString() {
        return label + " (" + workloadUnits + " units)";
    }
}
