package gpu.model;

/**
 * Abstract base class for everyone who can sign in (INHERITANCE: Student and Administrator extend it).
 * The login code keeps a {@code List<User>} and calls the same methods on both kinds
 * (POLYMORPHISM): checkPassword() is shared, getRole() behaves differently in each subclass.
 */
public abstract class User {

    private final String username;
    private final String fullName;
    private final String password;

    protected User(String username, String fullName, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        this.username = username.trim();
        this.fullName = fullName.trim();
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    /** True if the typed password matches. The password itself is never exposed to the GUI. */
    public boolean checkPassword(String attempt) {
        return password.equals(attempt);
    }

    /** "Administrator" or "Student". Each subclass answers differently. */
    public abstract String getRole();

    @Override
    public String toString() {
        return getRole() + " " + fullName + " (" + username + ")";
    }
}
