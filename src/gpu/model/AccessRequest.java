package gpu.model;

import java.util.List;

/**
 * A registration request. Pending until the administrator approves or rejects it;
 * after that it is kept as history and can never be processed again.
 * <p>
 * No timestamp is stored: students may register before the simulation clock has been set.
 */
public class AccessRequest implements Storable {

    private final String universityId;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String password;
    private RequestStatus status = RequestStatus.PENDING;

    public AccessRequest(String universityId, String fullName, String email, String phone, String password) {
        requireText(universityId, "University ID");
        requireText(fullName, "Name");
        requireText(email, "Email");
        requireText(phone, "Phone number");
        requireText(password, "Password");
        this.universityId = universityId.trim();
        this.fullName = fullName.trim();
        this.email = email.trim();
        this.phone = phone.trim();
        this.password = password;
    }

    public String getUniversityId() {
        return universityId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }

    /** Pending -> Approved. A request cannot be approved twice. */
    public void approve() {
        requirePending("approved");
        status = RequestStatus.APPROVED;
    }

    /** Pending -> Rejected. */
    public void reject() {
        requirePending("rejected");
        status = RequestStatus.REJECTED;
    }

    /** Builds the student account for an approved request (username = university ID). */
    public Student toStudent(int initialMinutes) {
        if (status != RequestStatus.APPROVED) {
            throw new IllegalStateException("Only an approved request can become an account.");
        }
        return new Student(universityId, fullName, email, phone, password, initialMinutes);
    }

    @Override
    public String toString() {
        return universityId + " " + fullName + " [" + status + "]";
    }

    @Override
    public String toLine() {
        return Storable.join(universityId, fullName, email, phone, password, status.name());
    }

    public static AccessRequest fromLine(String line) {
        List<String> f = Storable.split(line);
        if (f.size() != 6) {
            throw new IllegalArgumentException("expected 6 fields but found " + f.size());
        }
        AccessRequest request = new AccessRequest(f.get(0), f.get(1), f.get(2), f.get(3), f.get(4));
        request.status = RequestStatus.parse(f.get(5));
        return request;
    }

    private void requirePending(String action) {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("This request was already " + status.getLabel().toLowerCase()
                    + " and cannot be " + action + " again.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be empty.");
        }
    }
}
