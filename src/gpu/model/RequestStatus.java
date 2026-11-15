package gpu.model;

/** Status of a registration request. PENDING -> APPROVED or REJECTED (final). */
public enum RequestStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RequestStatus parse(String text) {
        if (text != null) {
            for (RequestStatus status : values()) {
                if (status.label.equalsIgnoreCase(text.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("unknown request status \"" + text + "\"");
    }

    @Override
    public String toString() {
        return label;
    }
}
