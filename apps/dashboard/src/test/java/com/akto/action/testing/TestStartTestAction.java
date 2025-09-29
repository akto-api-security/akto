package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.action.ApiTokenAction;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.*;
import com.akto.dto.testing.config.EditableTestingRunConfig;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.RBAC.Role;
import com.akto.dto.billing.Organization;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.URLMethods;
import com.akto.filter.UserDetailsFilter;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
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
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestStartTestAction extends MongoBasedTest {

    @Test
    public void testStopAllTests() {
        TestingRunDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun1 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.SCHEDULED, 0, "test", "", false);

        CustomTestingEndpoints customTestingEndpoints = new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(1000, "url", URLMethods.Method.GET)));
        TestingRun testingRun2 = new TestingRun(Context.now(), "", customTestingEndpoints ,0, TestingRun.State.SCHEDULED, 1, "test", "", false);

        WorkflowTestingEndpoints workflowTestingEndpoints = new WorkflowTestingEndpoints();
        WorkflowTest workflowTest = new WorkflowTest();
        workflowTest.setApiCollectionId(1000);
        workflowTestingEndpoints.setWorkflowTest(workflowTest);
        TestingRun testingRun3 = new TestingRun(Context.now(), "",  workflowTestingEndpoints,1, TestingRun.State.SCHEDULED, 0, "test", "", false);

        TestingRun testingRun4 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.RUNNING, 0, "test", "", false);
        // already completed test
        TestingRun testingRun5 = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.COMPLETED, 0, "test", "", false);

        TestingRunDao.instance.insertMany(Arrays.asList(testingRun1, testingRun2, testingRun3, testingRun4, testingRun5 ));

        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING)
        );

        Context.userId.set(0);
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);

        ApiCollection apiCollection = new ApiCollection();
        apiCollection.setId(1000);
        apiCollection.setName("test - test - test");
        ApiCollectionsDao.instance.insertOne(apiCollection);

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
        ApiCollectionsDao.instance.getMCollection().drop();

        List<TestingRunResultSummary> testingRunResultSummaryList = new ArrayList<>();
        ObjectId testingRunId = new ObjectId();
        TestingRun testingRun = new TestingRun(0, "avneesh@akto.io", new CollectionWiseTestingEndpoints(0), 0,  State.COMPLETED, 0, "test", "", false);
        testingRun.setId(testingRunId);
        for (int startTimestamp=0; startTimestamp < 30; startTimestamp++) {
            TestingRunResultSummary testingRunResultSummary = new TestingRunResultSummary(
                startTimestamp, startTimestamp+10, new HashMap<>(), 10, testingRunId, testingRunId.toHexString(), 10, 0, 10
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
        ApiCollectionsDao.instance.getMCollection().drop();

        CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = new CollectionWiseTestingEndpoints(1000);
        TestingRun testingRun = new TestingRun(Context.now(), "", collectionWiseTestingEndpoints,0, TestingRun.State.COMPLETED, 0, "test", "", false);
        TestingRunDao.instance.insertOne(testingRun);
        String testingRunHexId = testingRun.getHexId();

        StartTestAction startTestAction = new StartTestAction();
        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        startTestAction.setSession(session);
        Context.userId.set(user.getId());
        startTestAction.setTestingRunHexId(testingRunHexId);
        startTestAction.startTest();

        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);

        ApiCollection apiCollection = new ApiCollection();
        apiCollection.setId(1000);
        apiCollection.setName("test - test - test");
        ApiCollectionsDao.instance.insertOne(apiCollection);

        assertEquals(1,TestingRunDao.instance.findAll(new BasicDBObject()).size());

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
        ApiCollectionsDao.instance.getMCollection().drop();

        // create an CICD API token, mock a server request with it and check if it recognizes it.
        
        UserAccountEntry userAccountEntry = new UserAccountEntry();
        userAccountEntry.setAccountId(ACCOUNT_ID);
        userAccountEntry.setDefault(true);
        Map<String, UserAccountEntry> accountAccessMap = new HashMap<>();
        accountAccessMap.put(ACCOUNT_ID+"", userAccountEntry);
        
        User user = new User();
        String login="test@akto.io";
        user.setLogin(login);
        user.setAccounts(accountAccessMap);

        UsersDao.instance.insertOne(user);

        user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, login));

        Context.userId.set(user.getId());
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);

        RBAC rbac = new RBAC(user.getId(), Role.ADMIN.name(), ACCOUNT_ID);
        RBACDao.instance.insertOne(rbac);

        AccountSettings acc = new AccountSettings();
        acc.setDashboardVersion("test - test - test");
        acc.setId(ACCOUNT_ID);
        AccountSettingsDao.instance.insertOne(acc);

        Organization org = new Organization(UUID.randomUUID().toString(), user.getLogin(), user.getLogin(),
                new HashSet<>(Arrays.asList(ACCOUNT_ID)), true);
        OrganizationsDao.instance.insertOne(org);
        
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
                TestingRun.State.COMPLETED, 0, "test", "", false);
        TestingRunDao.instance.insertOne(testingRun);
        String testingRunHexId = testingRun.getHexId();

        ApiCollection apiCollection = new ApiCollection();
        apiCollection.setId(1000);
        apiCollection.setName("test - test - test");
        ApiCollectionsDao.instance.insertOne(apiCollection);
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

    @Test
    public void testModifyTestingRunConfig() {
        TestingRunConfigDao.instance.getMCollection().drop();
        TestingRunDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        int testingRunConfigId = UUID.randomUUID().hashCode() & 0xfffffff;
        ObjectId testingRunHexId = new ObjectId();

        List<String> list1 = Arrays.asList("pkucgztk", "oionxmec", "tmptskkz", "rorcqyqf");
        List<String> list2 = Arrays.asList("sqhrduyv", "awpqhaxz", "tdzydooe", "hxwvtoem");
        TestingRunConfig testingRunConfig = new TestingRunConfig();
        testingRunConfig.setId(testingRunConfigId);
        testingRunConfig.setTestRoleId("initialRole");
        testingRunConfig.setOverriddenTestAppUrl("https://initial.url");
        testingRunConfig.setTestSubCategoryList(list1);
        testingRunConfig.setId(testingRunConfigId);

        List<TestConfigsAdvancedSettings> testConfigsAdvancedSettingsList = new ArrayList<>();
        TestConfigsAdvancedSettings testConfigsAdvancedSettings1 = new TestConfigsAdvancedSettings();
        testConfigsAdvancedSettings1.setOperatorType("ADD_HEADER");
        testConfigsAdvancedSettings1.setOperationsGroupList(new ArrayList<ConditionsType>());
        testConfigsAdvancedSettings1.getOperationsGroupList().add(new ConditionsType("param1", "value1", new HashSet<>(Arrays.asList("url1", "url2"))));
        testConfigsAdvancedSettings1.getOperationsGroupList().add(new ConditionsType("param2", "value2", new HashSet<>(Arrays.asList("url1", "url2"))));
        testConfigsAdvancedSettingsList.add(testConfigsAdvancedSettings1);

        testingRunConfig.setConfigsAdvancedSettings(testConfigsAdvancedSettingsList);

        TestingRunConfigDao.instance.insertOne(testingRunConfig);

        TestingRun testingRun = new TestingRun();
        testingRun.setTestIdConfig(testingRunConfigId);
        testingRun.setTestRunTime(1800);
        testingRun.setMaxConcurrentRequests(5);
        testingRun.setId(testingRunHexId);

        TestingRunDao.instance.insertOne(testingRun);

        EditableTestingRunConfig editableTestingRunConfig = new EditableTestingRunConfig();
        editableTestingRunConfig.setTestRunTime(3600);
        editableTestingRunConfig.setTestRoleId("newRole");
        editableTestingRunConfig.setOverriddenTestAppUrl("https://test.url");
        editableTestingRunConfig.setMaxConcurrentRequests(10);
        editableTestingRunConfig.setTestSubCategoryList(list2);
        editableTestingRunConfig.setTestingRunHexId(testingRunHexId.toHexString());

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.setTestingRunConfigId(testingRunConfigId);
        startTestAction.setEditableTestingRunConfig(editableTestingRunConfig);

        String result = startTestAction.modifyTestingRunConfig();
        
        assertEquals(Action.SUCCESS.toUpperCase(), result);

        TestingRunConfig updatedConfig = TestingRunConfigDao.instance.findOne(Filters.eq(Constants.ID, testingRunConfigId));
        assertEquals("newRole", updatedConfig.getTestRoleId());
        assertEquals("https://test.url", updatedConfig.getOverriddenTestAppUrl());
        assertEquals(list2, updatedConfig.getTestSubCategoryList());

        assertEquals(testConfigsAdvancedSettingsList, updatedConfig.getConfigsAdvancedSettings());

        // Ensure TestConfigsAdvancedSettings equals() override method works correctly
        testConfigsAdvancedSettingsList.get(0).getOperationsGroupList().add(new ConditionsType("param3", "value3", new HashSet<>(Arrays.asList("url1", "url2"))));
        assertNotEquals(testConfigsAdvancedSettingsList, updatedConfig.getConfigsAdvancedSettings());


        TestingRun updatedTestingRun = TestingRunDao.instance.findOne(Filters.eq(Constants.ID, testingRunHexId));
        assertEquals(3600, updatedTestingRun.getTestRunTime());
        assertEquals(10, updatedTestingRun.getMaxConcurrentRequests());

        
    }

}
