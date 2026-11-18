
package gpu.logic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gpu.model.AccessRequest;
import gpu.model.Administrator;
import gpu.model.Student;
import gpu.model.TimeAllowance;
import gpu.model.User;

/**
 * Manages student registration, account approval,
 * login and additional GPU time.
 */
public class AccountService {

    private final Administrator administrator;
    private final List<Student> students;
    private final List<AccessRequest> requests;

    public AccountService() {
        this.administrator = new Administrator();
        this.students = new ArrayList<>();
        this.requests = new ArrayList<>();
    }

    /**
     * Submits a new student registration request.
     */
    public AccessRequest registerStudent(
            String universityId,
            String fullName,
            String email,
            String phone,
            String password) {

        requireText(universityId, "University ID");
        requireText(fullName, "Full name");
        requireText(email, "Email");
        requireText(phone, "Phone number");
        requireText(password, "Password");

        universityId = universityId.trim();
        fullName = fullName.trim();
        email = email.trim();
        phone = phone.trim();

        validateEmail(email);
        validatePhone(phone);

        if (isIdAlreadyUsed(universityId)) {
            throw new IllegalStateException(
                "This university ID is already registered or has a pending request."
            );
        }

        AccessRequest request = new AccessRequest(
            universityId,
            fullName,
            email,
            phone,
            password
        );

        requests.add(request);

        return request;
    }

    /**
     * Checks whether a university ID belongs to
     * an existing student or a pending request.
     */
    public boolean isIdAlreadyUsed(String universityId) {

        if (universityId == null || universityId.isBlank()) {
            return false;
        }

        String id = universityId.trim();

        for (Student student : students) {
            if (student.getUniversityId().equals(id)) {
                return true;
            }
        }

        for (AccessRequest request : requests) {
            if (request.getUniversityId().equals(id)
                    && request.isPending()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Approves a pending registration request.
     */
    public Student approveRequest(String universityId, int minutes) {

        if (minutes <= 0) {
            throw new IllegalArgumentException(
                "Initial GPU minutes must be positive."
            );
        }

        AccessRequest request = findPendingRequest(universityId);

        if (request == null) {
            throw new IllegalArgumentException(
                "No pending request was found for this university ID."
            );
        }

        for (Student student : students) {
            if (student.getUniversityId()
                    .equals(request.getUniversityId())) {
                throw new IllegalStateException(
                    "A student with this university ID already exists."
                );
            }
        }

        Student student = administrator.approve(request, minutes);

        students.add(student);

        return student;
    }

    /**
     * Approves a request with the default 100 GPU minutes.
     */
    public Student approveRequest(String universityId) {
        return approveRequest(
            universityId,
            TimeAllowance.DEFAULT_MINUTES
        );
    }

    /**
     * Rejects a pending registration request.
     */
    public AccessRequest rejectRequest(String universityId) {

        AccessRequest request = findPendingRequest(universityId);

        if (request == null) {
            throw new IllegalArgumentException(
                "No pending request was found for this university ID."
            );
        }

        administrator.reject(request);

        return request;
    }

    /**
     * Authenticates an administrator or approved student.
     * Returns null when the username or password is incorrect.
     */
    public User authenticate(String username, String password) {

        if (username == null || password == null) {
            return null;
        }

        if (administrator.getUsername().equals(username.trim())
                && administrator.checkPassword(password)) {
            return administrator;
        }

        Student student = getStudentById(username.trim());

        if (student != null && student.checkPassword(password)) {
            return student;
        }

        return null;
    }

    /**
     * Finds an approved student using university ID.
     */
    public Student getStudentById(String universityId) {

        if (universityId == null) {
            return null;
        }

        String id = universityId.trim();

        for (Student student : students) {
            if (student.getUniversityId().equals(id)) {
                return student;
            }
        }

        return null;
    }

    /**
     * Finds a pending registration request.
     */
    public AccessRequest findPendingRequest(String universityId) {

        if (universityId == null) {
            return null;
        }

        String id = universityId.trim();

        for (AccessRequest request : requests) {
            if (request.getUniversityId().equals(id)
                    && request.isPending()) {
                return request;
            }
        }

        return null;
    }

    /**
     * Returns all approved students.
     */
    public List<Student> getStudents() {
        return Collections.unmodifiableList(
            new ArrayList<>(students)
        );
    }

    /**
     * Returns all registration requests, including processed requests.
     */
    public List<AccessRequest> getRequests() {
        return Collections.unmodifiableList(
            new ArrayList<>(requests)
        );
    }

    /**
     * Returns only requests awaiting administrator approval.
     */
    public List<AccessRequest> getPendingRequests() {

        List<AccessRequest> pending = new ArrayList<>();

        for (AccessRequest request : requests) {
            if (request.isPending()) {
                pending.add(request);
            }
        }

        return Collections.unmodifiableList(pending);
    }

    public Administrator getAdministrator() {
        return administrator;
    }

    /**
     * Gives additional GPU minutes to an approved student.
     */
    public TimeAllowance.Grant grantAdditionalTime(
            String universityId,
            int minutes,
            String reason,
            LocalDateTime simulatedTime) {

        Student student = getStudentById(universityId);

        if (student == null) {
            throw new IllegalArgumentException(
                "Student account was not found."
            );
        }

        return administrator.grantTime(
            student,
            minutes,
            reason,
            simulatedTime
        );
    }

    /**
     * Restores a student loaded from saved data.
     */
    public void restoreStudent(Student student) {

        if (student == null) {
            throw new IllegalArgumentException(
                "Student is required."
            );
        }

        if (getStudentById(student.getUniversityId()) != null) {
            throw new IllegalStateException(
                "Duplicate student university ID."
            );
        }

        if (findPendingRequest(student.getUniversityId()) != null) {
            throw new IllegalStateException(
                "A pending request already uses this university ID."
            );
        }

        students.add(student);
    }

    /**
     * Restores a registration request loaded from saved data.
     */
    public void restoreRequest(AccessRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                "Registration request is required."
            );
        }

        if (request.isPending()
                && (getStudentById(request.getUniversityId()) != null
                || findPendingRequest(request.getUniversityId()) != null)) {
            throw new IllegalStateException(
                "A student or pending request already uses this university ID."
            );
        }

        requests.add(request);
    }

    private void validateEmail(String email) {

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                "Please enter a valid email address."
            );
        }
    }

    private void validatePhone(String phone) {

        if (!phone.matches("^\\+?[0-9() -]+$")) {
            throw new IllegalArgumentException(
                "Please enter a valid phone number."
            );
        }

        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.length() < 7 || digits.length() > 15) {
            throw new IllegalArgumentException(
                "Phone number must contain between 7 and 15 digits."
            );
        }
    }

    private void requireText(String value, String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " cannot be empty."
            );
        }
    }
}
