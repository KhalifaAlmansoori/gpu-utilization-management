package gpu.ui;

import gpu.model.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Three student views: 0 = Home, 1 = Submit job, 2 = My jobs. */
public class StudentPanel extends JPanel {
    private AppController app;
    private ActionListener navigation;
    private Student student;
    private int view;
    private int page;
    private int grantIndex = -1;
    private String selectedJobId;
    private TaskSize selectedSize = TaskSize.SMALL;
    private String searchText = "";
    private JobStatus statusFilter;

    private JTextField clock = displayField(40);
    private JLabel message = new JLabel();
    private JTextField[] profile = displayFields(8);
    private JTextField[] records = displayFields(5);
    private JTextField pageInfo = displayField(40);
    private JTextField recordNumber = new JTextField(5);
    private JTextField[] grantDetails = displayFields(3);
    private JTextField title = new JTextField(30);
    private JTextField application = new JTextField(30);
    private JTextField description = new JTextField(50);
    private JTextField taskSize = displayField(30);
    private JTextField[] jobDetails = displayFields(12);
    private JTextField cancellationReason = new JTextField(40);
    private JTextField search = new JTextField(25);

    private JButton reviewSubmission;
    private JButton cancelSelected;

    public StudentPanel(AppController app, Student student) {
        this(app, student, 0, null);
    }

    public StudentPanel(AppController app, Student student, int view) {
        this(app, student, view, null);
    }

    /** Main supplies the listener that opens the controller's requested screen. */
    public StudentPanel(AppController app, Student student, int view, ActionListener navigation) {
        this.app = app;
        this.navigation = navigation;
        this.student = student;
        this.view = view;
        setLayout(new BorderLayout());
        JPanel heading = new JPanel();
        heading.setLayout(new BorderLayout());
        String[] names = {"Student home", "Submit a job", "My jobs"};
        heading.add(new JLabel(names[view], SwingConstants.CENTER), BorderLayout.PAGE_START);
        heading.add(fieldRow("Simulation time", clock), BorderLayout.CENTER);
        message.setForeground(Color.RED);
        heading.add(message, BorderLayout.PAGE_END);
        add(heading, BorderLayout.PAGE_START);
        if (view == 0) {
            add(buildHome(), BorderLayout.CENTER);
        } else if (view == 1) {
            add(buildSubmission(), BorderLayout.CENTER);
        } else {
            add(buildJobs(), BorderLayout.CENTER);
        }
        JPanel navigationBar = new JPanel();
        String[] actions = {"Home", "Submit job", "My jobs", "Save", "Log out", "Exit"};
        for (String action : actions) {
            navigationBar.add(button(action));
        }
        add(navigationBar, BorderLayout.PAGE_END);
        refresh();
    }

    /** Main reuses these panels, so a new student must receive clean forms/selections. */
    public void setStudent(Student nextStudent) {
        student = nextStudent;
        page = 0;
        clearSelection();
        cancellationReason.setText("");
        search.setText("");
        searchText = "";
        statusFilter = null;
        clearSubmission();
        refresh();
    }

    // These helpers repeat basic Swing setup within this screen only.
    private JTextField displayField(int columns) {
        JTextField field = new JTextField(columns);
        field.setEditable(false);
        return field;
    }

