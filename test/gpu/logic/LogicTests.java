package gpu.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import gpu.model.AccessRequest;
import gpu.model.Job;
import gpu.model.JobStatus;
import gpu.model.RequestStatus;
import gpu.model.Student;
import gpu.model.TaskSize;
import gpu.model.TimeAllowance;
import gpu.model.User;
import gpu.storage.DataStore;

/**
 * Dependency-free console tests for Member 2 logic and its storage integration.
 * Run after compiling src and test:
 *   java -cp out gpu.logic.LogicTests
 */
public class LogicTests {

    private static int passed;
    private static int failed;
    private static final LocalDateTime T0 =
        LocalDateTime.of(2026, 11, 16, 10, 0);

    public static void main(String[] args) throws Exception {

        run("Simulation clock", LogicTests::testClock);
        run("Account service", LogicTests::testAccountService);
        run("FCFS, GPU assignment and completion", LogicTests::testFcfsAndCompletion);
        run("Remaining waiting-time prediction", LogicTests::testRemainingWaitPrediction);
        run("Official 10:20 worked example", LogicTests::testOfficialWorkedExample);
        run("Cancellation, permissions and charging", LogicTests::testCancellation);
        run("Utilization and time-zero display", LogicTests::testUtilization);
        run("Restoration and file save/reload", LogicTests::testDataStoreRoundTrip);

        System.out.println();
        System.out.println("========================================");
        System.out.println(passed + " passed, " + failed + " failed");
        System.out.println("========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testClock() {
        SimulationClock clock = new SimulationClock();

        check(!clock.isSet(), "new clock is unset");
        check(clock.getElapsedMinutes() == 0, "unset clock has zero elapsed minutes");

        expect(
            IllegalStateException.class,
            clock::advance,
            "cannot advance before initialization"
        );

        clock.setStartTime(T0);

        check(clock.isSet(), "clock is set");
        check(clock.getCurrentTime().equals(T0), "current time equals start time");

        clock.advance();

        check(
            clock.getCurrentTime().equals(T0.plusMinutes(1)),
            "advance moves exactly one minute"
        );

        check(clock.getElapsedMinutes() == 1, "elapsed minutes updates");

        expect(
            IllegalStateException.class,
            () -> clock.setStartTime(T0.plusHours(1)),
            "clock cannot be initialized twice"
        );

        SimulationClock restored = new SimulationClock();
        restored.restore(T0, T0.plusMinutes(7));

        check(restored.getElapsedMinutes() == 7, "clock restoration restores elapsed time");

        expect(
            IllegalArgumentException.class,
            () -> restored.restore(T0, T0.minusMinutes(1)),
            "restore refuses a current time before start"
        );
    }

    private static void testAccountService() {
        AccountService accounts = new AccountService();

        AccessRequest pending = accounts.registerStudent(
            "S1001",
            "Test Student",
            "student@example.com",
            "+971501234567",
            "student123"
        );

        check(pending.getStatus() == RequestStatus.PENDING, "new registration is pending");
        check(accounts.getPendingRequests().size() == 1, "pending request is listed");

        check(
            accounts.authenticate("S1001", "student123") == null,
            "pending student cannot log in"
        );

        expect(
            IllegalStateException.class,
            () -> accounts.registerStudent(
                "S1001",
                "Duplicate",
                "dup@example.com",
                "+971501234568",
                "password"
            ),
            "duplicate pending university ID is refused"
        );

        expect(
            IllegalArgumentException.class,
            () -> accounts.registerStudent(
                "S1002",
                "Bad Email",
                "not-an-email",
                "+971501234569",
                "password"
            ),
            "invalid email is refused"
        );

        expect(
            IllegalArgumentException.class,
            () -> accounts.registerStudent(
                "S1003",
                "Bad Phone",
                "good@example.com",
                "abc",
                "password"
            ),
            "invalid phone is refused"
        );

        Student student = accounts.approveRequest("S1001");

        check(pending.getStatus() == RequestStatus.APPROVED, "approval updates request status");
        check(student.getAllowance().getAllocated() == 100, "default allocation is 100 minutes");

        check(
            accounts.authenticate("S1001", "student123") instanceof Student,
            "approved student can log in"
        );

        check(
            accounts.authenticate("S1001", "wrong") == null,
            "incorrect student password is refused"
        );

        User admin = accounts.authenticate("admin", "gpu2026");

        check(
            admin != null && "Administrator".equals(admin.getRole()),
            "predefined administrator can log in"
        );

        check(
            accounts.authenticate("admin", "bad") == null,
            "incorrect administrator password is refused"
        );

        expect(
            IllegalArgumentException.class,
            () -> accounts.approveRequest("S1001"),
            "an already processed request cannot be approved again"
        );

        accounts.grantAdditionalTime(
            "S1001",
            20,
            "Testing extra time",
            T0
        );

        check(student.getAllowance().getAllocated() == 120, "grant increases allocated minutes");
        check(student.getAllowance().getGrants().size() == 1, "grant is recorded");

        AccessRequest rejected = accounts.registerStudent(
            "S1002",
            "Rejected Student",
            "rejected@example.com",
            "+971501234570",
            "secret"
        );

        accounts.rejectRequest("S1002");

        check(rejected.getStatus() == RequestStatus.REJECTED, "rejection updates request status");

        check(
            accounts.authenticate("S1002", "secret") == null,
            "rejected student cannot log in"
        );

        check(accounts.getPendingRequests().isEmpty(), "no pending requests remain");
    }

    private static void testFcfsAndCompletion() {
        SimulationClock clock = startedClock();
        Scheduler scheduler = new Scheduler(clock);
        TimeAllowance allowance = new TimeAllowance("S2001", 100);

        for (int i = 1; i <= 5; i++) {
            scheduler.submitJob(
                allowance,
                "Job " + i,
                "MATLAB",
                "FCFS test",
                TaskSize.SMALL
            );
        }

        check(scheduler.getRunningJobs().size() == 4, "four jobs start on four GPUs");
        check(scheduler.getQueuedJobs().size() == 1, "fifth job waits");

        check(
            scheduler.getGpu(1).getCurrentJob().getJobId().equals("J101"),
            "first job uses GPU1"
        );

        check(
            scheduler.getGpu(4).getCurrentJob().getJobId().equals("J104"),
            "fourth job uses GPU4"
        );

        check(
            scheduler.getQueuedJobs().get(0).getJobId().equals("J105"),
            "fifth job is first in queue"
        );

        check(scheduler.getQueuePosition("J105") == 1, "queue position is one");
        check(scheduler.getPredictedWaitMinutes("J105") == 5, "initial predicted wait is five minutes");

        check(
            scheduler.getPredictedStartTime("J105").equals(T0.plusMinutes(5)),
            "predicted start is five minutes after current time"
        );

        for (int i = 0; i < 5; i++) {
            scheduler.advanceMinute();
        }

        for (int i = 0; i < 4; i++) {
            check(
                scheduler.getAllJobs().get(i).getStatus() == JobStatus.COMPLETED,
                "first four jobs complete"
            );
        }

        check(
            scheduler.getJobById("J105").getStatus() == JobStatus.RUNNING,
            "queued fifth job starts when GPU becomes free"
        );

        check(allowance.getUsed() == 20, "completed jobs charge 20 minutes");
        check(allowance.getReserved() == 5, "fifth running job keeps its reservation");
        check(allowance.available() == 75, "available minutes are calculated correctly");
    }

    private static void testRemainingWaitPrediction() {
        SimulationClock clock = startedClock();
        Scheduler scheduler = new Scheduler(clock);
        TimeAllowance allowance = new TimeAllowance("S3001", 200);

        for (int i = 0; i < 5; i++) {
            scheduler.submitJob(
                allowance,
                "Large " + i,
                "CUDA",
                "Wait prediction",
                TaskSize.LARGE
            );
        }

        check(
            scheduler.getPredictedWaitMinutes("J105") == 30,
            "fifth large job initially waits 30 minutes"
        );

        for (int i = 0; i < 10; i++) {
            scheduler.advanceMinute();
        }

        check(
            scheduler.getPredictedStartTime("J105").equals(T0.plusMinutes(30)),
            "predicted start stays at original GPU availability time"
        );

        check(
            scheduler.getPredictedWaitMinutes("J105") == 20,
            "remaining wait is predicted start minus CURRENT time"
        );
    }

    /** Reproduces the official guide's 10:20 workload and 96.25% (shown as 96.3%) utilization. */
    private static void testOfficialWorkedExample() {
        SimulationClock clock = startedClock();
        Scheduler scheduler = new Scheduler(clock);

        TimeAllowance ali = new TimeAllowance("Ali", 100);
        TimeAllowance sara = new TimeAllowance("Sara", 100);
        TimeAllowance hamad = new TimeAllowance("Hamad", 100);
        TimeAllowance mariam = new TimeAllowance("Mariam", 100);
        TimeAllowance noura = new TimeAllowance("Noura", 100);

        scheduler.submitJob(
            ali, "Jupyter", "Jupyter", "Example J101", TaskSize.SMALL
        );

        scheduler.submitJob(
            sara, "Pandas", "Pandas", "Example J102", TaskSize.MEDIUM
        );

        scheduler.submitJob(
            hamad, "BLAST", "BLAST", "Example J103", TaskSize.MEDIUM
        );

        scheduler.submitJob(
            mariam, "Training", "PyTorch", "Example J104", TaskSize.LARGE
        );

        advance(clock, scheduler, 8);

        scheduler.submitJob(
            ali, "Second job", "CUDA", "Example J105", TaskSize.LARGE
        );

        advance(clock, scheduler, 2);

        scheduler.submitJob(
            sara, "Next job", "Python", "Example J106", TaskSize.MEDIUM
        );

        advance(clock, scheduler, 2);

        scheduler.submitJob(
            hamad, "Next job", "CUDA", "Example J107", TaskSize.LARGE
        );

        advance(clock, scheduler, 4);

        scheduler.submitJob(
            noura, "Queued medium", "Python", "Example J108", TaskSize.MEDIUM
        );

        advance(clock, scheduler, 2);

        scheduler.submitJob(
            sara, "Queued small", "Python", "Example J109", TaskSize.SMALL
        );

        advance(clock, scheduler, 1);

        Job cancelledQueued = scheduler.submitJob(
            hamad, "Cancelled queued", "CUDA", "Example J110", TaskSize.SMALL
        );

        scheduler.cancelJob(
            cancelledQueued.getJobId(),
            "Hamad",
            "Not needed"
        );

        advance(clock, scheduler, 1);

        check(
            clock.getCurrentTime().equals(T0.plusMinutes(20)),
            "scenario clock reaches 10:20"
        );

        check(scheduler.getRunningJobs().size() == 4, "all four GPUs are busy at 10:20");
        check(scheduler.getQueuePosition("J108") == 1, "J108 is queue position one");
        check(scheduler.getQueuePosition("J109") == 2, "J109 is queue position two");

        check(
            scheduler.getPredictedStartTime("J108").equals(T0.plusMinutes(30)),
            "J108 predicted start is 10:30"
        );

        check(
            scheduler.getPredictedStartTime("J109").equals(T0.plusMinutes(30)),
            "J109 predicted start is 10:30 on the tied second GPU"
        );

        check(
            scheduler.getPredictedWaitMinutes("J108") == 10,
            "J108 remaining wait at 10:20 is ten minutes"
        );

        check(
            scheduler.getPredictedWaitMinutes("J109") == 10,
            "J109 remaining wait at 10:20 is ten minutes"
        );

        check(
            scheduler.getTotalBusyGpuMinutes() == 77,
            "official scenario has 77 busy GPU minutes at 10:20"
        );

        check(
            close(scheduler.getOverallUtilizationPercent(), 96.25),
            "official scenario utilization is 96.25 percent (rounds to 96.3)"
        );

        check(
            cancelledQueued.getStatus() == JobStatus.CANCELLED
                && cancelledQueued.getChargedMinutes() == 0,
            "J110 queued cancellation is retained with no charge"
        );

        // At 10:30, GPU2 and GPU4 become free together; tie goes to lower GPU ID.
        advance(clock, scheduler, 10);

        check(
            scheduler.getJobById("J108").getAssignedGpuId() == 2,
            "J108 gets the lower-ID GPU in the availability tie"
        );

        check(
            scheduler.getJobById("J109").getAssignedGpuId() == 4,
            "J109 gets the other GPU in the tie"
        );
    }

    private static void testCancellation() {
        SimulationClock clock = startedClock();
        Scheduler scheduler = new Scheduler(clock);

        TimeAllowance studentA = new TimeAllowance("S4001", 100);
        TimeAllowance studentB = new TimeAllowance("S4002", 100);

        Job jobA = scheduler.submitJob(
            studentA, "A job", "MATLAB", "Owner test", TaskSize.SMALL
        );

        Job jobB = scheduler.submitJob(
            studentB, "B job", "MATLAB", "Owner test", TaskSize.SMALL
        );

        expect(
            IllegalStateException.class,
            () -> scheduler.cancelJob(jobA.getJobId(), "S4002", "Unauthorized"),
            "student B cannot cancel student A's job"
        );

        check(jobA.getStatus() == JobStatus.RUNNING, "unauthorized cancellation changes nothing");

        scheduler.cancelJob(jobB.getJobId(), "S4002", "Own job");

        check(jobB.getStatus() == JobStatus.CANCELLED, "student can cancel own job");

        check(
            studentB.getUsed() == 0 && studentB.getReserved() == 0,
            "immediate running cancellation charges zero elapsed minutes and releases reservation"
        );

        scheduler.cancelJob(jobA.getJobId(), "admin", "Admin cancellation");

        check(
            jobA.getStatus() == JobStatus.CANCELLED,
            "administrator can cancel any student's job"
        );

        check(
            "admin".equals(jobA.getCancelledBy()),
            "cancelling user is recorded"
        );

        check(
            "Admin cancellation".equals(jobA.getCancelReason()),
            "cancellation reason is recorded"
        );

        SimulationClock queuedClock = startedClock();
        Scheduler queuedScheduler = new Scheduler(queuedClock);
        TimeAllowance queuedAllowance = new TimeAllowance("S4003", 100);

        for (int i = 0; i < 5; i++) {
            queuedScheduler.submitJob(
                queuedAllowance,
                "Queued " + i,
                "MATLAB",
                "Refund",
                TaskSize.SMALL
            );
        }

        Job queued = queuedScheduler.getJobById("J105");

        queuedScheduler.cancelJob(
            "J105",
            "S4003",
            "Queued cancellation"
        );

        check(queued.getStatus() == JobStatus.CANCELLED, "queued job becomes cancelled");
        check(queuedAllowance.getUsed() == 0, "queued cancellation has no charge");

        check(
            queuedAllowance.getReserved() == 20,
            "queued cancellation releases its full five-minute reservation"
        );

        check(
            queuedAllowance.available() == 80,
            "queued cancellation restores available time"
        );

        SimulationClock runningClock = startedClock();
        Scheduler runningScheduler = new Scheduler(runningClock);
        TimeAllowance runningAllowance = new TimeAllowance("S4004", 100);

        Job running = runningScheduler.submitJob(
            runningAllowance,
            "Running cancel",
            "CUDA",
            "Elapsed runtime",
            TaskSize.LARGE
        );

        advance(runningClock, runningScheduler, 7);

        runningScheduler.cancelJob(
            running.getJobId(),
            "S4004",
            "Stop after seven minutes"
        );

        check(
            running.getChargedMinutes() == 7,
            "running cancellation charges elapsed GPU runtime only"
        );

        check(
            runningAllowance.getUsed() == 7 && runningAllowance.getReserved() == 0,
            "running cancellation settles balance exactly once"
        );

        check(
            runningAllowance.available() == 93,
            "remaining allowance is not negative or overcharged"
        );
    }

    private static void testUtilization() {
        SimulationClock clock = startedClock();
        Scheduler scheduler = new Scheduler(clock);

        check(
            Double.isNaN(scheduler.getOverallUtilizationPercent()),
            "overall utilization is N/A before one minute elapses"
        );

        check(
            Double.isNaN(scheduler.getGpuUtilizationPercent(1)),
            "individual GPU utilization is N/A at time zero"
        );

        TimeAllowance allowance = new TimeAllowance("S5001", 100);

        Job job = scheduler.submitJob(
            allowance,
            "One job",
            "MATLAB",
            "Utilization",
            TaskSize.SMALL
        );

        scheduler.advanceMinute();

        check(
            close(scheduler.getOverallUtilizationPercent(), 25.0),
            "one busy GPU for one minute gives 25 percent overall utilization"
        );

        check(
            close(scheduler.getGpuUtilizationPercent(1), 100.0),
            "GPU1 utilization is 100 percent for its first busy minute"
        );

        scheduler.cancelJob(
            job.getJobId(),
            "S5001",
            "Stop test"
        );

        scheduler.advanceMinute();

        check(
            scheduler.getTotalBusyGpuMinutes() == 1,
            "cancelled running time remains in historical busy minutes"
        );

        check(
            close(scheduler.getOverallUtilizationPercent(), 12.5),
            "utilization includes work before cancellation and excludes idle time"
        );
    }

    private static void testDataStoreRoundTrip() throws IOException {
        Path directory = Files.createTempDirectory("gpu-member2-state-");

        try {
            SimulationClock clock = startedClock();
            AccountService accounts = new AccountService();

            accounts.registerStudent(
                "S7001",
                "Storage Student",
                "store@example.com",
                "+971501234567",
                "secret1"
            );

            Student student = accounts.approveRequest("S7001", 100);

            accounts.registerStudent(
                "S7002",
                "Pending Student",
                "pending@example.com",
                "+971501234568",
                "secret2"
            );

            accounts.registerStudent(
                "S7003",
                "Rejected Student",
                "rejected@example.com",
                "+971501234569",
                "secret3"
            );

            accounts.rejectRequest("S7003");

            accounts.grantAdditionalTime(
                "S7001",
                20,
                "Storage test grant",
                clock.getCurrentTime()
            );

            Scheduler scheduler = new Scheduler(clock);

            for (int i = 0; i < 5; i++) {
                scheduler.submitJob(
                    student.getAllowance(),
                    "Saved job " + i,
                    "MATLAB",
                    "Storage round trip",
                    TaskSize.SMALL
                );
            }

            advance(clock, scheduler, 2);

            DataStore store = new DataStore(directory);
            store.save(accounts, scheduler);

            DataStore.State restored = store.load();

            check(
                restored.scheduler().getClock().getCurrentTime().equals(T0.plusMinutes(2)),
                "DataStore restores the simulation clock"
            );

            check(
                restored.accounts().getStudentById("S7001") != null,
                "DataStore restores approved students"
            );

            check(
                restored.accounts().authenticate("S7001", "secret1") instanceof Student,
                "restored student credentials remain usable"
            );

            check(
                restored.accounts().getPendingRequests().size() == 1,
                "DataStore restores pending registration requests"
            );

            check(
                restored.accounts().getRequests().stream()
                    .anyMatch(r -> r.getUniversityId().equals("S7003")
                        && r.getStatus() == RequestStatus.REJECTED),
                "DataStore retains rejected request history"
            );

            Student restoredStudent =
                restored.accounts().getStudentById("S7001");

            check(
                restoredStudent.getAllowance().getAllocated() == 120,
                "DataStore restores adjusted allowance allocation"
            );

            check(
                restoredStudent.getAllowance().getGrants().size() == 1,
                "DataStore restores recorded time grants"
            );

            check(
                restored.scheduler().getAllJobs().size() == 5,
                "DataStore restores all jobs"
            );

            check(
                restored.scheduler().getRunningJobs().size() == 4,
                "DataStore restores four running GPU assignments"
            );

            check(
                restored.scheduler().getQueuedJobs().size() == 1,
                "DataStore restores the waiting FCFS queue"
            );

            check(
                restored.scheduler().getPredictedWaitMinutes("J105") == 3,
                "restored queued job's remaining wait is based on restored current time"
            );

            advance(restored.scheduler().getClock(), restored.scheduler(), 3);

            check(
                restored.scheduler().getJobById("J105").getStatus() == JobStatus.RUNNING,
                "restored queued job starts when a GPU finishes"
            );

            check(
                restoredStudent.getAllowance().getUsed() == 20
                    && restoredStudent.getAllowance().getReserved() == 5,
                "restored completion settles balances correctly"
            );

            check(
                restoredStudent.getAllowance().available() == 95,
                "restored allowance available balance is correct"
            );

        } finally {
            deleteDirectory(directory);
        }
    }

    private static SimulationClock startedClock() {
        SimulationClock clock = new SimulationClock();
        clock.setStartTime(T0);
        return clock;
    }

    private static void advance(
            SimulationClock clock,
            Scheduler scheduler,
            int minutes) {

        for (int i = 0; i < minutes; i++) {
            scheduler.advanceMinute();
        }

        check(
            clock.getElapsedMinutes() >= minutes,
            "simulated clock advanced by requested minutes"
        );
    }

    private static void run(String section, CheckedRunnable test) throws Exception {
        System.out.println();
        System.out.println("== " + section);

        int before = passed + failed;

        try {
            test.run();

        } catch (Throwable error) {
            failed++;
            System.out.println("  FAIL  unexpected exception: " + error);
            error.printStackTrace(System.out);
        }

        System.out.println("  Section checks: " + (passed + failed - before));
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + description);

        } else {
            failed++;
            System.out.println("  FAIL  " + description);
        }
    }

    private static void expect(
            Class<? extends RuntimeException> type,
            CheckedRunnable action,
            String description) {

        try {
            action.run();
            check(false, description + " (expected " + type.getSimpleName() + ")");

        } catch (RuntimeException error) {
            check(
                type.isInstance(error),
                description + " (" + error.getClass().getSimpleName() + ")"
            );

        } catch (Exception error) {
            check(
                false,
                description + " (unexpected checked exception " + error + ")"
            );
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001;
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();

            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
