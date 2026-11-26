package com.akto.data_actor;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TrrsReclaimWindowTest extends MongoBasedTest {

    private static final String MODULE = "pod-a";
    private ObjectId testingRunId;

    @Before
    public void setUp() {
        TestingRunDao.instance.getMCollection().drop();
        TestingRunResultSummariesDao.instance.getMCollection().drop();
        TestingRun testingRun = new TestingRun();
        testingRunId = new ObjectId();
        testingRun.setId(testingRunId);
        testingRun.setState(TestingRun.State.RUNNING);
        testingRun.setMiniTestingServiceName(MODULE);
        TestingRunDao.instance.insertOne(testingRun);
    }

    // inserted the way the abstractor's insertTestingRunResultSummary does: a POJO, so leaseExpiryTs is written as 0
    private ObjectId insertRunningSummary(int startTimestamp, int leaseExpiryTs) {
        TestingRunResultSummary trrs = new TestingRunResultSummary();
        ObjectId id = new ObjectId();
        trrs.setId(id);
        trrs.setTestingRunId(testingRunId);
        trrs.setState(TestingRun.State.RUNNING);
        trrs.setStartTimestamp(startTimestamp);
        trrs.setLeaseExpiryTs(leaseExpiryTs);
        TestingRunResultSummariesDao.instance.insertOne(trrs);
        return id;
    }

    private TestingRunResultSummary poll(int now, int delta, int reclaimAfterSeconds) {
        return DbLayer.findPendingTestingRunResultSummary(now, delta, MODULE, null, 0, reclaimAfterSeconds);
    }

    @Test
    public void defaultWindowReclaimsSummaryOlderThanFiveMinutes() {
        int now = Context.now();
        ObjectId id = insertRunningSummary(now - 6 * 60, 0);

        TestingRunResultSummary claimed = poll(now, now - 20 * 60, 0);

        assertNotNull(claimed);
        assertEquals(id, claimed.getId());
    }

    @Test
    public void legacyOverloadKeepsFiveMinuteDefault() {
        int now = Context.now();
        insertRunningSummary(now - 4 * 60, 0);
        assertNull(DbLayer.findPendingTestingRunResultSummary(now, now - 20 * 60, MODULE, null, 0));

        ObjectId id = insertRunningSummary(now - 6 * 60, 0);
        TestingRunResultSummary claimed = DbLayer.findPendingTestingRunResultSummary(now, now - 20 * 60, MODULE, null, 0);
        assertNotNull(claimed);
        assertEquals(id, claimed.getId());
    }

    @Test
    public void customWindowLeavesYoungerSummaryAlone() {
        int now = Context.now();
        insertRunningSummary(now - 6 * 60, 0);

        assertNull(poll(now, now - 45 * 60, 30 * 60));
    }

    @Test
    public void customWindowReclaimsOnceOlderThanWindow() {
        int now = Context.now();
        ObjectId id = insertRunningSummary(now - 31 * 60, 0);

        TestingRunResultSummary claimed = poll(now, now - 45 * 60, 30 * 60);

        assertNotNull(claimed);
        assertEquals(id, claimed.getId());
    }

    @Test
    public void zeroLeaseFromLeaseUnawareModuleIsNotReclaimedImmediately() {
        int now = Context.now();
        insertRunningSummary(now - 30, 0);

        assertNull(poll(now, now - 20 * 60, 0));
    }

    @Test
    public void missingLeaseFieldIsNotReclaimedImmediately() {
        int now = Context.now();
        // created by createTRRSummaryIfAbsent's upsert: no leaseExpiryTs field at all
        TestingRunResultSummariesDao.instance.getMCollection().withDocumentClass(Document.class).insertOne(
                new Document("_id", new ObjectId())
                        .append(TestingRunResultSummary.TESTING_RUN_ID, testingRunId)
                        .append(TestingRunResultSummary.STATE, TestingRun.State.RUNNING.name())
                        .append(TestingRunResultSummary.START_TIMESTAMP, now - 30));

        assertNull(poll(now, now - 20 * 60, 0));
    }

    @Test
    public void expiredRealLeaseIsReclaimedRegardlessOfAge() {
        int now = Context.now();
        ObjectId id = insertRunningSummary(now - 30, now - 10);

        TestingRunResultSummary claimed = poll(now, now - 20 * 60, 30 * 60);

        assertNotNull(claimed);
        assertEquals(id, claimed.getId());
    }

    @Test
    public void liveLeaseOnYoungSummaryIsNotReclaimed() {
        int now = Context.now();
        insertRunningSummary(now - 30, now + 60);

        assertNull(poll(now, now - 20 * 60, 0));
    }
}
