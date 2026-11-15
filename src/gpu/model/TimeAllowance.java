package gpu.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A student's GPU-minute balance.
 * <pre>Available = Allocated - Used - Reserved</pre>
 * <p>
 * ENCAPSULATION: the three numbers are private. They only change through reserve(), release(),
 * charge(), settle(), grant() and setAllocated(), and every method refuses a change that would
 * make the balance negative. No other class can break the rule.
 */
public class TimeAllowance {

    /** Minutes given to a student when the administrator approves the account. */
    public static final int DEFAULT_MINUTES = 100;

    private final String ownerId;
    private int allocated;
    private int used;
    private int reserved;
    private final List<Grant> grants = new ArrayList<>();

    /** New allowance with nothing used or reserved. */
    public TimeAllowance(String ownerId, int allocated) {
        this(ownerId, allocated, 0, 0);
    }

    /** Rebuilds an allowance from saved numbers. */
    public TimeAllowance(String ownerId, int allocated, int used, int reserved) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Allowance owner is required.");
        }
        if (allocated < 0 || used < 0 || reserved < 0) {
            throw new IllegalArgumentException("Allowance values cannot be negative.");
        }
        if (used + reserved > allocated) {
            throw new IllegalArgumentException("Used + reserved (" + (used + reserved)
                    + ") exceeds allocated (" + allocated + ").");
        }
        this.ownerId = ownerId;
        this.allocated = allocated;
        this.used = used;
        this.reserved = reserved;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getAllocated() {
        return allocated;
    }

    public int getUsed() {
        return used;
    }

    public int getReserved() {
        return reserved;
    }

    /** Allocated - Used - Reserved. Never negative. */
    public int available() {
        return allocated - used - reserved;
    }

    /** True if a job of this many minutes fits. An exact match is accepted. */
    public boolean canReserve(int minutes) {
        return minutes > 0 && minutes <= available();
    }

    /** Reserves the full duration of an accepted job. */
    public void reserve(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be a positive whole number.");
        }
        if (minutes > available()) {
            throw new IllegalStateException("This job needs " + minutes + " min, but you have only "
                    + available() + " min available.");
        }
        reserved += minutes;
    }

    /** Gives back a reservation without charging (job cancelled while queued). */
    public void release(int minutes) {
        if (minutes < 0 || minutes > reserved) {
            throw new IllegalStateException("Cannot release " + minutes + " min; only " + reserved + " reserved.");
        }
        reserved -= minutes;
    }

    /** Adds minutes to Used. Refuses if it would make the balance negative. */
    public void charge(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Cannot charge a negative amount.");
        }
        if (minutes > available()) {
            throw new IllegalStateException("Cannot charge " + minutes + " min; only " + available() + " available.");
        }
        used += minutes;
    }

    /**
     * Settles one finished job: releases its whole reservation and charges the minutes it really ran.
     * Completed job: settle(duration, duration). Running job cancelled after 12 min: settle(30, 12).
     * Nothing changes if the arguments are invalid.
     */
    public void settle(int reservedMinutes, int chargedMinutes) {
        if (chargedMinutes < 0 || chargedMinutes > reservedMinutes) {
            throw new IllegalArgumentException("Charge (" + chargedMinutes
                    + ") must be between 0 and the reservation (" + reservedMinutes + ").");
        }
        if (reservedMinutes > reserved) {
            throw new IllegalStateException("Only " + reserved + " min reserved, cannot settle " + reservedMinutes + ".");
        }
        reserved -= reservedMinutes;
        used += chargedMinutes;
    }

    /** Administrator grants extra time. Increases Allocated and records who, when and why. */
    public Grant grant(int minutes, String reason, String grantedBy, LocalDateTime when) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be a positive whole number.");
        }
        Grant grant = new Grant(ownerId, minutes, reason, grantedBy, when);
        allocated += minutes;
        grants.add(grant);
        return grant;
    }

    /** Administrator changes the allocation (e.g. approving with something other than 100). */
    public void setAllocated(int newAllocated) {
        if (newAllocated < used + reserved) {
            throw new IllegalArgumentException("Allocated time cannot be below used + reserved ("
                    + (used + reserved) + " min).");
        }
        allocated = newAllocated;
    }

    /** Recorded grants, oldest first. */
    public List<Grant> getGrants() {
        return Collections.unmodifiableList(grants);
    }

    /** Re-attaches a saved grant when loading. Does not change Allocated (it was saved already). */
    public void restoreGrant(Grant grant) {
        if (!ownerId.equals(grant.getOwnerId())) {
            throw new IllegalArgumentException("Grant belongs to " + grant.getOwnerId() + ", not " + ownerId + ".");
        }
        grants.add(grant);
    }

    @Override
    public String toString() {
        return "allocated=" + allocated + " used=" + used + " reserved=" + reserved + " available=" + available();
    }

    /** One recorded grant of extra time: who, when (simulated time) and why. */
    public static class Grant implements Storable {
        private final String ownerId;
        private final int minutes;
        private final String reason;
        private final String grantedBy;
        private final LocalDateTime time;

        public Grant(String ownerId, int minutes, String reason, String grantedBy, LocalDateTime time) {
            if (minutes <= 0) {
                throw new IllegalArgumentException("Minutes must be a positive whole number.");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A reason is required.");
            }
            if (grantedBy == null || grantedBy.isBlank()) {
                throw new IllegalArgumentException("The administrator name is required.");
            }
            if (time == null) {
                throw new IllegalArgumentException("The simulated time is required.");
            }
            this.ownerId = ownerId;
            this.minutes = minutes;
            this.reason = reason.trim();
            this.grantedBy = grantedBy;
            this.time = time;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public int getMinutes() {
            return minutes;
        }

        public String getReason() {
            return reason;
        }

        public String getGrantedBy() {
            return grantedBy;
        }

        public LocalDateTime getTime() {
            return time;
        }

        @Override
        public String toLine() {
            return Storable.join(ownerId, String.valueOf(minutes), reason, grantedBy, TimeFormat.format(time));
        }

        public static Grant fromLine(String line) {
            List<String> f = Storable.split(line);
            if (f.size() != 5) {
                throw new IllegalArgumentException("expected 5 fields but found " + f.size());
            }
            return new Grant(f.get(0), Integer.parseInt(f.get(1)), f.get(2), f.get(3), TimeFormat.parse(f.get(4)));
        }
    }
}
