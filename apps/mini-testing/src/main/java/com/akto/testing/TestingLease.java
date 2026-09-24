package com.akto.testing;

import java.util.UUID;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.data_actor.LeaseStatus;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Ownership of the test run attempt this pod is executing.
 *
 * Replaces the discriminator that TestingStateStore's local file used to provide. The file could
 * only ever be read by the pod that wrote it, which made "am I the one running this?" trivially
 * answerable and also made the answer vanish whenever the filesystem did. A lease in mongo is
 * readable by every pod, so a run abandoned by a dead pod becomes recoverable - at the cost of
 * having to say explicitly what the file said implicitly.
 *
 * The token is minted per claim rather than per pod. A pod that restarts and re-claims the same
 * summary gets a fresh token, so any thread still running from its previous incarnation is fenced
 * out by the same mechanism that fences a different pod.
 */
public class TestingLease {

    /**
     * Renewal rides writes that already happen (per result while draining, and the produce loop
     * during fan-out), so the TTL only has to outlast the gap between two of those - not any
     * particular operation.
     *
     * The floor for that gap is MINI_TESTING_TASK_TIMEOUT_SECONDS (ConsumerUtil's per-test
     * timeout, 300s): a straggler test produces no renewal-eligible write until it either
     * completes or gets force-timed-out at that mark, so a perfectly healthy pod can legitimately
     * go up to ~300s between renewals. 90s was shorter than that floor and self-fenced healthy
     * pods - confirmed live: a synchronized-logging contention stall (SLF4J SimpleLogger's shared
     * PrintStream lock, contended across 100 concurrent workers) produced real 60-90s stretches
     * with zero completions and no exception, which is indistinguishable from a dead pod under a
     * TTL that tight. 360s clears that 300s floor with margin and comfortably covers every stall
     * actually observed (up to ~74s) without giving up fast reclaim of a truly dead pod relative
     * to typical run durations (30-3600s).
     *
     * Overridable via MINI_TESTING_LEASE_SECONDS - accounts large enough that apiWiseInit's own
     * pre-fan-out work (sample/status-code/auth-prefetch calls, none of which renew) exceeds this
     * on its own need a higher floor than what fits every account by default.
     */
    public static final int LEASE_SECONDS = Integer.parseInt(System.getenv().getOrDefault("MINI_TESTING_LEASE_SECONDS", "360"));
    private static final int RENEW_INTERVAL_SECONDS = LEASE_SECONDS / 3;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingLease.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final TestingLease INSTANCE = new TestingLease();

    private volatile String token;
    private volatile int lastRenewedAt;
    private volatile boolean rejected;

    /**
     * Distinct from {@link #rejected}: this is "a renewal-bearing write was attempted and the
     * call itself threw" (network/abstractor failure), not "the server answered and said no."
     * Without it, a failing write path and a genuinely idle consumer look identical from here -
     * both just show as lastRenewedAt not advancing. Set by {@link #recordAttemptFailed}, called
     * from the write call sites that would otherwise let the exception propagate uncaught past
     * this class entirely.
     */
    private volatile int lastAttemptFailedAt;
    private volatile String lastAttemptFailureSummary;

    private TestingLease() {}

    public static TestingLease getInstance() {
        return INSTANCE;
    }

    /** Minted before claiming; the server stamps it only if the claim succeeds. */
    public String mintToken() {
        return UUID.randomUUID().toString();
    }

    /** Called once a claim has come back successful, starting the TTL clock. */
    public void adopt(String token) {
        this.token = token;
        this.lastRenewedAt = Context.now();
        this.rejected = false;
        this.lastAttemptFailedAt = 0;
        this.lastAttemptFailureSummary = null;
    }

    public void clear() {
        this.token = null;
        this.lastRenewedAt = 0;
        this.rejected = false;
        this.lastAttemptFailedAt = 0;
        this.lastAttemptFailureSummary = null;
    }

    public String getToken() {
        return token;
    }

    /**
     * Folds the outcome of a lease-bearing write into our own view of ownership.
     * UNKNOWN is deliberately not treated as a loss here - {@link #isLost()} handles it by time.
     */
    public void record(LeaseStatus status) {
        if (status == null) {
            return;
        }
        if (status == LeaseStatus.APPLIED) {
            this.lastRenewedAt = Context.now();
        } else if (status == LeaseStatus.REJECTED) {
            if (!this.rejected) {
                loggerMaker.errorAndAddToDb("Lease rejected - another module has taken over this test run, stopping work");
            }
            this.rejected = true;
        }
    }

