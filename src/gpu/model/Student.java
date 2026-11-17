package gpu.model;

import java.util.List;

/**
 * An approved student account. The university ID is the username.
 * Owns exactly one {@link TimeAllowance}.
 */
public class Student extends User implements Storable {

    private final String email;
    private final String phone;
    private final String password; // kept only so the account can be saved
    private final TimeAllowance allowance;

    /** New account with an initial allowance (normally {@link TimeAllowance#DEFAULT_MINUTES}). */
    public Student(String universityId, String fullName, String email, String phone,
                   String password, int initialMinutes) {
        this(universityId, fullName, email, phone, password,
                new TimeAllowance(universityId == null ? "" : universityId.trim(), initialMinutes));
    }

    private Student(String universityId, String fullName, String email, String phone,
                    String password, TimeAllowance allowance) {
        super(universityId, fullName, password);
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.allowance = allowance;
    }

    public String getUniversityId() {
        return getUsername();
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public TimeAllowance getAllowance() {
        return allowance;
    }

    @Override
    public String getRole() {
        return "Student";
    }

    @Override
    public String toLine() {
        return Storable.join(getUniversityId(), getFullName(), email, phone, password,
                String.valueOf(allowance.getAllocated()),
                String.valueOf(allowance.getUsed()),
                String.valueOf(allowance.getReserved()));
    }

    /** Rebuilds a student, including balances, from a saved line. Grants are re-attached separately. */
    public static Student fromLine(String line) {
        List<String> f = Storable.split(line);
        if (f.size() != 8) {
            throw new IllegalArgumentException("expected 8 fields but found " + f.size());
        }
        String id = f.get(0).trim();
        TimeAllowance allowance = new TimeAllowance(id, Integer.parseInt(f.get(5)),
                Integer.parseInt(f.get(6)), Integer.parseInt(f.get(7)));
        return new Student(id, f.get(1), f.get(2), f.get(3), f.get(4), allowance);
    }
}
