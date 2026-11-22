package gpu.storage;

import gpu.logic.*;
import gpu.model.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Saves and restores accounts, jobs and the clock in one state.txt file. */
public class DataStore {
    private static final String HEADER = "GPU-UTILIZATION-STATE|1";
    private static final String[] SECTIONS = {"[clock]", "[students]", "[requests]", "[grants]", "[jobs]"};
    private String directory;

    public DataStore() {
        this("data");
    }

    /** The directory must exist. A missing state.txt starts an empty simulation. */
    public DataStore(String directory) {
        if (directory == null || directory.length() == 0) {
            throw new IllegalArgumentException("Enter a data directory.");
        }
        this.directory = directory;
    }

    /** One load returns the accounts and scheduler that belong to the same saved session. */
    public static class State {
        private AccountService accounts;
        private Scheduler scheduler;

        public State(AccountService accounts, Scheduler scheduler) {
            this.accounts = accounts;
            this.scheduler = scheduler;
        }

        public AccountService accounts() {
            return accounts;
        }

        public Scheduler scheduler() {
            return scheduler;
        }
    }

    /** Only a missing snapshot starts fresh; existing invalid or unreadable data is reported. */
    public State load() throws IOException {
        String filename = directory + "/state.txt";
        // A missing snapshot is normal on first use; a bad directory is an error.
        File dataDirectory = new File(directory);
        if (!dataDirectory.exists() || dataDirectory.isFile()) {
            throw new IOException(directory + ": Data directory must already exist and be a directory.");
        }
        File savedFile = new File(filename);
        if (!savedFile.exists()) {
            return new State(new AccountService(), new Scheduler(new SimulationClock()));
        }
        if (!savedFile.isFile()) {
            throw new IOException(filename + ": Saved data path must be a file, not a directory.");
        }
        return restore(readLines(filename), filename);
    }

