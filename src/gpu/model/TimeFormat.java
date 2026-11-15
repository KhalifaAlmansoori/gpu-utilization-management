package gpu.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Single place that defines how simulated timestamps look: dd/MM/yyyy HH:mm (24-hour).
 * Used by the model for saving/loading and by the UI for display.
 */
public final class TimeFormat {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TimeFormat() {
    }

    /** Formats a time, or returns an empty string for null. */
    public static String format(LocalDateTime time) {
        return time == null ? "" : time.format(FORMAT);
    }

    /** Parses dd/MM/yyyy HH:mm. Returns null for null/blank text. */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("bad date \"" + text + "\" (expected dd/MM/yyyy HH:mm)");
        }
    }
}
