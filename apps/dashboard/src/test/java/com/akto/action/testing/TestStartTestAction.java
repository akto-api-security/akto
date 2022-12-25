package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestStartTestAction extends MongoBasedTest {

    @Test
    public void testStopAllTests() {
        TestingRunDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun1 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.SCHEDULED, 0);

        CustomTestingEndpoints customTestingEndpoints = new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(0, "url", URLMethods.Method.GET)));
        TestingRun testingRun2 = new TestingRun(Context.now(), "", customTestingEndpoints ,0, TestingRun.State.SCHEDULED, 1);

        WorkflowTestingEndpoints workflowTestingEndpoints = new WorkflowTestingEndpoints();
        TestingRun testingRun3 = new TestingRun(Context.now(), "",  workflowTestingEndpoints,1, TestingRun.State.SCHEDULED, 0);

        TestingRun testingRun4 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.RUNNING, 0);
        // already completed test
        TestingRun testingRun5 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.COMPLETED, 0);

        TestingRunDao.instance.insertMany(Arrays.asList(testingRun1, testingRun2, testingRun3, testingRun4, testingRun5 ));

        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING)
        );

        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(filter);
        assertEquals(4, testingRuns.size());

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.stopAllTests();


        testingRuns = TestingRunDao.instance.findAll(filter);
        assertEquals(0, testingRuns.size());

        testingRuns = TestingRunDao.instance.findAll(Filters.eq(TestingRun.STATE, TestingRun.State.COMPLETED));
        assertEquals(1, testingRuns.size());
        assertEquals(testingRun5.getId(), testingRuns.get(0).getId());
    }


    @Test
    public void testFetchTestingRunResultSummaries() {
        TestingRunResultSummariesDao.instance.getMCollection().drop();

        List<TestingRunResultSummary> testingRunResultSummaryList = new ArrayList<>();
        ObjectId testingRunId = new ObjectId();
        TestingRun testingRun = new TestingRun(0, "avneesh@akto.io", new CollectionWiseTestingEndpoints(0), 0,  State.COMPLETED, 0);
        testingRun.setId(testingRunId);
        for (int startTimestamp=0; startTimestamp < 30; startTimestamp++) {
            TestingRunResultSummary testingRunResultSummary = new TestingRunResultSummary(
                startTimestamp, startTimestamp+10, new HashMap<>(), 10, testingRunId, testingRunId.toHexString(), 10
            );

            testingRunResultSummaryList.add(testingRunResultSummary);
        }

        TestingRunDao.instance.insertOne(testingRun);
        TestingRunResultSummariesDao.instance.insertMany(testingRunResultSummaryList);

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.setTestingRunHexId(testingRunId.toHexString());

        String result = startTestAction.fetchTestingRunResultSummaries();
        assertEquals("SUCCESS", result);

        List<TestingRunResultSummary> summariesFromDb = startTestAction.getTestingRunResultSummaries();
        assertEquals(startTestAction.limitForTestingRunResultSummary, summariesFromDb.size());
        assertEquals(29, summariesFromDb.get(0).getStartTimestamp());
        assertEquals(10, summariesFromDb.get(summariesFromDb.size()-1).getStartTimestamp());

    }
}