    /** PrintWriter replaces the file directly, so keep a separate copy of important saves. */
    public void save(AccountService accounts, Scheduler scheduler) throws IOException {
        if (accounts == null || scheduler == null) {
            throw new IllegalArgumentException("Accounts and scheduler are required.");
        }
        // Build the entire snapshot before opening the existing save.
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(HEADER);
        lines.add(SECTIONS[0]);
        SimulationClock clock = scheduler.getClock();
        if (clock.isSet()) {
            lines.add("set|" + TimeFormat.format(clock.getStartTime()) + "|"
                    + TimeFormat.format(clock.getCurrentTime()));
        } else {
            lines.add("unset");
        }
        lines.add(SECTIONS[1]);
        for (Student student : accounts.getStudents()) {
            lines.add(student.toLine());
        }
        lines.add(SECTIONS[2]);
        for (AccessRequest request : accounts.getRequests()) {
            lines.add(request.toLine());
        }
        lines.add(SECTIONS[3]);
        for (Student student : accounts.getStudents()) {
            for (TimeAllowance.Grant grant : student.getAllowance().getGrants()) {
                lines.add(grant.toLine());
            }
        }
        lines.add(SECTIONS[4]);
        for (Job job : scheduler.getAllJobs()) {
            lines.add(job.toLine());
        }

        String filename = directory + "/state.txt";
        // Each record must occupy one line, including when it contains unusual text.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (int j = 0; j < line.length(); j++) {
                char character = line.charAt(j);
                if (character == '\r' || character == '\n' || character == 133
                        || character == 8232 || character == 8233) {
                    throw invalid(filename, i + 1, "A record contains an unsupported line separator.");
                }
            }
        }
        // Test the records in separate objects before replacing the old file.
        restore(lines, filename);
        PrintWriter output = new PrintWriter(filename);
        try {
            for (String line : lines) {
                output.println(line);
            }
        } finally {
            output.close();
        }
        // Read back each line to detect an incomplete or changed save.
        ArrayList<String> saved = readLines(filename);
        if (saved.size() != lines.size()) {
            throw new IOException(filename + ": Saved records do not match. Use Save to retry.");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equals(saved.get(i))) {
                throw invalid(filename, i + 1, "Saved record does not match. Use Save to retry.");
            }
        }
    }

    /** Scanner throws NoSuchElementException when there are no more lines. */
    private ArrayList<String> readLines(String filename) throws IOException {
        Scanner input;
        try {
            input = new Scanner(new File(filename));
        } catch (FileNotFoundException exception) {
            throw new IOException(filename + ": Cannot open saved data. Check the file and its read permissions.");
        }
        ArrayList<String> lines = new ArrayList<String>();
        try {
            while (true) {
                lines.add(input.nextLine());
            }
        } catch (NoSuchElementException exception) {
            // End of file: return the lines already read.
        } finally {
            input.close();
        }
        return lines;
    }

    /** Restore in order: clock, students, requests, grants, then jobs. */
    private State restore(ArrayList<String> lines, String filename) throws IOException {
        if (lines.size() == 0 || !HEADER.equals(lines.get(0))) {
            throw invalid(filename, 1, "Missing or unsupported snapshot header.");
        }
        AccountService accounts = new AccountService();
        SimulationClock clock = new SimulationClock();
        Scheduler scheduler = new Scheduler(clock);
        ArrayList<Integer> studentLines = new ArrayList<Integer>(); // For balance error messages.
        int section = -1;
        boolean clockRead = false;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int marker = -1;
            for (int j = 0; j < SECTIONS.length; j++) {
                if (SECTIONS[j].equals(line)) {
                    marker = j;
                }
            }
            if (marker >= 0) {
                if (marker != section + 1) {
                    throw invalid(filename, i + 1, "Missing or out-of-order section: " + line);
                }
                if (marker == 1 && !clockRead) {
                    throw invalid(filename, i + 1, "Expected one clock record.");
                }
                section = marker;
            } else {
                try {
                    if (section < 0 || line.length() == 0) {
                        throw new IllegalArgumentException("Expected a section or nonempty record.");
                    }
                    if (section == 0) {
                        if (clockRead) {
                            throw new IllegalArgumentException("Expected one clock record.");
                        }
                        restoreClock(line, clock);
                        clockRead = true;
                    } else if (section == 1) {
                        Student student = Student.fromLine(line);
                        accounts.restoreStudent(student);
                        scheduler.registerAllowance(student.getAllowance());
                        studentLines.add(i + 1);
                    } else if (section == 2) {
                        // Rejected history may share an ID with a later registration request.
                        accounts.restoreRequest(AccessRequest.fromLine(line));
                    } else if (section == 3) {
                        TimeAllowance.Grant grant = TimeAllowance.Grant.fromLine(line);
                        Student student = accounts.getStudentById(grant.getOwnerId());
                        if (student == null) {
                            throw new IllegalArgumentException("Grant refers to an unknown student.");
                        }
                        checkTime(grant.getTime(), clock, "Time grant");
                        student.getAllowance().restoreGrant(grant);
                    } else {
                        Job job = Job.fromLine(line);
                        if (accounts.getStudentById(job.getStudentId()) == null) {
                            throw new IllegalArgumentException("Job refers to an unknown student.");
                        }
                        checkJob(job, clock);
                        // The scheduler checks duplicate IDs and conflicting GPU assignments.
                        scheduler.restoreJob(job);
                    }
                } catch (RuntimeException exception) {
                    throw invalid(filename, i + 1, exception.getMessage());
                }
            }
        }
        if (section != SECTIONS.length - 1) {
            throw invalid(filename, lines.size() + 1, "Missing snapshot sections.");
        }
        // Recalculate used/reserved minutes from jobs and compare them with each account.
        // long avoids overflow while adding minutes from many records.
        int studentIndex = 0;
        for (Student student : accounts.getStudents()) {
            long used = 0;
            long reserved = 0;
            for (Job job : scheduler.getAllJobs()) {
                if (job.getStudentId().equals(student.getUniversityId())) {
                    used += job.getChargedMinutes();
                    if (job.isCancellable()) {
                        reserved += job.getEstimatedDuration();
                    }
                }
            }
            if (student.getAllowance().getUsed() != used || student.getAllowance().getReserved() != reserved) {
                throw invalid(filename, studentLines.get(studentIndex),
                        "Saved used/reserved balances do not match jobs for student " + student.getUniversityId() + ".");
            }
            studentIndex++;
        }
        // All running jobs must be restored before assigning a free GPU to queued work.
        if (clock.isSet()) {
            scheduler.startQueuedJobs();
        }
        return new State(accounts, scheduler);
    }

    private void restoreClock(String line, SimulationClock clock) {
        if (line.equals("unset")) {
            return;
        }
        if (line.length() != 37 || !line.startsWith("set|") || line.charAt(20) != '|') {
            throw new IllegalArgumentException("Invalid clock record.");
        }
        String start = line.substring(4, 20);
        String current = line.substring(21);
        // Reject impossible dates that the parser silently changes to a different date.
        LocalDateTime startTime = TimeFormat.parse(start);
        LocalDateTime currentTime = TimeFormat.parse(current);
        if (!TimeFormat.format(startTime).equals(start) || !TimeFormat.format(currentTime).equals(current)) {
            throw new IllegalArgumentException("Enter valid clock dates as dd/MM/yyyy HH:mm.");
        }
        clock.restore(startTime, currentTime);
    }

    /** Rearrange dd/MM/yyyy HH:mm as yyyyMMddHH:mm so text comparison orders dates. */
    private String timeKey(LocalDateTime time) {
        String text = TimeFormat.format(time);
        if (text.length() != 16) {
            throw new IllegalArgumentException("Enter a four-digit simulation year.");
        }
        return text.substring(6, 10) + text.substring(3, 5) + text.substring(0, 2) + text.substring(11);
    }

    private boolean before(LocalDateTime first, LocalDateTime second) {
        return timeKey(first).compareTo(timeKey(second)) < 0;
    }

    private void checkTime(LocalDateTime time, SimulationClock clock, String label) {
        if (!clock.isSet() || time == null || before(time, clock.getStartTime())
                || before(clock.getCurrentTime(), time)) {
            throw new IllegalArgumentException(label + " must fall within the saved simulation time.");
        }
    }

    /** Saved job times and GPU assignments must agree with the saved clock. */
    private void checkJob(Job job, SimulationClock clock) {
        checkTime(job.getSubmissionTime(), clock, "Job submission");
        if (job.hasStarted()) {
            checkTime(job.getStartTime(), clock, "Job start");
            if (before(job.getStartTime(), job.getSubmissionTime())) {
                throw new IllegalArgumentException("Job starts before submission.");
            }
            if (job.getAssignedGpuId() < 1 || job.getAssignedGpuId() > Scheduler.GPU_COUNT) {
                throw new IllegalArgumentException("Invalid saved GPU ID.");
            }
        }
        if (job.getStatus() == JobStatus.RUNNING && !before(clock.getCurrentTime(), job.getExpectedFinish())) {
            throw new IllegalArgumentException("Running job should already have completed.");
        }
        if (job.getFinishTime() != null) {
            checkTime(job.getFinishTime(), clock, "Job finish");
            if (before(job.getFinishTime(), job.getStartTime()) || job.getChargedMinutes() > job.getEstimatedDuration()) {
                throw new IllegalArgumentException("Invalid job finish or charged runtime.");
            }
        }
        if (job.getStatus() == JobStatus.COMPLETED && before(job.getFinishTime(), job.getExpectedFinish())) {
            throw new IllegalArgumentException("Completed job has not run for its full duration.");
        }
        if (job.getCancelTime() != null) {
            checkTime(job.getCancelTime(), clock, "Job cancellation");
            LocalDateTime earliest = job.hasStarted() ? job.getStartTime() : job.getSubmissionTime();
            if (before(job.getCancelTime(), earliest)
                    || (job.hasStarted() && !timeKey(job.getCancelTime()).equals(timeKey(job.getFinishTime())))) {
                throw new IllegalArgumentException("Invalid job cancellation time.");
            }
        }
    }

    private IOException invalid(String filename, int line, String message) {
        return new IOException(filename + ":" + line + ": " + message);
    }
}
