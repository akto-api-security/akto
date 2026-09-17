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
     * particular operation. Kept short so an abandoned attempt is reclaimable quickly.
     */
    public static final int LEASE_SECONDS = 90;
    private static final int RENEW_INTERVAL_SECONDS = LEASE_SECONDS / 3;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingLease.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final TestingLease INSTANCE = new TestingLease();

    private volatile String token;
    private volatile int lastRenewedAt;
    private volatile boolean rejected;

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
    }

    public void clear() {
        this.token = null;
        this.lastRenewedAt = 0;
        this.rejected = false;
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
