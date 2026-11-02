package com.akto.action;

import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.utils.MongoBasedTest;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.struts2.json.JSONPopulator;
import org.apache.struts2.json.JSONUtil;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InsertTestingRunResultSummaryTest extends MongoBasedTest {

    @Before
    public void clear() {
        TestingRunResultSummariesDao.instance.getMCollection().drop();
    }

    // Same shape mini-testing's ClientActor sends: ObjectIds via plain Gson, plus hex ids
    private static String clientBody(ObjectId id, ObjectId testingRunId, ObjectId originalSummaryId, boolean withHexIds) {
        TestingRunResultSummary trrs = new TestingRunResultSummary();
        trrs.setId(id);
        trrs.setTestingRunId(testingRunId);
        trrs.setOriginalTestingRunResultSummaryId(originalSummaryId);
        trrs.setStartTimestamp(1791300000);
        trrs.setState(TestingRun.State.RUNNING);
        BasicDBObject obj = new BasicDBObject();
        if (withHexIds) {
            trrs.setTestingRunHexId(testingRunId.toHexString());
            if (originalSummaryId != null) {
                trrs.setOriginalTestingRunResultSummaryHexId(originalSummaryId.toHexString());
            }
            obj.put("summaryId", id.toHexString());
        }
        obj.put("trrs", trrs);
        return new Gson().toJson(obj);
    }

    private static String insert(String body) throws Exception {
        DbAction action = new DbAction();
        new JSONPopulator().populateObject(action, (Map) JSONUtil.deserialize(body));
        return action.insertTestingRunResultSummary();
    }

    private static List<TestingRunResultSummary> summariesOf(ObjectId testingRunId) {
        return TestingRunResultSummariesDao.instance.findAll(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId));
    }

    @Test
    public void retrySummaryKeepsClientIds() throws Exception {
        ObjectId id = new ObjectId();
        ObjectId testingRunId = new ObjectId();

        assertEquals("SUCCESS", insert(clientBody(id, testingRunId, null, true)));

        List<TestingRunResultSummary> saved = summariesOf(testingRunId);
        assertEquals(1, saved.size());
        assertEquals(id, saved.get(0).getId());
        assertNull(saved.get(0).getOriginalTestingRunResultSummaryId());
    }

    @Test
    public void selectedTestsRerunRetryKeepsOriginalSummaryLink() throws Exception {
        ObjectId id = new ObjectId();
        ObjectId testingRunId = new ObjectId();
        ObjectId originalSummaryId = new ObjectId();

        assertEquals("SUCCESS", insert(clientBody(id, testingRunId, originalSummaryId, true)));

        List<TestingRunResultSummary> saved = summariesOf(testingRunId);
        assertEquals(1, saved.size());
        assertEquals(id, saved.get(0).getId());
        assertEquals(originalSummaryId, saved.get(0).getOriginalTestingRunResultSummaryId());
    }

    @Test
    public void oldClientWithoutHexIdsIsStillAccepted() throws Exception {
        assertEquals("SUCCESS", insert(clientBody(new ObjectId(), new ObjectId(), null, false)));
        assertEquals(1, TestingRunResultSummariesDao.instance.count(Filters.empty()));
    }
}
