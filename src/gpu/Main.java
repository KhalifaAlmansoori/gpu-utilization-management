package gpu;

import gpu.model.*;
import gpu.storage.*;
import gpu.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

/**
 * Creates the windows and shows the destination requested by AppController.
 * Windows are reused; their displayed values are refreshed after every button action.
 */
public class Main {
    private AppController app;
    private JFrame activeWindow;
    private LoginFrame login;
    private RegisterPanel registration;
    private JFrame registrationWindow;
    private ClockSetupFrame clockSetup;
    private Student displayedStudent;
    // Student views: Home, Submit, My jobs. Admin views: Dashboard, Requests, Students, All jobs.
    private StudentPanel[] studentPanels = new StudentPanel[3];
    private JFrame[] studentWindows = new JFrame[3];
    private AdminDashboard[] adminPanels = new AdminDashboard[4];
    private JFrame[] adminWindows = new JFrame[4];
    private ActionListener navigation = new ScreenHandler();

    public Main(DataStore store) throws IOException {
        app = new AppController(store);
    }

    public static void main(String[] args) {
        try {
            Main application = new Main(new DataStore());
            application.start();
        } catch (IOException exception) {
            // Never replace unreadable saved information with an empty system.
            JPanel information = new JPanel();
            information.setLayout(new GridLayout(2, 1));
            information.add(new JLabel("Cannot load saved data. Check the data folder and saved file before retrying."));
            JLabel message = new JLabel(exception.getMessage());
            message.setForeground(Color.RED);
            information.add(message);
            JFrame errorWindow = createWindow("Unable to start", information, 1000, 180);
            errorWindow.setVisible(true);
        }
    }

    public void start() {
        refresh();
    }

    /** Open the requested window, reusing it on later visits. */
    public void refresh() {
        JFrame nextWindow;
        String destination = app.screen();
        if (!(app.signedIn() instanceof Student)) {
            displayedStudent = null;
        }
        if (destination.equals("login")) {
            // null means this window has not been created yet.
            if (login == null) {
                login = new LoginFrame(app, navigation);
            }
            if (activeWindow != login) {
                login.clearFields();
            }
            nextWindow = login;
        } else if (destination.equals("registration")) {
            if (registration == null) {
                registration = new RegisterPanel(app, navigation);
                registrationWindow = createWindow("Student registration", registration, 850, 340);
            }
            if (activeWindow != registrationWindow) {
                registration.clearFields();
            }
            nextWindow = registrationWindow;
        } else if (destination.equals("student")) {
            Student student = (Student) app.signedIn();
            // A new login must not reuse another student's inputs or selected records.
            if (displayedStudent != student) {
                for (int i = 0; i < studentPanels.length; i++) {
                    if (studentPanels[i] != null) {
                        studentPanels[i].setStudent(student);
                    }
                }
                displayedStudent = student;
            }
            int view = app.view();
            if (studentPanels[view] == null) {
                studentPanels[view] = new StudentPanel(app, student, view, navigation);
                int[] heights = {740, 460, 840};
                studentWindows[view] = createWindow("Student - GPU Utilization Management",
                        studentPanels[view], 1180, heights[view]);
            }
            nextWindow = studentWindows[view];
        } else if (destination.equals("admin")) {
            int view = app.view();
            if (adminPanels[view] == null) {
                adminPanels[view] = new AdminDashboard(app, view, navigation);
                int[] heights = {650, 680, 800, 860};
                adminWindows[view] = createWindow("Administrator - GPU Utilization Management",
                        adminPanels[view], 1180, heights[view]);
            }
            nextWindow = adminWindows[view];
        } else if (destination.equals("clock")) {
            if (clockSetup == null) {
                clockSetup = new ClockSetupFrame(app, navigation);
            }
            nextWindow = clockSetup;
        } else {
            throw new IllegalStateException("Unknown screen: " + destination);
        }
        if (activeWindow != null && activeWindow != nextWindow) {
            activeWindow.setVisible(false);
        }
        activeWindow = nextWindow;
        refreshScreens();
        activeWindow.setVisible(true);
    }

    /** Wraps a JPanel in the same basic JFrame layout used by the other screens. */
    private static JFrame createWindow(String title, JPanel panel, int width, int height) {
        JFrame window = new JFrame(title);
        Container pane = window.getContentPane();
        pane.setLayout(new BorderLayout());
        pane.add(panel, BorderLayout.CENTER);
        window.setSize(width, height);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return window;
    }

    /** Refresh after an action, including validation errors and failed saves. */
    private void refreshScreens() {
        if (login != null) {
            login.refresh();
        }
        if (registration != null) {
            registration.refresh();
        }
        if (clockSetup != null) {
            clockSetup.refresh();
        }
        for (int view = 0; view < studentPanels.length; view++) {
            if (studentPanels[view] != null) {
                studentPanels[view].refresh();
            }
        }
        for (int view = 0; view < adminPanels.length; view++) {
            if (adminPanels[view] != null) {
                adminPanels[view].refresh();
            }
        }
        // After a save failure, the title-bar X must not discard the unsaved session.
        if (app.hasUnsavedChanges()) {
            activeWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        } else {
            activeWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
    }

    /** The same named listener is passed to each screen using the lecture's interface. */
    private class ScreenHandler implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            refresh();
        }
    }
}
