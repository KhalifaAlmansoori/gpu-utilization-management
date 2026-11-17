package gpu.model;

import java.time.LocalDateTime;

/**
 * Plain console tests for the model package (no libraries needed).
 * Run:  javac -d out $(find src test -name "*.java")  then  java -cp out gpu.model.ModelTests
 */
public class ModelTests {

    private static int passed = 0;
    private static int failed = 0;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 16, 10, 0);

    public static void main(String[] args) {
        taskSizes();
        jobLifecycle();
        jobIllegalMoves();
        jobCalculations();
        jobSaveLoad();
        allowanceWorkedExample();
        allowanceProtectsItself();
        gpuBehaviour();
        accountsAndRequests();
        fcfsOrdering();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ tests

    static void taskSizes() {
        section("Task sizes");
        check("Small = 5 units/min", TaskSize.SMALL.getWorkloadUnits() == 5 && TaskSize.SMALL.getDurationMinutes() == 5);
        check("Medium = 15", TaskSize.MEDIUM.getDurationMinutes() == 15);
        check("Large = 30", TaskSize.LARGE.getDurationMinutes() == 30);
        check("Very large = 60", TaskSize.VERY_LARGE.getDurationMinutes() == 60);
        check("parse 'Very large'", TaskSize.parse("Very large") == TaskSize.VERY_LARGE);
        check("parse 'LARGE'", TaskSize.parse("LARGE") == TaskSize.LARGE);
        throwsIAE("parse unknown size", () -> TaskSize.parse("Huge"));
    }

    static void jobLifecycle() {
        section("Job life cycle");
        Job j = newJob("J101", TaskSize.MEDIUM, T0);
        check("new job is Queued", j.getStatus() == JobStatus.QUEUED);
        check("queued job has no GPU/start/finish",
                j.getAssignedGpuId() == null && j.getStartTime() == null && j.getFinishTime() == null);
        check("duration equals units", j.getEstimatedDuration() == 15 && j.getWorkloadUnits() == 15);
        check("queued job is cancellable", j.isCancellable());

        j.start(2, T0);
        check("started job is Running on GPU2", j.getStatus() == JobStatus.RUNNING && j.getAssignedGpuId() == 2);
        check("running job: runtime after 5 min", j.getRuntimeMinutes(T0.plusMinutes(5)) == 5);
        check("running job: remaining after 5 min", j.getRemainingRuntime(T0.plusMinutes(5)) == 10);
        check("expected finish 10:15", j.getExpectedFinish().equals(T0.plusMinutes(15)));

        j.complete(T0.plusMinutes(15));
        check("completed", j.getStatus() == JobStatus.COMPLETED);
        check("finish time kept", j.getFinishTime().equals(T0.plusMinutes(15)));
        check("GPU kept in history", j.getAssignedGpuId() == 2);
        check("charge = full runtime 15", j.getChargedMinutes() == 15);
        check("completed job is not cancellable", !j.isCancellable());
    }

    static void jobIllegalMoves() {
        section("Job illegal moves");
        Job queued = newJob("J102", TaskSize.SMALL, T0);
        throwsISE("Queued cannot complete", () -> queued.complete(T0.plusMinutes(5)));

        Job running = newJob("J103", TaskSize.SMALL, T0);
        running.start(1, T0);
        throwsISE("Running cannot start again", () -> running.start(2, T0));
        throwsISE("Cannot complete before duration is run", () -> running.complete(T0.plusMinutes(3)));

        Job done = newJob("J104", TaskSize.SMALL, T0);
        done.start(1, T0);
        done.complete(T0.plusMinutes(5));
        throwsISE("Completed cannot restart", () -> done.start(1, T0.plusMinutes(6)));
        throwsISE("Completed cannot be cancelled", () -> done.cancel("100001", "oops", T0.plusMinutes(6)));

        Job cancelled = newJob("J105", TaskSize.SMALL, T0);
        cancelled.cancel("100001", "changed my mind", T0.plusMinutes(1));
        throwsISE("Cancelled cannot restart", () -> cancelled.start(1, T0.plusMinutes(2)));
        throwsISE("Cancelled cannot cancel twice", () -> cancelled.cancel("admin", "again", T0.plusMinutes(2)));

        Job j = newJob("J106", TaskSize.SMALL, T0);
        throwsIAE("Cancel needs a reason", () -> j.cancel("admin", "   ", T0));
        throwsIAE("Cancel needs a user", () -> j.cancel("", "reason", T0));
        check("job still Queued after refused cancels", j.getStatus() == JobStatus.QUEUED);

        throwsIAE("Empty title refused", () -> new Job("J107", "100001", " ", "App", "d", TaskSize.SMALL, T0));
        throwsIAE("Null size refused", () -> new Job("J108", "100001", "t", "App", "d", null, T0));
        throwsIAE("Null clock refused", () -> new Job("J109", "100001", "t", "App", "d", TaskSize.SMALL, null));
        throwsIAE("Bad job ID refused", () -> new Job("X", "100001", "t", "App", "d", TaskSize.SMALL, T0));
    }

    static void jobCalculations() {
        section("Job calculations (charges and waits)");
        // Cancelled while queued: no charge, no finish time, no GPU
        Job q = newJob("J110", TaskSize.SMALL, T0);
        q.cancel("100001", "not needed", T0.plusMinutes(1));
        check("queued cancel: status Cancelled", q.getStatus() == JobStatus.CANCELLED);
        check("queued cancel: charge 0", q.getChargedMinutes() == 0);
        check("queued cancel: no finish time", q.getFinishTime() == null);
        check("queued cancel: who and why recorded",
                "100001".equals(q.getCancelledBy()) && "not needed".equals(q.getCancelReason())
                        && T0.plusMinutes(1).equals(q.getCancelTime()));
        check("queued cancel: never started", !q.hasStarted() && q.getQueueWaitMinutes() == -1);

        // Cancelled while running after 12 minutes: charge 12
        Job r = newJob("J111", TaskSize.LARGE, T0);
        r.start(3, T0.plusMinutes(2));
        r.cancel("admin", "Wrong input dataset", T0.plusMinutes(14));
        check("running cancel: charge elapsed 12", r.getChargedMinutes() == 12);
        check("running cancel: finish = cancel time", r.getFinishTime().equals(T0.plusMinutes(14)));
        check("running cancel: GPU kept", r.getAssignedGpuId() == 3);
        check("running cancel: runtime 12", r.getRuntimeMinutes(T0.plusMinutes(99)) == 12);
        check("queue wait = start - submission = 2", r.getQueueWaitMinutes() == 2);

        // Running job: not charged until settled
        Job running = newJob("J112", TaskSize.MEDIUM, T0);
        running.start(1, T0);
        check("running job: charge 0 until settled", running.getChargedMinutes() == 0);
        check("queued job: remaining runtime 0", newJob("J113", TaskSize.SMALL, T0).getRemainingRuntime(T0) == 0);
    }

    static void jobSaveLoad() {
        section("Saving and loading a job");
        Job j = new Job("J120", "100052210", "Weather | model", "PyTorch",
                "Line one, with comma\nLine two with a | pipe and a \\ backslash", TaskSize.VERY_LARGE, T0);
        j.start(4, T0.plusMinutes(3));
        j.cancel("admin", "Reason, with | odd chars", T0.plusMinutes(10));
        String line = j.toLine();
        check("saved line is a single line", !line.contains("\n") && !line.contains("\r"));
        Job back = Job.fromLine(line);
        check("round trip: same line again", back.toLine().equals(line));
        check("round trip: description survives", back.getDescription().equals(j.getDescription()));
        check("round trip: reason survives", back.getCancelReason().equals(j.getCancelReason()));
        check("round trip: status and GPU", back.getStatus() == JobStatus.CANCELLED && back.getAssignedGpuId() == 4);
        check("round trip: times", back.getStartTime().equals(j.getStartTime())
                && back.getFinishTime().equals(j.getFinishTime()) && back.getSubmissionTime().equals(T0));

        Job queued = newJob("J121", TaskSize.SMALL, T0);
        check("round trip: queued job", Job.fromLine(queued.toLine()).getStatus() == JobStatus.QUEUED);
        Job running = newJob("J122", TaskSize.SMALL, T0);
        running.start(1, T0);
        check("round trip: running job", Job.fromLine(running.toLine()).getAssignedGpuId() == 1);

        throwsIAE("bad date in file is reported", () -> Job.fromLine(
                "J1|100001|t|a|d|SMALL|16-11-26|QUEUED|||||| ".trim()));
        throwsIAE("wrong field count is reported", () -> Job.fromLine("J1|100001|t"));
        throwsIAE("status/field mismatch is reported", () -> Job.fromLine(
                "J123|100001|t|a|d|SMALL|16/11/2026 10:00|COMPLETED||||||"));
    }

    static void allowanceWorkedExample() {
        section("Allowance: the guide's worked example");
        TimeAllowance a = new TimeAllowance("100052210", 100);
        check("approved: 100/0/0 -> 100", state(a, 100, 0, 0, 100));
        a.reserve(15);
        check("submit A (15): 100/0/15 -> 85", state(a, 100, 0, 15, 85));
        a.reserve(30);
        check("submit B (30): 100/0/45 -> 55", state(a, 100, 0, 45, 55));
        a.settle(15, 15);
        check("A completes: 100/15/30 -> 55 (available unchanged)", state(a, 100, 15, 30, 55));
        a.settle(30, 12);
        check("B cancelled after 12: 100/27/0 -> 73", state(a, 100, 27, 0, 73));
        a.reserve(60);
        check("submit C (60): 100/27/60 -> 13", state(a, 100, 27, 60, 13));
        throwsISE("D (30) rejected: 30 > 13", () -> a.reserve(30));
        check("balance untouched after rejection", state(a, 100, 27, 60, 13));
        a.grant(20, "Extra runs for final report", "admin", T0);
        check("grant +20: 120/27/60 -> 33", state(a, 120, 27, 60, 33));
        check("grant recorded with reason", a.getGrants().size() == 1
                && a.getGrants().get(0).getReason().equals("Extra runs for final report")
                && a.getGrants().get(0).getGrantedBy().equals("admin"));

        TimeAllowance exact = new TimeAllowance("100001", 100);
        exact.reserve(100);
        check("exact match accepted: available 0", exact.available() == 0);
        throwsISE("zero available blocks a new job", () -> exact.reserve(5));
        exact.release(100);
        check("queued cancel releases without charge", state(exact, 100, 0, 0, 100));
    }

    static void allowanceProtectsItself() {
        section("Allowance: balance can never go negative");
        TimeAllowance a = new TimeAllowance("100001", 50);
        a.reserve(40);
        throwsISE("cannot reserve more than available", () -> a.reserve(11));
        throwsIAE("cannot reserve 0", () -> a.reserve(0));
        throwsIAE("cannot reserve negative", () -> a.reserve(-5));
        throwsISE("cannot release more than reserved", () -> a.release(41));
        throwsISE("cannot charge more than available", () -> a.charge(11));
        throwsIAE("cannot charge more than reservation in settle", () -> a.settle(10, 11));
        throwsISE("cannot settle more than reserved", () -> a.settle(41, 41));
        throwsIAE("grant needs a reason", () -> a.grant(10, " ", "admin", T0));
        throwsIAE("grant must be positive", () -> a.grant(0, "r", "admin", T0));
        throwsIAE("cannot set allocation below used+reserved", () -> a.setAllocated(39));
        check("balance unchanged after all refusals", state(a, 50, 0, 40, 10));
        a.setAllocated(60);
        check("admin can raise allocation", a.available() == 20);
        throwsIAE("cannot restore inconsistent numbers", () -> new TimeAllowance("100001", 10, 8, 5));
    }

    static void gpuBehaviour() {
        section("GPU");
        GPU gpu = new GPU(2);
        check("new GPU is free", gpu.isFree() && gpu.getCurrentJob() == null && gpu.getExpectedFinish() == null);
        check("free GPU: free-at = now", gpu.getFreeAt(T0).equals(T0));
        Job j = newJob("J130", TaskSize.MEDIUM, T0);
        gpu.assign(j, T0);
        check("assigned: busy, job Running on GPU2", !gpu.isFree() && j.getStatus() == JobStatus.RUNNING && j.getAssignedGpuId() == 2);
        check("remaining 15", gpu.getRemainingRuntime(T0) == 15);
        check("busy GPU: free-at = finish time", gpu.getFreeAt(T0.plusMinutes(4)).equals(T0.plusMinutes(15)));
        throwsISE("busy GPU refuses a second job", () -> gpu.assign(newJob("J131", TaskSize.SMALL, T0), T0));
        check("freeing returns the job", gpu.release() == j && gpu.isFree());

        // restore after restart
        GPU other = new GPU(2);
        other.restore(j); // j is still Running on GPU2, as after a restart
        check("restore puts a running job back", !other.isFree());
        throwsIAE("restore refuses a job from another GPU", () -> new GPU(3).restore(j));
        throwsIAE("GPU ID 0 refused", () -> new GPU(0));
    }

    static void accountsAndRequests() {
        section("Users, requests, administrator");
        Administrator admin = new Administrator();
        check("admin username/password", admin.getUsername().equals("admin") && admin.checkPassword("gpu2026"));
        check("admin wrong password", !admin.checkPassword("wrong"));
        check("admin role", admin.getRole().equals("Administrator"));

        AccessRequest req = new AccessRequest("100061477", "Omar Khalid", "omar.k@ku.ac.ae", "0501234567", "secret1");
        check("new request is Pending", req.isPending());
        Student omar = admin.approve(req, 100);
        check("approved: request Approved, student created", req.getStatus() == RequestStatus.APPROVED
                && omar.getUniversityId().equals("100061477") && omar.getUsername().equals("100061477"));
        check("student starts with 100 minutes", omar.getAllowance().getAllocated() == 100 && omar.getAllowance().available() == 100);
        check("student password works", omar.checkPassword("secret1") && !omar.checkPassword("nope"));
        check("student role", omar.getRole().equals("Student"));
        throwsISE("cannot approve twice", () -> admin.approve(req, 100));
        throwsISE("cannot reject after approval", () -> admin.reject(req));

        AccessRequest req2 = new AccessRequest("100063052", "Lina Hassan", "lina.h@ku.ac.ae", "0559876543", "pw");
        admin.reject(req2);
        check("rejected request", req2.getStatus() == RequestStatus.REJECTED);
        throwsISE("rejected request cannot become an account", () -> req2.toStudent(100));
        throwsISE("cannot approve a rejected request", () -> admin.approve(req2, 100));

        AccessRequest req3 = new AccessRequest("100070000", "Test", "t@ku.ac.ae", "0500000000", "pw");
        throwsIAE("approval needs positive minutes", () -> admin.approve(req3, 0));
        check("failed approval leaves request Pending", req3.isPending());
        throwsIAE("empty field in request refused", () -> new AccessRequest("", "N", "e", "p", "pw"));

        TimeAllowance.Grant g = admin.grantTime(omar, 20, "Final report", T0);
        check("admin grant raises allocation", omar.getAllowance().getAllocated() == 120 && g.getGrantedBy().equals("admin"));

        // saving
        Student back = Student.fromLine(omar.toLine());
        check("student round trip keeps balances", back.getAllowance().getAllocated() == 120
                && back.getUniversityId().equals("100061477") && back.checkPassword("secret1"));
        AccessRequest backReq = AccessRequest.fromLine(req2.toLine());
        check("request round trip keeps status", backReq.getStatus() == RequestStatus.REJECTED);
        TimeAllowance.Grant backGrant = TimeAllowance.Grant.fromLine(g.toLine());
        back.getAllowance().restoreGrant(backGrant);
        check("grant round trip", backGrant.getReason().equals("Final report") && backGrant.getTime().equals(T0)
                && back.getAllowance().getAllocated() == 120);

        // polymorphism: one list, same calls
        java.util.List<User> users = java.util.List.of(admin, omar);
        check("polymorphism: different roles through User", users.get(0).getRole().equals("Administrator")
                && users.get(1).getRole().equals("Student"));
        // one loop saves every Storable
        java.util.List<Storable> storables = java.util.List.of(omar, req, g, newJob("J140", TaskSize.SMALL, T0));
        boolean allOneLine = true;
        for (Storable s : storables) {
            allOneLine &= !s.toLine().isEmpty() && !s.toLine().contains("\n");
        }
        check("polymorphism: one loop saves every Storable", allOneLine);
    }

    static void fcfsOrdering() {
        section("FCFS ordering (collections)");
        java.util.List<Job> jobs = new java.util.ArrayList<>();
        jobs.add(newJob("J106", TaskSize.MEDIUM, T0.plusMinutes(10)));
        jobs.add(newJob("J110", TaskSize.SMALL, T0.plusMinutes(10)));
        jobs.add(newJob("J102", TaskSize.MEDIUM, T0));
        jobs.add(newJob("J105", TaskSize.LARGE, T0.plusMinutes(8)));
        java.util.Collections.sort(jobs);
        StringBuilder order = new StringBuilder();
        for (Job j : jobs) {
            order.append(j.getJobId()).append(' ');
        }
        check("sorted by time, then ID: " + order.toString().trim(),
                order.toString().trim().equals("J102 J105 J106 J110"));
        java.util.PriorityQueue<Job> queue = new java.util.PriorityQueue<>(jobs);
        check("PriorityQueue head is first in line", queue.peek().getJobId().equals("J102"));
        check("numeric ID compare (J99 before J100)", newJob("J99", TaskSize.SMALL, T0).compareTo(newJob("J100", TaskSize.SMALL, T0)) < 0);
    }

    // ------------------------------------------------------------------ helpers

    static Job newJob(String id, TaskSize size, LocalDateTime submitted) {
        return new Job(id, "100052210", "Title " + id, "PyTorch", "Description of " + id, size, submitted);
    }

    static boolean state(TimeAllowance a, int allocated, int used, int reserved, int available) {
        return a.getAllocated() == allocated && a.getUsed() == used
                && a.getReserved() == reserved && a.available() == available;
    }

    static void section(String name) {
        System.out.println();
        System.out.println("== " + name);
    }

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    static void throwsISE(String name, Runnable r) {
        throwsType(name, r, IllegalStateException.class);
    }

    static void throwsIAE(String name, Runnable r) {
        throwsType(name, r, IllegalArgumentException.class);
    }

    static void throwsType(String name, Runnable r, Class<? extends RuntimeException> type) {
        try {
            r.run();
            check(name + " (expected " + type.getSimpleName() + ")", false);
        } catch (RuntimeException e) {
            check(name + " -> \"" + e.getMessage() + "\"", type.isInstance(e));
        }
    }
}