    /**
     * True once we can no longer claim to own the attempt: either the server told us so, or we
     * have gone a full TTL without a confirmed renewal. The second case needs no retry policy and
     * no failure counting - if we could not renew within the TTL then the lease has expired
     * server-side whether or not we ever heard back, so a pod partitioned from the abstractor
     * stands down on its own rather than carrying on unfenced.
     */
    public boolean isLost() {
        if (token == null) {
            return false;
        }
        if (rejected) {
            return true;
        }
        return Context.now() - lastRenewedAt > LEASE_SECONDS;
    }

    /** A write attempt threw before it could reach {@link #record}, e.g. a network/abstractor
     *  failure - called from the write call sites themselves, which still rethrow afterward. */
    public void recordAttemptFailed(Exception e) {
        this.lastAttemptFailedAt = Context.now();
        this.lastAttemptFailureSummary = e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * Best-effort classification of why {@link #isLost()} is about to be true, for the
     * self-fencing log line - so the next incident's cause is visible from the log alone rather
     * than requiring a live jstack. Checked in priority order: an explicit rejection means
     * another module has already taken over, which isn't a failure at all; a recent failed write
     * attempt points at the write path (abstractor/network); anything else is unattributed and
     * says so rather than guessing.
     */
    public String describeWhyLost() {
        if (rejected) {
            return "another module's claim was accepted - this pod's token was rejected";
        }
        int secondsSinceRenewal = Context.now() - lastRenewedAt;
        if (lastAttemptFailedAt > 0 && Context.now() - lastAttemptFailedAt < LEASE_SECONDS) {
            return "last renewal-bearing write attempt failed " + (Context.now() - lastAttemptFailedAt)
                    + "s ago (" + lastAttemptFailureSummary + ") - likely abstractor/network, not the consumer";
        }
        return "no successful renewal for " + secondsSinceRenewal
                + "s and no failed write attempt on record - cause not attributable from here alone";
    }

    /**
     * Voluntarily gives up the lease before it would naturally expire - for when this pod already
     * knows for certain it is done (e.g. the consumer engine reported closed/failed on its own),
     * so whoever reclaims the summary next doesn't have to wait out the remaining TTL first.
     *
     * Deliberately does not route the response through {@link #record} - a successful release
     * still reads back as an applied write (the CAS matched), and folding that into lastRenewedAt
     * would look like a renewal, undoing the very release this method exists to perform. Nothing
     * about this pod's own lease state needs to reflect the outcome, since it is about to exit
     * either way; this is purely the server-side side effect. Best-effort: if the call itself
     * fails, isLost()'s own TTL is still there underneath as the backstop, exactly as if this
     * method had never been called.
     */
    public void release(String summaryHexId) {
        String currentToken = this.token;
        if (currentToken == null || summaryHexId == null) {
            return;
        }
        try {
            dataActor.updateTestResultsCountInTestSummary(summaryHexId, 0, currentToken, -1);
        } catch (Exception e) {
            loggerMaker.warnAndAddToDb("Best-effort lease release failed for " + summaryHexId + ": " + e.getMessage());
        }
    }

    /**
     * Renewal for phases that write nothing else - chiefly fan-out, which can run for many minutes
     * without producing a single result. Cheap to call in a tight loop: it does nothing until the
     * renewal interval has elapsed.
     *
     * Renewing from the thread doing the work is the point. A background timer would keep the
     * lease alive for a pod wedged mid-request, which is exactly the pod that ought to lose it.
     */
    public void renewIfDue(String summaryHexId) {
        String currentToken = this.token;
        if (currentToken == null || summaryHexId == null) {
            return;
        }
        if (Context.now() - lastRenewedAt < RENEW_INTERVAL_SECONDS) {
            return;
        }
        // a zero increment: this call exists for the lease extension the server performs alongside it
        record(dataActor.updateTestResultsCountInTestSummary(summaryHexId, 0, currentToken, LEASE_SECONDS));
    }
}
