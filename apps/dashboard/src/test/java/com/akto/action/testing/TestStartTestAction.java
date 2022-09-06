package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingSchedulesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.*;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestStartTestAction extends MongoBasedTest {

    @Test
    public void testStopAllTests() {
        TestingRunDao.instance.getMCollection().drop();
        TestingSchedulesDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun1 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.SCHEDULED);

        CustomTestingEndpoints customTestingEndpoints = new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(0, "url", URLMethods.Method.GET)));
        TestingRun testingRun2 = new TestingRun(Context.now(), "", customTestingEndpoints ,0, TestingRun.State.SCHEDULED);

        WorkflowTestingEndpoints workflowTestingEndpoints = new WorkflowTestingEndpoints();
        TestingRun testingRun3 = new TestingRun(Context.now(), "",  workflowTestingEndpoints,1, TestingRun.State.SCHEDULED);

        TestingRun testingRun4 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.RUNNING);

        TestingSchedule testingSchedule1 = new TestingSchedule("", Context.now(), "", 0, 0, Context.now(), false, testingRun1);
        TestingSchedule testingSchedule2 = new TestingSchedule("", Context.now(), "", 0, 1,Context.now(), false, testingRun2);

        TestingRunDao.instance.insertMany(Arrays.asList(testingRun1, testingRun2, testingRun3, testingRun4 ));
        TestingSchedulesDao.instance.insertMany(Arrays.asList(testingSchedule1, testingSchedule2));

        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING)
        );

        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(filter);
        assertEquals(4, testingRuns.size());
        List<TestingSchedule> testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        assertEquals(2, testingSchedules.size());

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.stopAllTests();


        testingRuns = TestingRunDao.instance.findAll(filter);
        assertEquals(0, testingRuns.size());
        testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        assertEquals(0, testingSchedules.size());

    }
}
