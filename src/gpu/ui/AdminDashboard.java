package gpu.ui;

import gpu.model.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Displays GPUs, requests, students and jobs; services own all simulation changes. */
public class AdminDashboard extends JPanel {
    private AppController app;
    private ActionListener navigation;
    private int view;
    // These selections affect this screen only, not the stored records.
    private int page;
    private int grantIndex;
    private boolean pendingOnly = true;
    private boolean studentJobs;
    private String searchText = "";
    private String statusFilter = "All";
    private AccessRequest selectedRequest;
    private Student selectedStudent;
    private String selectedJobId;
    // Display fields are read-only; separate input fields collect the administrator's choices.
    private JTextField clock = display(40);
    private JLabel message = new JLabel(" ");
    private JTextField summary = display(80);
    private JTextField[] gpuRows = displays(4);
    private JTextField[] records = displays(5);
    private JTextField pageInfo = display(40);
    private JTextField recordNumber = new JTextField(5);
    private JTextField initialMinutes = new JTextField("100", 6);
    private JTextField extraMinutes = new JTextField("20", 6);
    private JTextField reason = new JTextField(35);
    private JTextField[] details = displays(12);
    private JButton historyButton = button("Show request history");
    private JButton advanceButton = button("Advance one minute");
    private JButton approveButton = button("Approve selected");
    private JButton rejectButton = button("Reject selected");
    private JButton grantButton = button("Grant additional time");
    private JButton studentJobsButton = button("View student's jobs");
    private JButton cancelButton = button("Cancel selected job");
    private JTextField search = new JTextField(20);
    private JTextField filterInfo = display(24);
    private JTextField cancellationReason = new JTextField(35);
    private JLabel searchLabel = new JLabel("University ID");
    private JLabel[] detailLabels = new JLabel[12];
    private JPanel statusButtons;
    private JPanel grantActions;
    private JPanel cancellationActions;
    private String[] studentLabels = {"University ID / name", "Email", "Phone", "Allocated minutes",
            "Used minutes", "Reserved minutes", "Available minutes", "Grant record",
            "Grant minutes / by / time", "Grant reason", "", ""};
    private String[] jobLabels = {"Job / student ID", "Title", "Application", "Description",
            "Size / duration", "Status / GPU", "Submitted", "Started / finished",
            "Predicted start / finish", "Queue position / wait", "Runtime / remaining / charged",
            "Cancellation time / actor / reason"};

    public AdminDashboard(AppController app) {
        this(app, 0, null);
    }

    /** View 0 is GPUs/queue, 1 requests, 2 students, and 3 all jobs. */
    public AdminDashboard(AppController app, int view) {
        this(app, view, null);
    }

    /** Main supplies its listener when the separate screens are connected. */
    public AdminDashboard(AppController app, int view, ActionListener navigation) {
        this.app = app;
        this.view = view;
        this.navigation = navigation;
        setLayout(new BorderLayout());
        message.setForeground(Color.RED);
        String[] names = {"GPU dashboard", "Registration requests", "Students and time grants", "All jobs"};
        JPanel heading = gridPanel(3, 1);
        heading.add(new JLabel(names[view], SwingConstants.CENTER));
        heading.add(fieldRow("Simulation time", clock));
        heading.add(message);
        add(heading, BorderLayout.PAGE_START);
        if (view == 0) {
            add(buildDashboard(), BorderLayout.CENTER);
        } else if (view == 1) {
            add(buildRequests(), BorderLayout.CENTER);
        } else if (view == 2) {
            add(buildStudents(), BorderLayout.CENTER);
        } else {
            add(buildJobs(), BorderLayout.CENTER);
        }
        JPanel navigationPanel = flowPanel();
        String[] buttonNames = {"Dashboard", "Requests", "Students", "All jobs",
                "Clock setup", "Save", "Log out", "Exit"};
        for (String name : buttonNames) {
            navigationPanel.add(button(name));
        }
        add(navigationPanel, BorderLayout.PAGE_END);
        refresh();
    }

