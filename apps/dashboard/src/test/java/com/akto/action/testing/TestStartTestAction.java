package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.action.ApiTokenAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiTokensDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiToken;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.URLMethods;
import com.akto.filter.UserDetailsFilter;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestStartTestAction extends MongoBasedTest {

    @Test
    public void testStopAllTests() {
        TestingRunDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun1 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.SCHEDULED, 0, "test", "");

        CustomTestingEndpoints customTestingEndpoints = new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(0, "url", URLMethods.Method.GET)));
        TestingRun testingRun2 = new TestingRun(Context.now(), "", customTestingEndpoints ,0, TestingRun.State.SCHEDULED, 1, "test", "");

        WorkflowTestingEndpoints workflowTestingEndpoints = new WorkflowTestingEndpoints();
        TestingRun testingRun3 = new TestingRun(Context.now(), "",  workflowTestingEndpoints,1, TestingRun.State.SCHEDULED, 0, "test", "");

        TestingRun testingRun4 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.RUNNING, 0, "test", "");
        // already completed test
        TestingRun testingRun5 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.COMPLETED, 0, "test", "");

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
        TestingRun testingRun = new TestingRun(0, "avneesh@akto.io", new CollectionWiseTestingEndpoints(0), 0,  State.COMPLETED, 0, "test", "");
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

    @Test
    public void testStartTest() {
        TestingRunDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.COMPLETED, 0, "test", "");
        TestingRunDao.instance.insertOne(testingRun);
        String testingRunHexId = testingRun.getHexId();

        assertEquals(1,TestingRunDao.instance.findAll(new BasicDBObject()).size());

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.setSession(new HashMap<>());
        startTestAction.setTestingRunHexId(testingRunHexId);
        startTestAction.startTest();

        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(new BasicDBObject());
        assertEquals(1,testingRuns.size());

        testingRun = testingRuns.get(0);
        assertEquals(State.SCHEDULED, testingRun.getState());

        TestingRunDao.instance.updateOne(Constants.ID, new ObjectId(testingRunHexId), Updates.set(TestingRun.STATE, State.COMPLETED.toString()));

        int startTimestamp = Context.now() + 10000;
        startTestAction.setStartTimestamp(startTimestamp);

        startTestAction.startTest();
        testingRun = TestingRunDao.instance.findOne(Constants.ID, new ObjectId(testingRunHexId));
        assertEquals(State.SCHEDULED, testingRun.getState());
        assertEquals(startTimestamp, testingRun.getScheduleTimestamp());

    }

    @Test
    public void testStartCICDTest() throws IOException, ServletException {
        TestingRunDao.instance.getMCollection().drop();
        ApiTokensDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        AccountSettingsDao.instance.getMCollection().drop();
        TestingRunResultSummariesDao.instance.getMCollection().drop();

        // create an CICD API token, mock a server request with it and check if it recognizes it.
        
        UserAccountEntry userAccountEntry = new UserAccountEntry();
        userAccountEntry.setAccountId(ACCOUNT_ID);
        userAccountEntry.setDefault(true);
        Map<String, UserAccountEntry> accountAccessMap = new HashMap<>();
        accountAccessMap.put(ACCOUNT_ID+"", userAccountEntry);
        
        User user = new User();
        user.setLogin("test@akto.io");
        user.setAccounts(accountAccessMap);

        UsersDao.instance.insertOne(user);
        AccountSettings acc = new AccountSettings();
        acc.setDashboardVersion("test - test - test");
        acc.setId(ACCOUNT_ID);
        AccountSettingsDao.instance.insertOne(acc);
        
        Map<String,Object> userSession = new HashMap<>();
        userSession.put("user",user);
        
        ApiTokenAction apiTokenAction = new ApiTokenAction();
        apiTokenAction.setSession(userSession);
        apiTokenAction.setTokenUtility(Utility.CICD);
        String res = apiTokenAction.addApiToken();
        
        assertEquals(Action.SUCCESS.toUpperCase(), res);

        List<ApiToken> apiTokens = apiTokenAction.getApiTokenList();
        assertEquals(1, apiTokens.size());

        ApiToken apiToken = apiTokens.get(0);
        assertEquals(Utility.CICD, apiToken.getUtility());

        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        HttpSession httpSession = mock(HttpSession.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(httpServletRequest.getHeader("X-API-KEY")).thenReturn(apiToken.getKey());
        when(httpServletRequest.getRequestURI()).thenReturn("/api/startTest");
        when(httpServletRequest.getSession(true)).thenReturn(httpSession);
        when(httpServletRequest.getSession()).thenReturn(httpSession);
        when(httpSession.getAttribute("accountId")).thenReturn(ACCOUNT_ID);

        UserDetailsFilter userDetailsFilter = new UserDetailsFilter();
        userDetailsFilter.doFilter(httpServletRequest, httpServletResponse,
                filterChain);

        // verify if cicd token is recognized.
        verify(httpSession).setAttribute("utility", Utility.CICD.toString());
        verify(httpSession).setAttribute("accountId", String.valueOf(ACCOUNT_ID));

        // check completion of filter chain
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        
        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints, 0,
                TestingRun.State.COMPLETED, 0, "test", "");
        TestingRunDao.instance.insertOne(testingRun);
        String testingRunHexId = testingRun.getHexId();

        assertEquals(1, TestingRunDao.instance.findAll(new BasicDBObject()).size());

        // trigger startTest API with CICD session.
        StartTestAction startTestAction = new StartTestAction();
        Map<String,Object> testSession = new HashMap<>();
        testSession.put("utility", Utility.CICD.toString());
        startTestAction.setSession(testSession);
        startTestAction.setTestingRunHexId(testingRunHexId);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("test", "test");
        startTestAction.setMetadata(metadata);
        startTestAction.startTest();

        assertEquals(1, TestingRunDao.instance.findAll(new BasicDBObject()).size());

        List<TestingRunResultSummary> summariesFromDb = TestingRunResultSummariesDao.instance.findAll(new BasicDBObject());
        assertEquals(1, summariesFromDb.size());

        TestingRunResultSummary summary = summariesFromDb.get(0);

        assertEquals(metadata, summary.getMetadata());

    }

}
