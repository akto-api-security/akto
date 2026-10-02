package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.mongodb.client.model.Filters;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * HTTP-layer smoke test for the claimNextTestWork action - DbLayer.claimNextTestWork itself has a
 * thorough suite (TestClaimNextTestWork in libs/utils), but nothing there exercises the action
 * wrapper: request-field binding, the testingRunHexId conversion done only in this class, or that
 * the action returns SUCCESS/ERROR correctly. This is the only layer mini-testing (a separate
 * codebase, talking to this one solely over HTTP) actually depends on.
 */
public class TestClaimNextTestWorkAction extends MongoBasedTest {

    private static final String MODULE = "akto-testing-module";

    @Before
    public void clearCollections() {
        Context.accountId.set(ACCOUNT_ID);
        TestingRunDao.instance.getMCollection().deleteMany(Filters.empty());
        TestingRunResultSummariesDao.instance.getMCollection().deleteMany(Filters.empty());
    }

    @Test
    public void claimNextTestWork_bindsFieldsAndReturnsSuccess_freshRun() {
        TestingRun run = new TestingRun();
        run.setId(new ObjectId());
        run.setState(State.SCHEDULED);
        run.setScheduleTimestamp(Context.now() - 100);
        run.setMiniTestingServiceName(MODULE);
        TestingRunDao.instance.insertOne(run);

        DbAction action = new DbAction();
        action.setMiniTestingName(MODULE);
        action.setLeaseToken("action-token-1");
        action.setLeaseSeconds(360);

        String result = action.claimNextTestWork();

        assertEquals("SUCCESS", result);
        assertEquals(DbLayer.VERDICT_FRESH_RUN, action.getVerdict());
        assertNotNull(action.getTrrs());
        assertNotNull(action.getTestingRun());
        assertEquals(run.getId(), action.getTestingRun().getId());
        // the one conversion step that lives only in the action, not in DbLayer - a JSON consumer
        // gets a hex string, never a raw ObjectId.
        assertEquals(run.getId().toHexString(), action.getTrrs().getTestingRunHexId());
    }

    @Test
    public void claimNextTestWork_returnsSuccessWithNoWorkFound_whenNothingEligible() {
        DbAction action = new DbAction();
        action.setMiniTestingName(MODULE);
        action.setLeaseToken("action-token-2");
        action.setLeaseSeconds(360);

        String result = action.claimNextTestWork();

        assertEquals("SUCCESS", result);
        assertEquals(DbLayer.VERDICT_NO_WORK_FOUND, action.getVerdict());
        assertNull(action.getTrrs());
        assertNull(action.getTestingRun());
    }
}
