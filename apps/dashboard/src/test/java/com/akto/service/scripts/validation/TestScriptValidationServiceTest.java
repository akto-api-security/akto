package com.akto.service.scripts.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.mockito.MockedStatic;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.config.TestScriptDraftsDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.config.ScriptDiagnostics;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptDraft;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.service.scripts.TestScriptException;
import com.akto.service.scripts.TestScriptService;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class TestScriptValidationServiceTest extends MongoBasedTest {

    private static final int COLLECTION = 77;
    private static final String URL = "https://agent.test/api/v1/ask/queries";
    private static final String MESSAGE = "{\"request\":{\"url\":\"" + URL + "\",\"method\":\"POST\",\"body\":\"{}\"},"
        + "\"response\":{\"statusCode\":200,\"body\":\"final answer\"}}";

    private static class FakeRunner implements ReplayRunner {
        final AtomicInteger calls = new AtomicInteger();
        TestingRunConfig lastConfig;
        String lastSample;
        RuntimeException failWith;
        String message = MESSAGE;

        @Override
        public ReplayOutcome replay(ApiInfo.ApiInfoKey apiInfoKey, String sample, TestingRunConfig config) {
            calls.incrementAndGet();
            lastConfig = config;
            lastSample = sample;
            if (failWith != null) throw failWith;
            List<GenericTestResult> results = new ArrayList<>();
            results.add(new TestResult(message, null, message == null ? Collections.singletonList("no request") : new ArrayList<>(),
                100, false, TestResult.Confidence.HIGH, null));
            TestingRunResult result = new TestingRunResult(new ObjectId(), apiInfoKey, "SCRIPT_VALIDATION", ScriptValidationReplayTemplate.ID,
                results, false, null, 0, 0, 0, null, null, new ArrayList<>());
            return ReplayOutcome.from(result, new ScriptDiagnostics(), null);
        }
    }

    private FakeRunner runner;
    private TestScriptValidationService service;
    private User user;
    private MockedStatic<UsersCollectionsList> rbac;
    private List<Integer> allowedCollections;

    @After
    public void tearDown() {
        rbac.close();
        setHybridTesting(false);
    }

    private static void setHybridTesting(boolean enabled) {
        // upserts the account document, which is shared with every other test class
        AccountsDao.instance.updateOne(Filters.eq(Constants.ID, ACCOUNT_ID), Updates.set(Account.HYBRID_TESTING_ENABLED, enabled));
    }

    private static void heartbeat(String name, int secondsAgo) {
        ModuleInfo module = new ModuleInfo();
        module.setId(UUID.randomUUID().toString());
        module.setName(name);
        module.setModuleType(ModuleInfo.ModuleType.MINI_TESTING);
        module.setLastHeartbeatReceived(Context.now() - secondsAgo);
        ModuleInfoDao.instance.insertOne(module);
    }

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        // SampleDataDao applies the user's collection RBAC itself; null = full access
        allowedCollections = null;
        rbac = mockStatic(UsersCollectionsList.class);
        rbac.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenAnswer(i -> allowedCollections);
        TestScriptsDao.instance.getMCollection().drop();
        TestScriptDraftsDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        TestRolesDao.instance.getMCollection().drop();
        EndpointLogicalGroupDao.instance.getMCollection().drop();
        TestingRunPlaygroundDao.instance.getMCollection().drop();
        ModuleInfoDao.instance.getMCollection().drop();
        TestScriptsDao.instance.createIndicesIfAbsent();
        TestScriptDraftsDao.instance.createIndicesIfAbsent();
        SampleDataDao.instance.insertOne(new SampleData(new Key(COLLECTION, URL, URLMethods.Method.POST, -1, 0, 0),
            new ArrayList<>(Arrays.asList("older-sample", "newest-sample"))));
        runner = new FakeRunner();
        service = new TestScriptValidationService(runner);
        user = new User();
        user.setLogin("dev@x.com");
    }

    private ScriptValidationRequest request() {
        ScriptValidationRequest r = new ScriptValidationRequest();
        r.setValidationId(UUID.randomUUID().toString());
        r.setApiCollectionId(COLLECTION);
        r.setUrl(URL);
        r.setMethod("POST");
        r.setPostJavascript("body = 'x';");
        return r;
    }

    @Test
    public void draftIsSavedInItsOwnCollectionAndEveryInputReachesTheRun() throws Exception {
        ScriptValidationRequest req = request();
        ReplayOutcome outcome = service.validate(req, user);

        String draftId = TestScriptDraft.draftId(req.getValidationId(), TestScript.Type.POST_REQUEST);
        assertEquals("newest-sample", runner.lastSample);
        assertEquals(draftId, runner.lastConfig.getPostRequestScriptId());
        // no pre script means explicitly none, never the account's default script
        assertEquals(TestScript.NO_SCRIPT, runner.lastConfig.getPreRequestScriptId());
        assertNull(runner.lastConfig.getTestRoleId());
        assertEquals("body = 'x';", TestScriptDraftsDao.instance.findById(draftId).getJavascript());
        assertEquals(0, TestScriptsDao.instance.count(new Document()));
        assertEquals(Integer.valueOf(200), outcome.getStatusCode());
        assertTrue(outcome.isDiagnosticsAvailable());
    }

    @Test
    public void retriedValidationReusesItsDraft() throws Exception {
        ScriptValidationRequest req = request();
        service.validate(req, user);
        service.validate(req, user);
        assertEquals(1, TestScriptDraftsDao.instance.count(new Document()));
    }

    @Test
    public void libraryScriptsAreUsedByIdAndMustMatchTheType() throws Exception {
        TestScript lib = new TestScriptService().create("sign", "PRE_REQUEST", "pre();", "a@x.com");
        ScriptValidationRequest req = request();
        req.setPreScriptId(lib.getId());
        service.validate(req, user);
        assertEquals(lib.getId(), runner.lastConfig.getPreRequestScriptId());

        ScriptValidationRequest wrongType = request();
        wrongType.setPostJavascript(null);
        wrongType.setPostScriptId(lib.getId());
        assertThrows(TestScriptException.class, () -> service.validate(wrongType, user));

        // a draft is not a library script, and a testing run cannot select one
        String draftId = TestScriptDraft.draftId(req.getValidationId(), TestScript.Type.POST_REQUEST);
        ScriptValidationRequest usesDraft = request();
        usesDraft.setPostJavascript(null);
        usesDraft.setPostScriptId(draftId);
        assertThrows(TestScriptException.class, () -> service.validate(usesDraft, user));
        assertThrows(TestScriptException.class,
            () -> new TestScriptService().validateRunSelection(draftId, TestScript.Type.POST_REQUEST));
    }

    @Test
    public void rejectsBadInputBeforeWritingOrRunning() {
        ScriptValidationRequest both = request();
        both.setPostScriptId("x");
        assertThrows(TestScriptException.class, () -> service.validate(both, user));

        ScriptValidationRequest none = request();
        none.setPostJavascript(null);
        assertThrows(TestScriptException.class, () -> service.validate(none, user));

        ScriptValidationRequest badId = request();
        badId.setValidationId("not-a-uuid");
        assertThrows(TestScriptException.class, () -> service.validate(badId, user));

        assertThrows(TestScriptException.class, () -> service.validate(null, user));

        ScriptValidationRequest noSample = request();
        noSample.setUrl("https://agent.test/never-seen");
        TestScriptException e = assertThrows(TestScriptException.class, () -> service.validate(noSample, user));
        assertTrue(e.getMessage().contains("No captured request"));

        assertEquals(0, runner.calls.get());
    }

    @Test
    public void eachEntryPointRefusesTheOtherModulesRequest() {
        ScriptValidationRequest mini = request();
        mini.setMiniTestingName("mt-1");
        assertThrows(IllegalArgumentException.class, () -> service.validate(mini, user));
        assertThrows(IllegalArgumentException.class, () -> service.startInMiniTesting(request(), user));
        assertEquals(0, runner.calls.get());
    }

    @Test
    public void replayFailuresAreErrorsNotEmptyResults() {
        runner.failWith = new IllegalStateException("connect timed out");
        TestScriptException failed = assertThrows(TestScriptException.class, () -> service.validate(request(), user));
        assertTrue(failed.getMessage().contains("connect timed out"));

        runner.failWith = null;
        runner.message = null;
        TestScriptException notSent = assertThrows(TestScriptException.class, () -> service.validate(request(), user));
        assertTrue(notSent.getMessage().contains("did not send the request"));
        assertTrue(notSent.getMessage().contains("no request"));
    }

    @Test
    public void roleThatDoesNotCoverTheEndpointIsAWarning() throws Exception {
        ObjectId otherGroup = new ObjectId();
        EndpointLogicalGroupDao.instance.insertOne(new EndpointLogicalGroup(otherGroup, 0, 0, "a", "other",
            new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(COLLECTION, "https://agent.test/else", URLMethods.Method.GET)))));
        ObjectId coveringGroup = new ObjectId();
        EndpointLogicalGroupDao.instance.insertOne(new EndpointLogicalGroup(coveringGroup, 0, 0, "a", "covering",
            new CustomTestingEndpoints(Collections.singletonList(new ApiInfo.ApiInfoKey(COLLECTION, URL, URLMethods.Method.POST)))));
        ObjectId viewer = new ObjectId();
        TestRolesDao.instance.insertOne(new TestRoles(viewer, "viewer", otherGroup, new ArrayList<>(), "a", 0, 0, null, "a"));
        ObjectId admin = new ObjectId();
        TestRolesDao.instance.insertOne(new TestRoles(admin, "admin", coveringGroup, new ArrayList<>(), "a", 0, 0, null, "a"));

        ScriptValidationRequest warned = request();
        warned.setTestRoleId(viewer.toHexString());
        ReplayOutcome w = service.validate(warned, user);
        assertEquals(viewer.toHexString(), runner.lastConfig.getTestRoleId());
        assertTrue(w.getWarnings().stream().anyMatch(s -> s.contains("does not cover this endpoint")));

        ScriptValidationRequest applied = request();
        applied.setTestRoleId(admin.toHexString());
        assertFalse(service.validate(applied, user).getWarnings().stream().anyMatch(s -> s.contains("does not cover")));
    }

    // ---- mini-testing: scheduled as a test-editor playground, read back when the module reports ----

    private ScriptValidationRequest miniRequest() {
        ScriptValidationRequest r = request();
        r.setMiniTestingName("mt-1");
        r.setPreJavascript("headers['x'] = 'y';");
        return r;
    }

    private String scheduled(ScriptValidationRequest req) throws Exception {
        setHybridTesting(true);
        heartbeat("mt-1", 30);
        return service.startInMiniTesting(req, user);
    }

    private static TestingRunPlayground playground(String hexId) {
        return TestingRunPlaygroundDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(hexId)));
    }

    /** What cyborg does when mini-testing reports a finished playground (DbLayer.updateTestingRunPlayground). */
    private static void complete(String hexId, TestingRunResult result) {
        TestingRunPlaygroundDao.instance.updateOne(Filters.eq(Constants.ID, new ObjectId(hexId)),
            Updates.combine(Updates.set(TestingRunPlayground.STATE, State.COMPLETED),
                Updates.set(TestingRunPlayground.TESTING_RUN_RESULT, result)));
    }

    private static TestingRunResult resultWith(String message, List<String> errors) {
        List<GenericTestResult> results = new ArrayList<>();
        results.add(new TestResult(message, null, errors, 100, false, TestResult.Confidence.HIGH, null));
        return new TestingRunResult(new ObjectId(), new ApiInfo.ApiInfoKey(COLLECTION, URL, URLMethods.Method.POST), "SCRIPT_VALIDATION",
            ScriptValidationReplayTemplate.ID, results, false, null, 0, 0, 0, null, null, new ArrayList<>());
    }

    @Test
    public void miniTestingValidationIsScheduledAsAPlaygroundCarryingEveryInput() throws Exception {
        ScriptValidationRequest req = miniRequest();
        ObjectId role = new ObjectId();
        req.setTestRoleId(role.toHexString());

        String hex = scheduled(req);

        assertEquals(TestScriptValidationService.playgroundId(req.getValidationId()).toHexString(), hex);
        TestingRunPlayground p = playground(hex);
        assertEquals(State.SCHEDULED, p.getState());
        assertEquals("mt-1", p.getMiniTestingName());
        assertEquals(ScriptValidationReplayTemplate.YAML, p.getTestTemplate());
        assertEquals(Collections.singletonList("newest-sample"), p.getSamples());
        assertEquals(COLLECTION, p.getApiInfoKey().getApiCollectionId());
        assertEquals(URL, p.getApiInfoKey().getUrl());
        assertEquals(URLMethods.Method.POST, p.getApiInfoKey().getMethod());
        assertEquals(TestScriptDraft.draftId(req.getValidationId(), TestScript.Type.PRE_REQUEST), p.getTestingRunConfig().getPreRequestScriptId());
        assertEquals(TestScriptDraft.draftId(req.getValidationId(), TestScript.Type.POST_REQUEST), p.getTestingRunConfig().getPostRequestScriptId());
        assertEquals(role.toHexString(), p.getTestingRunConfig().getTestRoleId());
        assertEquals(2, TestScriptDraftsDao.instance.count(new Document()));
        assertEquals("nothing runs in the dashboard", 0, runner.calls.get());
    }

    @Test
    public void aRetriedMiniTestingClickSchedulesNothingNew() throws Exception {
        ScriptValidationRequest req = miniRequest();
        String first = scheduled(req);
        String second = service.startInMiniTesting(req, user);

        assertEquals(first, second);
        assertEquals(1, TestingRunPlaygroundDao.instance.count(new Document()));
        assertEquals(2, TestScriptDraftsDao.instance.count(new Document()));
    }

    @Test
    public void miniTestingNeedsHybridTestingAndALiveModuleAndWritesNothingOtherwise() {
        heartbeat("mt-1", 30);
        TestScriptException off = assertThrows(TestScriptException.class, () -> service.startInMiniTesting(miniRequest(), user));
        assertTrue(off.getMessage().contains("not enabled"));

        setHybridTesting(true);
        ScriptValidationRequest unknown = miniRequest();
        unknown.setMiniTestingName("nobody");
        TestScriptException none = assertThrows(TestScriptException.class, () -> service.startInMiniTesting(unknown, user));
        assertTrue(none.getMessage().contains("not active"));

        ModuleInfoDao.instance.getMCollection().drop();
        heartbeat("mt-1", 21 * 60);
        TestScriptException stale = assertThrows(TestScriptException.class, () -> service.startInMiniTesting(miniRequest(), user));
        assertTrue(stale.getMessage().contains("not active"));

        assertEquals(0, TestingRunPlaygroundDao.instance.count(new Document()));
        assertEquals("a rejected request leaves no draft behind", 0, TestScriptDraftsDao.instance.count(new Document()));
    }

    @Test
    public void aScheduledMiniTestingValidationIsPendingNotFailed() throws Exception {
        String hex = scheduled(miniRequest());
        assertNull(service.fetchMiniTestingOutcome(hex, user));
    }

    @Test
    public void aFinishedMiniTestingValidationIsReadFromTheStoredResultWithoutDiagnostics() throws Exception {
        ScriptValidationRequest req = miniRequest();
        req.setTestRoleId(new ObjectId().toHexString());
        String hex = scheduled(req);
        complete(hex, resultWith(MESSAGE, new ArrayList<>()));

        ReplayOutcome outcome = service.fetchMiniTestingOutcome(hex, user);

        assertEquals(Integer.valueOf(200), outcome.getStatusCode());
        assertEquals(MESSAGE, outcome.getReplayMessage());
        assertFalse("a module that reports nothing is unavailable, never clean", outcome.isDiagnosticsAvailable());
        assertNull(outcome.getDiagnostics());
        assertEquals(TestScriptValidationService.MINI_TESTING_NO_DIAGNOSTICS, outcome.getDiagnosticsUnavailableReason());
        assertTrue(outcome.getWarnings().stream().anyMatch(s -> s.contains("does not cover this endpoint")));
    }

    @Test
    public void aFinishedRunThatSentNothingIsAnErrorWithTheEnginesReason() throws Exception {
        String hex = scheduled(miniRequest());
        complete(hex, resultWith("", Collections.singletonList("Error in handling test editor playground: boom")));

        TestScriptException e = assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(hex, user));
        assertTrue(e.getMessage().contains("did not send the request"));
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    public void aCompletedPlaygroundWithoutAResultIsAnErrorNotAnEmptySuccess() throws Exception {
        String hex = scheduled(miniRequest());
        TestingRunPlaygroundDao.instance.updateOne(Filters.eq(Constants.ID, new ObjectId(hex)),
            Updates.set(TestingRunPlayground.STATE, State.COMPLETED));

        TestScriptException e = assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(hex, user));
        assertTrue(e.getMessage().contains("without a result"));
    }

    @Test
    public void onlyValidationPlaygroundsOnReachableEndpointsAreReadable() throws Exception {
        String hex = scheduled(miniRequest());
        complete(hex, resultWith(MESSAGE, new ArrayList<>()));

        // the test editor's playgrounds live in the same collection and are never readable here
        ObjectId editorId = new ObjectId();
        TestingRunPlayground editor = new TestingRunPlayground();
        editor.setId(editorId);
        editor.setTestTemplate("id: SOMETHING_ELSE");
        editor.setState(State.COMPLETED);
        editor.setApiInfoKey(new ApiInfo.ApiInfoKey(COLLECTION, URL, URLMethods.Method.POST));
        editor.setCreatedAt(Context.now());
        TestingRunPlaygroundDao.instance.insertOne(editor);
        assertEquals("Validation not found",
            assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(editorId.toHexString(), user)).getMessage());

        allowedCollections = Collections.singletonList(COLLECTION + 1);
        assertEquals("Validation not found",
            assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(hex, user)).getMessage());
        allowedCollections = null;

        assertEquals("Invalid validation id",
            assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome("not-hex", user)).getMessage());
        assertEquals("Invalid validation id",
            assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(null, user)).getMessage());
        assertEquals("Validation not found",
            assertThrows(TestScriptException.class, () -> service.fetchMiniTestingOutcome(new ObjectId().toHexString(), user)).getMessage());
    }
}