    private JTextField[] displayFields(int count) {
        JTextField[] fields = new JTextField[count];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = displayField(40);
        }
        return fields;
    }

    private JPanel fieldRow(String label, JTextField field) {
        JPanel row = new JPanel();
        row.setLayout(new GridLayout(1, 2));
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private JPanel fieldRows(String[] labels, JTextField[] fields) {
        JPanel rows = new JPanel();
        rows.setLayout(new GridLayout(labels.length, 1));
        for (int i = 0; i < labels.length; i++) {
            rows.add(fieldRow(labels[i], fields[i]));
        }
        return rows;
    }

    private JButton button(String label) {
        return button(label, new ButtonHandler(label));
    }

    private JButton button(String label, ActionListener listener) {
        JButton button = new JButton(label);
        button.addActionListener(listener);
        return button;
    }

    private JPanel buildHome() {
        JPanel home = new JPanel();
        home.setLayout(new BorderLayout());
        home.add(fieldRows(new String[] {"University ID", "Full name", "Email", "Phone",
                "Allocated minutes", "Used minutes", "Reserved minutes", "Available minutes"},
                profile), BorderLayout.PAGE_START);
        home.add(recordList("Additional time grants"), BorderLayout.CENTER);
        home.add(fieldRows(new String[] {"Grant minutes", "Granted by / time", "Grant reason"},
                grantDetails), BorderLayout.PAGE_END);
        return home;
    }

    private JPanel buildSubmission() {
        JPanel submissionForm = new JPanel();
        submissionForm.setLayout(new GridLayout(7, 1));
        submissionForm.add(fieldRow("Job title", title));
        submissionForm.add(fieldRow("Application name", application));
        submissionForm.add(fieldRow("Description", description));
        JPanel sizes = new JPanel();
        TaskSize[] choices = {TaskSize.SMALL, TaskSize.MEDIUM, TaskSize.LARGE, TaskSize.VERY_LARGE};
        for (TaskSize size : choices) {
            sizes.add(button(size.toString(), new TaskSizeHandler(size)));
        }
        submissionForm.add(sizes);
        submissionForm.add(fieldRow("Selected size", taskSize));
        submissionForm.add(new JLabel("One workload unit takes one simulated GPU minute.", SwingConstants.CENTER));
        reviewSubmission = button("Review job");
        submissionForm.add(reviewSubmission);
        return submissionForm;
    }

    private JPanel buildJobs() {
        JPanel jobs = new JPanel();
        jobs.setLayout(new BorderLayout());
        JPanel history = new JPanel();
        history.setLayout(new BorderLayout());
        JPanel filters = new JPanel();
        filters.setLayout(new GridLayout(2, 1));
        JPanel searchRow = new JPanel();
        searchRow.add(new JLabel("Job ID or title"));
        searchRow.add(search);
        searchRow.add(button("Search"));
        searchRow.add(button("Clear search"));
        filters.add(searchRow);
        JPanel statuses = new JPanel();
        statuses.add(new JLabel("Status"));
        statuses.add(button("All", new StatusHandler(null)));
        JobStatus[] choices = {JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.COMPLETED, JobStatus.CANCELLED};
        for (JobStatus status : choices) {
            statuses.add(button(status.toString(), new StatusHandler(status)));
        }
        filters.add(statuses);
        history.add(filters, BorderLayout.PAGE_START);
        history.add(recordList("Your job history"), BorderLayout.CENTER);
        jobs.add(history, BorderLayout.PAGE_START);
        jobs.add(fieldRows(new String[] {"Job / student ID", "Title", "Application", "Description",
                "Size / duration", "Status / GPU", "Submitted", "Started / finished",
                "Predicted start / finish", "Queue position / wait", "Runtime / remaining / charged",
                "Cancellation time / actor / reason"}, jobDetails), BorderLayout.CENTER);
        JPanel cancellationForm = new JPanel();
        cancellationForm.add(new JLabel("Cancellation reason"));
        cancellationForm.add(cancellationReason);
        cancelSelected = button("Cancel selected job");
        cancellationForm.add(cancelSelected);
        jobs.add(cancellationForm, BorderLayout.PAGE_END);
        return jobs;
    }

    private JPanel recordList(String heading) {
        JPanel list = new JPanel();
        list.setLayout(new GridLayout(8, 1));
        list.add(new JLabel(heading, SwingConstants.CENTER));
        for (JTextField record : records) {
            list.add(record);
        }
        list.add(pageInfo);
        JPanel controls = new JPanel();
        controls.add(button("Previous"));
        controls.add(button("Next"));
        controls.add(new JLabel("Record number"));
        controls.add(recordNumber);
        controls.add(button("View record"));
        list.add(controls);
        return list;
    }

    /** Only this student's jobs enter the filtered list and its record numbering. */
    private ArrayList<Job> studentJobs() {
        ArrayList<Job> jobs = new ArrayList<Job>();
        for (Job job : app.scheduler().getAllJobs()) {
            if (job.getStudentId().equals(student.getUniversityId())
                    && (containsText(job.getJobId(), searchText) || containsText(job.getTitle(), searchText))
                    && (statusFilter == null || job.getStatus() == statusFilter)) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Search each possible starting position using the taught String methods. */
    private boolean containsText(String text, String query) {
        text = text.toLowerCase();
        query = query.toLowerCase();
        for (int i = 0; i <= text.length() - query.length(); i++) {
            if (text.substring(i, i + query.length()).equals(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > ' ') {
                return true;
            }
        }
        return false;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : TimeFormat.format(time);
    }

    private void clearFields(JTextField[] fields) {
        for (JTextField field : fields) {
            field.setText("");
        }
    }

    /** Bound the five-record page, then clear unused rows before filling them. */
    private void preparePage(int count) {
        int pages = (count + 4) / 5;
        if (pages == 0) {
            pages = 1;
        }
        if (page < 0) {
            page = 0;
        } else if (page >= pages) {
            page = pages - 1;
        }
        clearFields(records);
        pageInfo.setText("Page " + (page + 1) + " of " + pages + " | " + count + " records");
    }

    public void refresh() {
        clock.setText(formatTime(app.clock().getCurrentTime()));
        message.setText(app.status());
        if (view == 0) {
            refreshHome();
        } else if (view == 1) {
            taskSize.setText(selectedSize + " / Available: " + student.getAllowance().available() + " min");
            boolean canSubmit = app.clock().isSet()
                    && student.getAllowance().available() >= selectedSize.getDurationMinutes();
            reviewSubmission.setEnabled(canSubmit);
        } else {
            refreshJobs();
        }
    }

    private void refreshHome() {
        TimeAllowance allowance = student.getAllowance();
        String[] values = {student.getUniversityId(), student.getFullName(), student.getEmail(),
                student.getPhone(), "" + allowance.getAllocated(), "" + allowance.getUsed(),
                "" + allowance.getReserved(), "" + allowance.available()};
        for (int i = 0; i < profile.length; i++) {
            profile[i].setText(values[i]);
        }
        List<TimeAllowance.Grant> grants = allowance.getGrants();
        preparePage(grants.size());
        for (int row = 0; row < records.length && page * 5 + row < grants.size(); row++) {
            int index = page * 5 + row;
            TimeAllowance.Grant grant = grants.get(index);
            records[row].setText((index + 1) + ". " + grant.getMinutes() + " min | "
                    + formatTime(grant.getTime()) + " | " + grant.getReason());
        }
        clearFields(grantDetails);
        if (grantIndex >= 0 && grantIndex < grants.size()) {
            TimeAllowance.Grant grant = grants.get(grantIndex);
            grantDetails[0].setText("" + grant.getMinutes());
            grantDetails[1].setText(grant.getGrantedBy() + " / " + formatTime(grant.getTime()));
            grantDetails[2].setText(grant.getReason());
        }
    }

    private void refreshJobs() {
        ArrayList<Job> jobs = studentJobs();
        preparePage(jobs.size());
        pageInfo.setText(pageInfo.getText() + " | " + (statusFilter == null ? "All statuses" : statusFilter));
        Job selected = null;
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (i >= page * 5 && i < page * 5 + records.length) {
                records[i - page * 5].setText((i + 1) + ". " + job.getJobId() + " | "
                        + job.getTitle() + " | " + job.getStatus());
            }
            if (job.getJobId().equals(selectedJobId)) {
                selected = job;
            }
        }
        showJobDetails(selected);
        cancelSelected.setEnabled(selected != null && selected.isCancellable());
    }

    /** The screen reads results; all scheduling and charging remain in Scheduler. */
    private void showJobDetails(Job job) {
        clearFields(jobDetails);
        if (job == null) {
            return;
        }
        jobDetails[0].setText(job.getJobId() + " / " + job.getStudentId());
        jobDetails[1].setText(job.getTitle());
        jobDetails[2].setText(job.getApplicationName());
        jobDetails[3].setText(job.getDescription());
        jobDetails[4].setText(job.getTaskSize() + " / " + job.getEstimatedDuration() + " min");
        jobDetails[5].setText(job.getStatus() + " / GPU "
                + (job.getAssignedGpuId() == null ? "-" : job.getAssignedGpuId()));
        jobDetails[6].setText(formatTime(job.getSubmissionTime()));
        jobDetails[7].setText(formatTime(job.getStartTime()) + " / " + formatTime(job.getFinishTime()));
        jobDetails[8].setText(formatTime(app.scheduler().getPredictedStartTime(job.getJobId())) + " / "
                + formatTime(app.scheduler().getPredictedFinishTime(job.getJobId())));
        int position = app.scheduler().getQueuePosition(job.getJobId());
        long wait = app.scheduler().getPredictedWaitMinutes(job.getJobId());
        jobDetails[9].setText((position < 0 ? "Not queued" : "Position " + position)
                + " / " + (wait < 0 ? "No wait recorded" : wait + " min"));
        LocalDateTime now = app.clock().getCurrentTime();
        jobDetails[10].setText(job.getRuntimeMinutes(now) + " / " + job.getRemainingRuntime(now)
                + " / " + job.getChargedMinutes() + " min");
        jobDetails[11].setText(job.getCancelTime() == null ? "-" : formatTime(job.getCancelTime())
                + " / " + job.getCancelledBy() + " / " + job.getCancelReason());
    }

    private void clearSelection() {
        grantIndex = -1;
        selectedJobId = null;
        recordNumber.setText("");
    }

    private void clearSubmission() {
        title.setText("");
        application.setText("");
        description.setText("");
        selectedSize = TaskSize.SMALL;
    }

    private void selectRecord() {
        ArrayList<Job> jobs = studentJobs();
        int count = view == 0 ? student.getAllowance().getGrants().size() : jobs.size();
        if (count == 0) {
            throw new IllegalArgumentException("There are no records to select.");
        }
        int number;
        try {
            number = Integer.parseInt(recordNumber.getText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole record number.");
        }
        if (number < 1 || number > count) {
            throw new IllegalArgumentException("Choose a record number from 1 to " + count + ".");
        }
        // The displayed record numbers start at 1; list indexes start at 0.
        if (view == 0) {
            grantIndex = number - 1;
        } else {
            selectedJobId = jobs.get(number - 1).getJobId();
        }
    }

    private void reviewSubmission() {
        if (!hasText(title.getText()) || !hasText(application.getText()) || !hasText(description.getText())) {
            app.report("Enter the job title, application and description first.");
        } else {
            int duration = selectedSize.getDurationMinutes();
            int answer = JOptionPane.showConfirmDialog(this,
                    "Submit " + title.getText() + " as a " + selectedSize.getLabel() + " job?\n"
                    + "Duration: " + duration + " min\nAvailable after submitting: "
                    + (student.getAllowance().available() - duration) + " min",
                    "Confirm job", JOptionPane.YES_NO_OPTION);
            // Only Yes submits; No or closing the dialog leaves the form unchanged.
            if (answer == JOptionPane.YES_OPTION && app.submitJob(student, title.getText(),
                    application.getText(), description.getText(), selectedSize)) {
                clearSubmission();
            }
        }
    }

    private void reviewCancellation() {
        Job job = app.scheduler().getJobById(selectedJobId);
        if (job == null || !job.getStudentId().equals(student.getUniversityId())) {
            app.report("Choose a job with View record first.");
        } else if (!job.isCancellable()) {
            app.report("Only queued or running jobs can be cancelled.");
        } else if (!hasText(cancellationReason.getText())) {
            app.report("Enter a cancellation reason first.");
        } else {
            String reason = cancellationReason.getText();
            int charge = job.getRuntimeMinutes(app.clock().getCurrentTime());
            // The modal dialog blocks clock changes while this charge is reviewed.
            int answer = JOptionPane.showConfirmDialog(this,
                    "Cancel " + job.getJobId() + " (" + job.getStatus() + ")?\n"
                    + "Charge: " + charge + " min\nReturned to balance: "
                    + (job.getEstimatedDuration() - charge) + " min\nReason: " + reason,
                    "Cancel job", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                if (!job.isCancellable() || charge != job.getRuntimeMinutes(app.clock().getCurrentTime())) {
                    app.report("The job changed. Review cancellation again before confirming.");
                } else if (app.cancelJob(job.getJobId(), student.getUniversityId(), reason)) {
                    cancellationReason.setText("");
                }
            }
        }
    }

    /** Refresh first, then let Main handle navigation and refresh other open views. */
    private void updateScreen(ActionEvent event) {
        refresh();
        if (navigation != null) {
            navigation.actionPerformed(event);
        }
    }

    /** The button label identifies its action; the work stays in the methods above. */
    private class ButtonHandler implements ActionListener {
        private String action;

        public ButtonHandler(String action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent event) {
            try {
                if (action.equals("Home")) {
                    app.showStudent(0);
                } else if (action.equals("Submit job")) {
                    app.showStudent(1);
                } else if (action.equals("My jobs")) {
                    app.showStudent(2);
                } else if (action.equals("Save")) {
                    app.save();
                } else if (action.equals("Log out")) {
                    app.logout();
                } else if (action.equals("Exit")) {
                    app.close();
                } else if (action.equals("Previous") || action.equals("Next")) {
                    page += action.equals("Next") ? 1 : -1;
                    clearSelection();
                } else if (action.equals("View record")) {
                    selectRecord();
                } else if (action.equals("Search") || action.equals("Clear search")) {
                    if (action.equals("Clear search")) {
                        search.setText("");
                    }
                    searchText = search.getText();
                    page = 0;
                    clearSelection();
                } else if (action.equals("Review job")) {
                    reviewSubmission();
                } else if (action.equals("Cancel selected job")) {
                    reviewCancellation();
                }
            } catch (IllegalArgumentException exception) {
                app.report(exception.getMessage());
            } finally {
                updateScreen(event);
            }
        }
    }

    private class TaskSizeHandler implements ActionListener {
        private TaskSize size;

        public TaskSizeHandler(TaskSize size) {
            this.size = size;
        }

        public void actionPerformed(ActionEvent event) {
            selectedSize = size;
            updateScreen(event);
        }
    }

    private class StatusHandler implements ActionListener {
        private JobStatus status;

        public StatusHandler(JobStatus status) {
            this.status = status;
        }

        public void actionPerformed(ActionEvent event) {
            statusFilter = status;
            page = 0;
            clearSelection();
            updateScreen(event);
        }
    }
}
