package gpu.storage;

import gpu.logic.*;
import gpu.model.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Saves a complete application snapshot in data/state.txt using UTF-8 text.
 * Section records use the model's documented toLine/fromLine format unchanged.
 * A single replacement avoids mixing accounts and jobs from different saves.
 */
public class DataStore {
    private static final String HEADER = "GPU-UTILIZATION-STATE|1";
    private static final List<String> SECTIONS =
            List.of("[clock]", "[students]", "[requests]", "[grants]", "[jobs]");
    private static final DateTimeFormatter STRICT_TIME = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private final Path directory;

    public DataStore() {
        this(Path.of("data"));
    }

    /** A separate directory is useful for tests and for loading demonstration data. */
    public DataStore(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    /** A plain holder class keeps the two restored services together. */
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

    /** Missing data means first run. Invalid saved data is reported, never discarded. */
    public State load() throws IOException {
        Path file = directory.resolve("state.txt");
        if (Files.notExists(file)) {
            return new State(new AccountService(), new Scheduler(new SimulationClock()));
        }
        return readSnapshot(file, file);
    }

    /**
     * Writes and validates a temporary snapshot before replacing the previous save.
     * Unsupported atomic moves fall back to the filesystem's normal replace operation.
     */
    public void save(AccountService accounts, Scheduler scheduler) throws IOException {
        Objects.requireNonNull(accounts);
        Objects.requireNonNull(scheduler);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(SECTIONS.get(0));
        SimulationClock clock = scheduler.getClock();
        lines.add(clock.isSet() ? "set|" + TimeFormat.format(clock.getStartTime())
                + "|" + TimeFormat.format(clock.getCurrentTime()) : "unset");
        lines.add(SECTIONS.get(1));
        for (Student student : accounts.getStudents()) {
            lines.add(student.toLine());
        }
        lines.add(SECTIONS.get(2));
        for (AccessRequest request : accounts.getRequests()) {
            lines.add(request.toLine());
        }
        lines.add(SECTIONS.get(3));
        for (Student student : accounts.getStudents()) {
            for (TimeAllowance.Grant grant : student.getAllowance().getGrants()) {
                lines.add(grant.toLine());
            }
        }
        lines.add(SECTIONS.get(4));
        for (Job job : scheduler.getAllJobs()) {
            lines.add(job.toLine());
        }

        Files.createDirectories(directory);
        Path destination = directory.resolve("state.txt");
        Path temporary = Files.createTempFile(directory, "state-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            // Reading into fresh objects catches broken references without changing this session.
            readSnapshot(temporary, destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private State readSnapshot(Path source, Path reportedFile) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw invalid(reportedFile, 1, "Missing or unsupported snapshot header.", null);
        }
        List<List<RecordLine>> sections = readSections(lines, reportedFile);
        if (sections.get(0).size() != 1) {
            throw invalid(reportedFile, 2, "Expected one clock record.", null);
        }
        SimulationClock clock = new SimulationClock();
        RecordLine savedClock = sections.get(0).get(0);
        try {
            if (!savedClock.text().equals("unset")) {
                String[] parts = savedClock.text().split("\\|", -1);
                if (parts.length != 3 || !parts[0].equals("set")) {
                    throw new IllegalArgumentException("Invalid clock record.");
                }
                clock.restore(LocalDateTime.parse(parts[1], STRICT_TIME),
                        LocalDateTime.parse(parts[2], STRICT_TIME));
            }
        } catch (RuntimeException ex) {
            throw invalid(reportedFile, savedClock.number(), ex.getMessage(), ex);
        }

        AccountService accounts = new AccountService();
        Scheduler scheduler = new Scheduler(clock);
        Map<String, Integer> studentLines = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        Map<String, Integer> reserved = new HashMap<>();

        // Restore allowance totals first; restoring jobs and grants must not charge them again.
        for (RecordLine record : sections.get(1)) {
            try {
                Student student = Student.fromLine(record.text());
                accounts.restoreStudent(student);
                scheduler.registerAllowance(student.getAllowance());
                studentLines.put(student.getUniversityId(), record.number());
            } catch (RuntimeException ex) {
                throw invalid(reportedFile, record.number(), ex.getMessage(), ex);
            }
        }
        for (RecordLine record : sections.get(2)) {
            try {
                // Rejected historical requests may legitimately share an ID with a later request.
                accounts.restoreRequest(AccessRequest.fromLine(record.text()));
            } catch (RuntimeException ex) {
                throw invalid(reportedFile, record.number(), ex.getMessage(), ex);
            }
        }
        for (RecordLine record : sections.get(3)) {
            try {
                TimeAllowance.Grant grant = TimeAllowance.Grant.fromLine(record.text());
                Student student = accounts.getStudentById(grant.getOwnerId());
                if (student == null) {
                    throw new IllegalArgumentException("Grant refers to an unknown student.");
                }
                checkTime(grant.getTime(), clock, "Time grant");
                student.getAllowance().restoreGrant(grant);
            } catch (RuntimeException ex) {
                throw invalid(reportedFile, record.number(), ex.getMessage(), ex);
            }
        }
        for (RecordLine record : sections.get(4)) {
            try {
                Job job = Job.fromLine(record.text());
                if (accounts.getStudentById(job.getStudentId()) == null) {
                    throw new IllegalArgumentException("Job refers to an unknown student.");
                }
                checkJob(job, clock);
                // restoreJob also checks duplicate job IDs and conflicting GPU assignments.
                scheduler.restoreJob(job);
                addMinutes(used, job.getStudentId(), job.getChargedMinutes());
                if (job.isCancellable()) {
                    addMinutes(reserved, job.getStudentId(), job.getEstimatedDuration());
                }
            } catch (RuntimeException ex) {
                throw invalid(reportedFile, record.number(), ex.getMessage(), ex);
            }
        }
        for (Student student : accounts.getStudents()) {
            TimeAllowance allowance = student.getAllowance();
            String id = student.getUniversityId();
            if (allowance.getUsed() != used.getOrDefault(id, 0)
                    || allowance.getReserved() != reserved.getOrDefault(id, 0)) {
                throw invalid(reportedFile, studentLines.get(id),
                        "Saved used/reserved balances do not match jobs for student " + id + ".", null);
            }
        }
        // Running jobs are all attached before a free GPU may take a queued job.
        if (clock.isSet()) {
            scheduler.startQueuedJobs();
        }
        return new State(accounts, scheduler);
    }

    private static void checkTime(LocalDateTime time, SimulationClock clock, String label) {
        if (!clock.isSet() || time == null || time.isBefore(clock.getStartTime())
                || time.isAfter(clock.getCurrentTime())) {
            throw new IllegalArgumentException(label + " must fall within the saved simulation time.");
        }
    }

    /** Check relationships that individual model records cannot validate against the clock. */
    private static void checkJob(Job job, SimulationClock clock) {
        checkTime(job.getSubmissionTime(), clock, "Job submission");
        if (job.hasStarted()) {
            checkTime(job.getStartTime(), clock, "Job start");
            if (job.getStartTime().isBefore(job.getSubmissionTime())) {
                throw new IllegalArgumentException("Job starts before submission.");
            }
            if (job.getAssignedGpuId() < 1 || job.getAssignedGpuId() > Scheduler.GPU_COUNT) {
                throw new IllegalArgumentException("Invalid saved GPU ID.");
            }
        }
        if (job.getStatus() == JobStatus.RUNNING
                && !job.getExpectedFinish().isAfter(clock.getCurrentTime())) {
            throw new IllegalArgumentException("Running job should already have completed.");
        }
        if (job.getFinishTime() != null) {
            checkTime(job.getFinishTime(), clock, "Job finish");
            if (job.getFinishTime().isBefore(job.getStartTime())
                    || job.getChargedMinutes() > job.getEstimatedDuration()) {
                throw new IllegalArgumentException("Invalid job finish or charged runtime.");
            }
        }
        if (job.getStatus() == JobStatus.COMPLETED
                && job.getFinishTime().isBefore(job.getExpectedFinish())) {
            throw new IllegalArgumentException("Completed job has not run for its full duration.");
        }
        if (job.getCancelTime() != null) {
            checkTime(job.getCancelTime(), clock, "Job cancellation");
            LocalDateTime earliest = job.hasStarted() ? job.getStartTime() : job.getSubmissionTime();
            if (job.getCancelTime().isBefore(earliest)
                    || (job.hasStarted() && !job.getCancelTime().equals(job.getFinishTime()))) {
                throw new IllegalArgumentException("Invalid job cancellation time.");
            }
        }
    }

    private static List<List<RecordLine>> readSections(List<String> lines, Path file) throws IOException {
        List<List<RecordLine>> records = new ArrayList<>();
        for (String ignored : SECTIONS) {
            records.add(new ArrayList<>());
        }
        int section = -1;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            int marker = SECTIONS.indexOf(line);
            if (marker >= 0) {
                if (marker != section + 1) {
                    throw invalid(file, index + 1, "Missing or out-of-order section: " + line, null);
                }
                section = marker;
            } else if (section < 0 || line.isBlank()) {
                throw invalid(file, index + 1, "Expected a section or nonempty record.", null);
            } else {
                records.get(section).add(new RecordLine(index + 1, line));
            }
        }
        if (section != SECTIONS.size() - 1) {
            throw invalid(file, lines.size() + 1, "Missing snapshot sections.", null);
        }
        return records;
    }

    private static IOException invalid(Path file, int line, String detail, Throwable cause) {
        return new IOException(file + ":" + line + ": " + detail, cause);
    }

    /** Keep balance checks explicit rather than using a callback to update the map. */
    private static void addMinutes(Map<String, Integer> totals, String studentId, int minutes) {
        int previous = totals.getOrDefault(studentId, 0);
        totals.put(studentId, Math.addExact(previous, minutes));
    }

    private static class RecordLine {
        private int number;
        private String text;

        public RecordLine(int number, String text) {
            this.number = number;
            this.text = text;
        }

        public int number() {
            return number;
        }

        public String text() {
            return text;
        }
    }
}
