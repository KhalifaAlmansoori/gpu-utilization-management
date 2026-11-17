package gpu.model;

import java.time.LocalDateTime;

/**
 * The single predefined administrator (admin / gpu2026). Not stored in any file.
 * Registry-level checks (no duplicate accounts) belong to AccountService in the logic package;
 * this class only enforces the rules of one request or one student.
 */
public class Administrator extends User {

    public static final String USERNAME = "admin";
    public static final String PASSWORD = "gpu2026";

    public Administrator() {
        super(USERNAME, "Administrator", PASSWORD);
    }

    @Override
    public String getRole() {
        return "Administrator";
    }

    /**
     * Approves a pending request and returns the new student account.
     *
     * @param minutes initial GPU minutes (default {@link TimeAllowance#DEFAULT_MINUTES}); must be positive
     * @throws IllegalStateException if the request was already approved or rejected
     */
    public Student approve(AccessRequest request, int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be a positive whole number.");
        }
        request.approve(); // throws if not pending; nothing changes in that case
        return request.toStudent(minutes);
    }

    /** Approves with the default 100 minutes. */
    public Student approve(AccessRequest request) {
        return approve(request, TimeAllowance.DEFAULT_MINUTES);
    }

    /** Rejects a pending request. No account is created. */
    public void reject(AccessRequest request) {
        request.reject();
    }

    /** Grants extra time to a student with a required reason; the grant is recorded. */
    public TimeAllowance.Grant grantTime(Student student, int minutes, String reason, LocalDateTime when) {
        return student.getAllowance().grant(minutes, reason, getUsername(), when);
    }
}