    // These small builders keep repeated Swing setup inside this screen.
    private JPanel borderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        return panel;
    }

    private JPanel flowPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        return panel;
    }

    private JPanel gridPanel(int rows, int columns) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(rows, columns));
        return panel;
    }

    private JTextField display(int columns) {
        JTextField field = new JTextField(columns);
        field.setEditable(false);
        return field;
    }

    private JTextField[] displays(int count) {
        JTextField[] fields = new JTextField[count];
        for (int i = 0; i < count; i++) {
            fields[i] = display(40);
        }
        return fields;
    }

    private JButton button(String action) {
        JButton button = new JButton(action);
        // The action stays the same even when a toggle button changes its label.
        button.addActionListener(new ActionHandler(action));
        return button;
    }

    private JPanel fieldRow(String label, JTextField field) {
        JPanel row = gridPanel(1, 2);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private String time(LocalDateTime value) {
        return value == null ? "-" : TimeFormat.format(value);
    }

    private JPanel buildDashboard() {
        JPanel dashboard = borderPanel();
        JPanel gpuStatus = gridPanel(5, 1);
        gpuStatus.add(summary);
        for (int index = 0; index < gpuRows.length; index++) {
            gpuStatus.add(gpuRows[index]);
        }
        dashboard.add(gpuStatus, BorderLayout.PAGE_START);
        dashboard.add(buildRecords("Waiting queue - first come, first served"), BorderLayout.CENTER);
        dashboard.add(advanceButton, BorderLayout.PAGE_END);
        return dashboard;
    }

    private JPanel buildRequests() {
        JPanel requests = borderPanel();
        requests.add(buildRecords("Pending requests / processed history"), BorderLayout.PAGE_START);
        requests.add(detailRows(new String[]{"University ID", "Full name", "Email", "Phone", "Password", "Status"}),
                BorderLayout.CENTER);
        JPanel actions = flowPanel();
        actions.add(historyButton);
        actions.add(new JLabel("Initial minutes"));
        actions.add(initialMinutes);
        actions.add(approveButton);
        actions.add(rejectButton);
        requests.add(actions, BorderLayout.PAGE_END);
        return requests;
    }

    private JPanel buildStudents() {
        JPanel students = borderPanel();
        students.add(buildSearchableRecords("Approved students / selected student's jobs"), BorderLayout.PAGE_START);
        students.add(detailRows(studentLabels), BorderLayout.CENTER);
        JPanel actions = borderPanel();
        JPanel selection = flowPanel();
        selection.add(studentJobsButton);
        actions.add(selection, BorderLayout.PAGE_START);
        grantActions = gridPanel(2, 1);
        JPanel grant = flowPanel();
        grant.add(new JLabel("Additional minutes"));
        grant.add(extraMinutes);
        grant.add(new JLabel("Reason"));
        grant.add(reason);
        grant.add(grantButton);
        JPanel history = flowPanel();
        history.add(button("Previous grant"));
        history.add(button("Next grant"));
        grantActions.add(grant);
        grantActions.add(history);
        actions.add(grantActions, BorderLayout.CENTER);
        cancellationActions = buildCancellation();
        actions.add(cancellationActions, BorderLayout.PAGE_END);
        students.add(actions, BorderLayout.PAGE_END);
        return students;
    }

    private JPanel buildJobs() {
        JPanel jobs = borderPanel();
        jobs.add(buildSearchableRecords("All students' jobs"), BorderLayout.PAGE_START);
        jobs.add(detailRows(jobLabels), BorderLayout.CENTER);
        cancellationActions = buildCancellation();
        jobs.add(cancellationActions, BorderLayout.PAGE_END);
        return jobs;
    }

    private JPanel buildSearchableRecords(String heading) {
        JPanel group = borderPanel();
        JPanel filters = borderPanel();
        JPanel searchRow = flowPanel();
        searchRow.add(searchLabel);
        searchRow.add(search);
        searchRow.add(button("Search"));
        searchRow.add(button("Clear search"));
        filters.add(searchRow, BorderLayout.PAGE_START);
        statusButtons = flowPanel();
        String[] statuses = {"All", "Queued", "Running", "Completed", "Cancelled"};
        for (String status : statuses) {
            statusButtons.add(button(status));
        }
        statusButtons.add(filterInfo);
        filters.add(statusButtons, BorderLayout.PAGE_END);
        group.add(filters, BorderLayout.PAGE_START);
        group.add(buildRecords(heading), BorderLayout.CENTER);
        return group;
    }

    private JPanel buildCancellation() {
        JPanel actions = flowPanel();
        actions.add(new JLabel("Cancellation reason"));
        actions.add(cancellationReason);
        actions.add(cancelButton);
        return actions;
    }

    private JPanel buildRecords(String heading) {
        JPanel list = borderPanel();
        list.add(new JLabel(heading, SwingConstants.CENTER), BorderLayout.PAGE_START);
        JPanel rows = gridPanel(6, 1);
        for (int index = 0; index < records.length; index++) {
            rows.add(records[index]);
        }
        rows.add(pageInfo);
        list.add(rows, BorderLayout.CENTER);
        JPanel controls = flowPanel();
        controls.add(button("Previous"));
        controls.add(button("Next"));
        if (view != 0) {
            controls.add(new JLabel("Record number"));
            controls.add(recordNumber);
            controls.add(button("View record"));
        }
        list.add(controls, BorderLayout.PAGE_END);
        return list;
    }

    private JPanel detailRows(String[] labels) {
        JPanel rows = gridPanel(labels.length, 1);
        for (int index = 0; index < labels.length; index++) {
            JPanel row = gridPanel(1, 2);
            detailLabels[index] = new JLabel(labels[index]);
            row.add(detailLabels[index]);
            row.add(details[index]);
            rows.add(row);
        }
        return rows;
    }

    private List<AccessRequest> requests() {
        if (pendingOnly) {
            return app.accounts().getPendingRequests();
        }
        return app.accounts().getRequests();
    }

    private ArrayList<Student> students() {
        ArrayList<Student> matches = new ArrayList<Student>();
        for (Student student : app.accounts().getStudents()) {
            if (matchesText(student.getUniversityId(), searchText)) {
                matches.add(student);
            }
        }
        return matches;
    }

    private ArrayList<Job> jobs() {
        ArrayList<Job> matches = new ArrayList<Job>();
        for (Job job : app.scheduler().getAllJobs()) {
            boolean ownerMatches = !studentJobs || (selectedStudent != null
                    && job.getStudentId().equals(selectedStudent.getUniversityId()));
            boolean statusMatches = statusFilter.equals("All")
                    || statusFilter.equals(job.getStatus().toString());
            boolean textMatches = matchesText(job.getJobId(), searchText)
                    || matchesText(job.getTitle(), searchText)
                    || matchesText(job.getStudentId(), searchText);
            if (ownerMatches && statusMatches && textMatches) {
                matches.add(job);
            }
        }
        return matches;
    }

    /** Lecture 15 sequential search, with the String methods from Lecture 3. */
    private boolean matchesText(String text, String query) {
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

    public void refresh() {
        LocalDateTime currentTime = app.clock().getCurrentTime();
        clock.setText(time(currentTime));
        message.setText(app.status());
        ArrayList<String> summaries = new ArrayList<String>();
        if (view == 0) {
            summary.setText("Busy GPUs: " + (4 - app.scheduler().getFreeGpuCount())
                    + " / 4 | Running: " + app.scheduler().getRunningJobs().size()
                    + " | Queued: " + app.scheduler().getQueuedJobs().size()
                    + " | Completed: " + app.scheduler().getCompletedJobs().size()
                    + " | Cancelled: " + app.scheduler().getCancelledJobs().size()
                    + " | Utilization: " + percent(app.scheduler().getOverallUtilizationPercent()));
            advanceButton.setEnabled(app.clock().isSet());
            for (int index = 0; index < gpuRows.length; index++) {
                GPU gpu = app.scheduler().getGpus().get(index);
                String description = "GPU " + gpu.getId() + " | Available";
                if (!gpu.isFree()) {
                    Job job = gpu.getCurrentJob();
                    description = "GPU " + gpu.getId() + " | Job " + job.getJobId()
                            + " | Student " + job.getStudentId()
                            + " | Remaining " + gpu.getRemainingRuntime(app.clock().getCurrentTime())
                            + " min | Finish " + time(gpu.getExpectedFinish());
                }
                gpuRows[index].setText(description + " | Utilization "
                        + percent(app.scheduler().getGpuUtilizationPercent(gpu.getId())));
            }
            for (Job job : app.scheduler().getQueuedJobs()) {
                LocalDateTime start = app.scheduler().getPredictedStartTime(job.getJobId());
                LocalDateTime finish = app.scheduler().getPredictedFinishTime(job.getJobId());
                summaries.add(job.getJobId() + " | Student " + job.getStudentId()
                        + " | " + job.getApplicationName()
                        + " | Position " + app.scheduler().getQueuePosition(job.getJobId())
                        + " | Start " + time(start)
                        + " | Finish " + time(finish)
                        + " | Wait " + app.scheduler().getPredictedWaitMinutes(job.getJobId()) + " min");
            }
        } else if (view == 1) {
            for (AccessRequest request : requests()) {
                summaries.add(request.getUniversityId() + " | " + request.getFullName() + " | " + request.getStatus());
            }
            for (int index = 0; index < details.length; index++) {
                details[index].setText("");
            }
            if (selectedRequest != null) {
                details[0].setText(selectedRequest.getUniversityId());
                details[1].setText(selectedRequest.getFullName());
                details[2].setText(selectedRequest.getEmail());
                details[3].setText(selectedRequest.getPhone());
                details[4].setText("******** (masked)");
                details[5].setText("" + selectedRequest.getStatus());
            }
            historyButton.setText(pendingOnly ? "Show request history" : "Show pending requests");
            boolean pending = selectedRequest != null && selectedRequest.isPending();
            approveButton.setEnabled(pending);
            rejectButton.setEnabled(pending);
            initialMinutes.setEditable(pending);
        } else if (view == 2) {
            searchLabel.setText(studentJobs ? "Jobs for " + selectedStudent.getUniversityId()
                    + ": job ID / title" : "University ID");
            statusButtons.setVisible(studentJobs);
            grantActions.setVisible(!studentJobs);
            cancellationActions.setVisible(studentJobs);
            studentJobsButton.setText(studentJobs ? "Back to student" : "View student's jobs");
            studentJobsButton.setEnabled(selectedStudent != null);
            if (studentJobs) {
                addJobSummaries(summaries);
            } else {
                for (Student student : students()) {
                    summaries.add(student.getUniversityId() + " | " + student.getFullName()
                            + " | Available " + student.getAllowance().available() + " min");
                }
                setDetailLabels(studentLabels);
                refreshStudentDetails();
                grantButton.setEnabled(selectedStudent != null && app.clock().isSet());
            }
        } else {
            searchLabel.setText("Job ID / title / student ID");
            addJobSummaries(summaries);
        }
        // Each page contains five records; keep Previous and Next within the list.
        int lastPage = (summaries.size() - 1) / 5;
        if (page < 0) {
            page = 0;
        } else if (page > lastPage) {
            page = lastPage;
        }
        for (int row = 0; row < records.length; row++) {
            int index = page * 5 + row;
            records[row].setText(index < summaries.size() ? (index + 1) + ". " + summaries.get(index) : "");
        }
        int pageCount = (summaries.size() + 4) / 5;
        if (pageCount == 0) {
            pageCount = 1;
        }
        pageInfo.setText("Page " + (page + 1) + " of " + pageCount + " | " + summaries.size() + " records");
    }

    private void setDetailLabels(String[] labels) {
        for (int i = 0; i < labels.length; i++) {
            detailLabels[i].setText(labels[i]);
        }
    }

    private void addJobSummaries(ArrayList<String> summaries) {
        boolean selectedVisible = false;
        for (Job job : jobs()) {
            summaries.add(job.getJobId() + " | " + job.getStudentId() + " | "
                    + job.getTitle() + " | " + job.getStatus());
            if (job.getJobId().equals(selectedJobId)) {
                selectedVisible = true;
            }
        }
        if (!selectedVisible) {
            selectedJobId = null;
        }
        setDetailLabels(jobLabels);
        filterInfo.setText("Status: " + statusFilter);
        refreshJobDetails();
        refreshCancellation();
    }

    private void refreshCancellation() {
        Job job = app.scheduler().getJobById(selectedJobId);
        cancelButton.setEnabled(job != null && job.isCancellable());
    }

    private void refreshStudentDetails() {
        for (int index = 0; index < details.length; index++) {
            details[index].setText("");
        }
        if (selectedStudent == null) {
            return;
        }
        TimeAllowance allowance = selectedStudent.getAllowance();
        String[] values = {selectedStudent.getUniversityId() + " / " + selectedStudent.getFullName(),
                selectedStudent.getEmail(), selectedStudent.getPhone(), "" + allowance.getAllocated(),
                "" + allowance.getUsed(), "" + allowance.getReserved(), "" + allowance.available()};
        for (int index = 0; index < values.length; index++) {
            details[index].setText(values[index]);
        }
        List<TimeAllowance.Grant> grants = allowance.getGrants();
        if (grants.size() == 0) {
            details[7].setText("No additional grants");
        } else {
            if (grantIndex < 0) {
                grantIndex = 0;
            } else if (grantIndex >= grants.size()) {
                grantIndex = grants.size() - 1;
            }
            TimeAllowance.Grant grant = grants.get(grantIndex);
            details[7].setText((grantIndex + 1) + " of " + grants.size());
            details[8].setText(grant.getMinutes() + " / " + grant.getGrantedBy() + " / "
                    + time(grant.getTime()));
            details[9].setText(grant.getReason());
        }
    }

    /** Reads the selected job's values without changing the job or scheduler. */
    private void refreshJobDetails() {
        Job job = app.scheduler().getJobById(selectedJobId);
        for (int index = 0; index < details.length; index++) {
            details[index].setText("");
        }
        if (job == null) {
            return;
        }
        details[0].setText(job.getJobId() + " / " + job.getStudentId());
        details[1].setText(job.getTitle());
        details[2].setText(job.getApplicationName());
        details[3].setText(job.getDescription());
        details[4].setText(job.getTaskSize() + " / " + job.getEstimatedDuration() + " min");
        details[5].setText(job.getStatus() + " / GPU "
                + (job.getAssignedGpuId() == null ? "-" : job.getAssignedGpuId()));
        details[6].setText(time(job.getSubmissionTime()));
        details[7].setText(time(job.getStartTime())
                + " / " + time(job.getFinishTime()));
        LocalDateTime predictedStart = app.scheduler().getPredictedStartTime(job.getJobId());
        LocalDateTime predictedFinish = app.scheduler().getPredictedFinishTime(job.getJobId());
        details[8].setText(time(predictedStart)
                + " / " + time(predictedFinish));
        int position = app.scheduler().getQueuePosition(job.getJobId());
        long wait = app.scheduler().getPredictedWaitMinutes(job.getJobId());
        details[9].setText((position < 0 ? "Not queued" : "Position " + position)
                + " / " + (wait < 0 ? "No wait recorded" : wait + " min"));
        LocalDateTime currentTime = app.clock().getCurrentTime();
        details[10].setText(job.getRuntimeMinutes(currentTime) + " / " + job.getRemainingRuntime(currentTime)
                + " / " + job.getChargedMinutes() + " min");
        if (job.getCancelTime() == null) {
            details[11].setText("-");
        } else {
            details[11].setText(TimeFormat.format(job.getCancelTime()) + " / " + job.getCancelledBy()
                    + " / " + job.getCancelReason());
        }
    }

    private String percent(double value) {
        // Utilization is unavailable until the first simulated minute has elapsed.
        if (app.clock().getElapsedMinutes() <= 0) {
            return "N/A";
        }
        return String.format("%.1f", value) + "%";
    }

    private boolean pendingRequestSelected() {
        if (selectedRequest == null || !selectedRequest.isPending()) {
            app.report("Choose a pending request using View record first.");
            return false;
        }
        return true;
    }

    /** Refresh locally, then let Main show the destination requested by the controller. */
    private void updateScreen(ActionEvent event) {
        refresh();
        if (navigation != null) {
            // Forward the original button event after its local handler has finished.
            navigation.actionPerformed(event);
        }
    }

    private void clearJobSelection() {
        recordNumber.setText("");
        selectedJobId = null;
    }

    private void clearSelection() {
        clearJobSelection();
        selectedRequest = null;
        // Keep the owner selected while browsing that student's jobs.
        if (!studentJobs) {
            selectedStudent = null;
        }
    }

    /** Record numbers refer to the same filtered lists used by refresh(). */
    private void selectRecord() {
        int count;
        if (view == 1) {
            count = requests().size();
        } else if (view == 2 && !studentJobs) {
            count = students().size();
        } else {
            count = jobs().size();
        }
        if (count == 0) {
            throw new IllegalArgumentException("There are no records to select.");
        }
        int number = Integer.parseInt(recordNumber.getText());
        if (number < 1 || number > count) {
            throw new IllegalArgumentException("Choose a record number from 1 to " + count + ".");
        }
        if (view == 1) {
            AccessRequest request = requests().get(number - 1);
            if (request != selectedRequest) {
                initialMinutes.setText("100"); // Each new request starts with the required default.
            }
            selectedRequest = request;
        } else if (view == 2 && !studentJobs) {
            selectedStudent = students().get(number - 1);
            grantIndex = 0;
        } else {
            selectedJobId = jobs().get(number - 1).getJobId();
        }
    }

    private void processRequest(boolean approve) {
        if (!pendingRequestSelected()) {
            return;
        }
        boolean success;
        if (approve) {
            success = app.approveRequest(selectedRequest.getUniversityId(),
                    Integer.parseInt(initialMinutes.getText()));
        } else {
            success = app.rejectRequest(selectedRequest.getUniversityId());
        }
        if (success) {
            selectedRequest = null;
            recordNumber.setText("");
            initialMinutes.setText("100");
        }
    }

    private void grantTime() {
        if (selectedStudent == null) {
            app.report("Choose a student using View record first.");
        } else if (app.grantTime(selectedStudent.getUniversityId(),
                Integer.parseInt(extraMinutes.getText()), reason.getText())) {
            reason.setText("");
            grantIndex = selectedStudent.getAllowance().getGrants().size() - 1;
        }
    }

    private void toggleStudentJobs() {
        if (selectedStudent == null) {
            app.report("Choose a student using View record first.");
            return;
        }
        studentJobs = !studentJobs;
        searchText = "";
        search.setText("");
        statusFilter = "All";
        page = 0;
        clearJobSelection();
        cancellationReason.setText("");
    }

    private void cancelJob() {
        Job job = app.scheduler().getJobById(selectedJobId);
        if (job == null || !job.isCancellable()) {
            app.report("Choose a queued or running job using View record first.");
        } else if (!hasText(cancellationReason.getText())) {
            app.report("Enter a cancellation reason before reviewing the charge.");
        } else {
            int charge = job.getRuntimeMinutes(app.clock().getCurrentTime());
            String reasonText = cancellationReason.getText();
            // A modal dialog requires a decision before another screen can advance time.
            int choice = JOptionPane.showConfirmDialog(this,
                    "Cancel " + job.getJobId() + " (" + job.getStatus() + ")?\nCharge: "
                    + charge + " min. Unused reservation returned: "
                    + (job.getEstimatedDuration() - charge) + " min.\nReason: " + reasonText,
                    "Confirm cancellation", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                if (!job.isCancellable()
                        || charge != job.getRuntimeMinutes(app.clock().getCurrentTime())) {
                    app.report("The job changed. Review cancellation again before confirming.");
                } else if (app.cancelJob(job.getJobId(), "admin", reasonText)) {
                    cancellationReason.setText("");
                }
            }
        }
    }

    /** Every button uses one named listener and the same refresh/error handling. */
    private class ActionHandler implements ActionListener {
        private String action;

        public ActionHandler(String action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent event) {
            try {
                if (action.equals("Previous") || action.equals("Next")) {
                    page += action.equals("Previous") ? -1 : 1;
                    clearSelection();
                } else if (action.equals("View record")) {
                    selectRecord();
                } else if (action.equals("Advance one minute")) {
                    app.advanceMinute();
                } else if (action.equals("Search") || action.equals("Clear search")) {
                    if (action.equals("Clear search")) {
                        search.setText("");
                    }
                    searchText = search.getText();
                    page = 0;
                    clearSelection();
                } else if (action.equals("All") || action.equals("Queued") || action.equals("Running")
                        || action.equals("Completed") || action.equals("Cancelled")) {
                    statusFilter = action;
                    page = 0;
                    clearJobSelection();
                } else if (action.equals("View student's jobs")) {
                    toggleStudentJobs();
                } else if (action.equals("Show request history")) {
                    pendingOnly = !pendingOnly;
                    page = 0;
                    selectedRequest = null;
                    recordNumber.setText("");
                } else if (action.equals("Approve selected") || action.equals("Reject selected")) {
                    processRequest(action.equals("Approve selected"));
                } else if (action.equals("Grant additional time")) {
                    grantTime();
                } else if (action.equals("Previous grant") || action.equals("Next grant")) {
                    grantIndex += action.equals("Previous grant") ? -1 : 1;
                } else if (action.equals("Cancel selected job")) {
                    cancelJob();
                } else {
                    if (action.equals("Dashboard")) {
                        app.showAdmin(0);
                    } else if (action.equals("Requests")) {
                        app.showAdmin(1);
                    } else if (action.equals("Students")) {
                        app.showAdmin(2);
                    } else if (action.equals("All jobs")) {
                        app.showAdmin(3);
                    } else if (action.equals("Clock setup")) {
                        app.showClockSetup();
                    } else if (action.equals("Save")) {
                        app.save();
                    } else if (action.equals("Log out")) {
                        app.logout();
                    } else {
                        app.close();
                    }
                }
            } catch (NumberFormatException exception) {
                app.report("Enter a whole number for the record number or minutes.");
            } catch (IllegalArgumentException exception) {
                app.report(exception.getMessage());
            } finally {
                updateScreen(event);
            }
        }
    }
}
