package gpu.ui;

import gpu.logic.*;
import gpu.model.*;
import gpu.storage.*;
import java.io.*;
import java.time.*;

/**
 * Shares the existing account service, scheduler and simulation clock.
 * Each action calls the relevant service once, then saves and records a status message.
 * This class never creates a screen. Main reads the requested destination and opens it.
 * It can therefore be understood and compiled before any of the screen classes.
 */
public class AppController {
    private DataStore store;
    private AccountService accounts;
    private Scheduler scheduler;
    private User signedIn;
    private boolean unsavedChanges;
    private String status = "Ready.";
    private String screen = "login";
    private int view;

    public AppController(DataStore store) throws IOException {
        this.store = store;
        DataStore.State restored = store.load();
        accounts = restored.accounts();
        scheduler = restored.scheduler();
    }

    public AccountService accounts() {
        return accounts;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public SimulationClock clock() {
        return scheduler.getClock();
    }

    public String status() {
        return status;
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    public User signedIn() {
        return signedIn;
    }

    /** A destination name, rather than a reference to a particular screen class. */
    public String screen() {
        return screen;
    }

    /** Student views are 0..2; administrator views are 0..3. */
    public int view() {
        return view;
    }

    public void showRegistration() {
        screen = "registration";
        view = 0;
    }

    public void showLogin() {
        screen = "login";
        view = 0;
    }

    public void openSession(User user) {
        signedIn = user;
        status = "Signed in as " + user.getFullName() + ".";
        if (user instanceof Student) {
            showStudent(0);
        } else if (!clock().isSet()) {
            showClockSetup();
        } else {
            showAdmin(0);
        }
    }

    public void showStudent(int view) {
        if (!(signedIn instanceof Student)) {
            report("Please log in as a student.");
            return;
        }
        if (view < 0 || view > 2) {
            report("Choose a student view from 0 to 2.");
            return;
        }
        screen = "student";
        this.view = view;
    }

    public void showAdmin(int view) {
        if (!(signedIn instanceof Administrator)) {
            report("Please log in as the administrator.");
            return;
        }
        if (view < 0 || view > 3) {
            report("Choose an administrator view from 0 to 3.");
            return;
        }
        screen = "admin";
        this.view = view;
    }

    public void showClockSetup() {
        if (!(signedIn instanceof Administrator)) {
            report("Only the administrator can set the clock.");
        } else if (clock().isSet()) {
            report("The simulation clock has already been set.");
        } else {
            screen = "clock";
            view = 0;
        }
    }

    public void report(String message) {
        status = message;
        if (unsavedChanges) {
            status += " Changes are not saved. Use Save to retry.";
        }
    }

    private void changed(String message) {
        unsavedChanges = true;
        saveChanges(message);
    }

    public boolean save() {
        return saveChanges("Application saved.");
    }

    private boolean saveChanges(String message) {
        try {
            store.save(accounts, scheduler);
            unsavedChanges = false;
            status = message;
            return true;
        } catch (IOException exception) {
            status = "Save failed: " + exception.getMessage()
                    + " Changes remain in this session. Use Save to retry.";
            return false;
        }
    }

    public void logout() {
        if (unsavedChanges && !save()) {
            return;
        }
        signedIn = null;
        status = "Logged out.";
        showLogin();
    }

    public void close() {
        if (!unsavedChanges || save()) {
            System.exit(0);
        }
    }

    public boolean registerStudent(String id, String name, String email, String phone, String password) {
        try {
            accounts.registerStudent(id, name, email, phone, password);
            changed("Registration submitted. Wait for administrator approval.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean initializeClock(String text) {
        try {
            // Compare with the formatted input to reject impossible dates such as 31/02.
            LocalDateTime start = TimeFormat.parse(text);
            if (start == null || !TimeFormat.format(start).equals(text.trim())) {
                throw new IllegalArgumentException("Enter a valid date as dd/MM/yyyy HH:mm.");
            }
            clock().setStartTime(start);
            changed("Simulation clock set.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean approveRequest(String id, int minutes) {
        try {
            accounts.approveRequest(id, minutes);
            changed("Registration approved for " + id + ".");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean rejectRequest(String id) {
        try {
            accounts.rejectRequest(id);
            changed("Registration rejected for " + id + ".");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean grantTime(String id, int minutes, String reason) {
        try {
            accounts.grantAdditionalTime(id, minutes, reason, clock().getCurrentTime());
            changed("Additional time granted to " + id + ".");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean submitJob(Student student, String title, String application,
            String description, TaskSize size) {
        try {
            Job job = scheduler.submitJob(student.getAllowance(), title, application, description, size);
            changed("Job " + job.getJobId() + " submitted.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean cancelJob(String jobId, String actor, String reason) {
        try {
            scheduler.cancelJob(jobId, actor, reason);
            changed("Job " + jobId + " cancelled.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }

    public boolean advanceMinute() {
        try {
            scheduler.advanceMinute();
            changed("Simulation advanced by one minute.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report(exception.getMessage());
            return false;
        }
    }
}
