package com.akto.service.scripts.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.config.TestScriptDraftsDao;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptDraft;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.scripts.TestScriptException;
import com.akto.service.scripts.TestScriptService;
import com.akto.utils.MiniTestingModules;
import com.akto.util.Constants;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;

/**
 * Validate an Ask Akto script: replay one endpoint's newest captured request with a pre and/or post
 * script. In the dashboard module it runs inline, like the test editor does; in a mini-testing module
 * it is scheduled as a test-editor playground and read back later. Access is the same as the script
 * library's (route feature label + usage, plus requireWriteAccess). See CLAUDE.md in this package.
 */
public class TestScriptValidationService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestScriptValidationService.class, LogDb.DASHBOARD);

    static final int DRAFT_TTL_SECONDS = 24 * 60 * 60;

    /** Shown with every mini-testing result: the module's build, not the dashboard, decides what comes back. */
    static final String MINI_TESTING_NO_DIAGNOSTICS = "This mini-testing module does not report script errors or logs, so a script that "
        + "failed looks the same as one that did not run: compare the replay with the original request and response. The script ran "
        + "without the dashboard's sandbox, and the module may replay the first sample it saw for this endpoint.";

    private final ReplayRunner runner;
    private final TestScriptService scripts = new TestScriptService();
    private final TestScriptValidationInputValidator inputValidator = new TestScriptValidationInputValidator();

    public TestScriptValidationService() {
        this(new DashboardReplayRunner());
    }

    TestScriptValidationService(ReplayRunner runner) {
        this.runner = runner;
    }

    /** What a validation replays: the newest sample of the endpoint, with the scripts and role to apply. */
    private static final class Replay {
        final ApiInfo.ApiInfoKey apiInfoKey;
        final String sample;
        final TestingRunConfig config;

        Replay(ApiInfo.ApiInfoKey apiInfoKey, String sample, TestingRunConfig config) {
            this.apiInfoKey = apiInfoKey;
            this.sample = sample;
            this.config = config;
        }
    }

    public static boolean isMiniTesting(ScriptValidationRequest request) {
        return request != null && StringUtils.isNotBlank(request.getMiniTestingName());
    }

    /** Runs the replay in the dashboard module and returns its outcome. */
    public ReplayOutcome validate(ScriptValidationRequest request, User user) throws TestScriptException {
        checkRequest(request, user);
        if (isMiniTesting(request)) {
            throw new IllegalArgumentException("a mini-testing validation is started with startInMiniTesting");
        }
        Replay replay = load(request, user);

        ReplayOutcome outcome;
        try {
            outcome = runner.replay(replay.apiInfoKey, replay.sample, replay.config);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("script validation replay failed: " + e.getClass().getSimpleName());
            throw new TestScriptException("The replay failed: " + StringUtils.abbreviate(String.valueOf(e.getMessage()), 500));
        }
        requireSent(outcome);
        addRoleWarning(request.getTestRoleId(), replay.apiInfoKey, outcome.getWarnings());
        return outcome;
    }

    /**
     * Schedules the replay in a mini-testing module as a test-editor playground and returns its hex id; the
     * result is read back with fetchMiniTestingOutcome. The id derives from the client's validationId, so a
     * retried request schedules nothing new.
     */
    public String startInMiniTesting(ScriptValidationRequest request, User user) throws TestScriptException {
        checkRequest(request, user);
        if (!isMiniTesting(request)) {
            throw new IllegalArgumentException("a dashboard validation is run with validate");
        }
        String module = request.getMiniTestingName();
        requireActiveModule(module);
        Replay replay = load(request, user);

        TestingRunPlayground playground = new TestingRunPlayground();
        ObjectId id = playgroundId(request.getValidationId());
        playground.setId(id);
        playground.setTestTemplate(ScriptValidationReplayTemplate.YAML);
        playground.setState(State.SCHEDULED);
        playground.setSamples(Collections.singletonList(replay.sample));
        playground.setApiInfoKey(replay.apiInfoKey);
        playground.setCreatedAt(Context.now());
        playground.setMiniTestingName(module);
        playground.setTestingRunConfig(replay.config);
        try {
            TestingRunPlaygroundDao.instance.insertOne(playground);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw e;
            }
        }
        loggerMaker.infoAndAddToDb("script validation scheduled on mini-testing module " + module + ", playground " + id.toHexString());
        return id.toHexString();
    }

    /**
     * The outcome of a scheduled validation, or null while the module has not reported yet. A module reports
     * only when the run ends, so null means waiting or running, never failed.
     */
    public ReplayOutcome fetchMiniTestingOutcome(String playgroundHexId, User user) throws TestScriptException {
        scripts.requireWriteAccess(user, Context.accountId.get());
        TestingRunPlayground playground = findValidationPlayground(playgroundHexId, user);
        if (playground.getState() != State.COMPLETED) {
            return null;
        }
        TestingRunResult result = playground.getTestingRunResult();
        if (result == null) {
            throw new TestScriptException("The mini-testing module finished without a result");
        }
        ReplayOutcome outcome;
        try {
            outcome = ReplayOutcome.from(result, null, MINI_TESTING_NO_DIAGNOSTICS);
        } catch (IllegalStateException e) {
            throw new TestScriptException("The mini-testing module returned no usable result: " + e.getMessage());
        }
        requireSent(outcome);
        TestingRunConfig config = playground.getTestingRunConfig();
        addRoleWarning(config == null ? null : config.getTestRoleId(), playground.getApiInfoKey(), outcome.getWarnings());
        loggerMaker.infoAndAddToDb("script validation finished on mini-testing playground " + playgroundHexId
            + ", status " + outcome.getStatusCode() + ", warnings " + outcome.getWarnings().size());
        return outcome;
    }

    private void checkRequest(ScriptValidationRequest request, User user) throws TestScriptException {
        inputValidator.validate(request);
        scripts.requireWriteAccess(user, Context.accountId.get());
    }

    private Replay load(ScriptValidationRequest request, User user) throws TestScriptException {
        String login = user == null ? null : user.getLogin();
        URLMethods.Method method = TestScriptValidationInputValidator.parseMethod(request.getMethod());
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(request.getApiCollectionId(), request.getUrl(), method);
        String sample = latestSample(apiInfoKey);

        TestingRunConfig config = new TestingRunConfig();
        config.setPreRequestScriptId(scriptId(request.getValidationId(), request.getPreJavascript(), request.getPreScriptId(), TestScript.Type.PRE_REQUEST, login));
        config.setPostRequestScriptId(scriptId(request.getValidationId(), request.getPostJavascript(), request.getPostScriptId(), TestScript.Type.POST_REQUEST, login));
        if (StringUtils.isNotBlank(request.getTestRoleId())) {
            config.setTestRoleId(request.getTestRoleId());
        }
        return new Replay(apiInfoKey, sample, config);
    }

    private static void requireSent(ReplayOutcome outcome) throws TestScriptException {
        if (!outcome.wasSent()) {
            throw new TestScriptException("The test engine did not send the request: "
                + (outcome.getWarnings().isEmpty() ? "no reason given" : String.join("; ", outcome.getWarnings())));
        }
    }

    /** Same rule the test editor's module picker uses: hybrid testing on, and the module has sent a heartbeat lately. */
    private static void requireActiveModule(String module) throws TestScriptException {
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, Context.accountId.get()));
        if (account == null || !account.getHybridTestingEnabled()) {
            throw new TestScriptException("Mini-testing is not enabled for this account");
        }
        if (!MiniTestingModules.activeNames().contains(module)) {
            throw new TestScriptException("Mini-testing module '" + module + "' is not active: it has sent no heartbeat in the last 20 minutes");
        }
    }

    /** Only playgrounds this feature scheduled, on endpoints the user's collection access covers, are readable here. */
    private static TestingRunPlayground findValidationPlayground(String hexId, User user) throws TestScriptException {
        ObjectId id;
        try {
            id = new ObjectId(String.valueOf(hexId));
        } catch (IllegalArgumentException e) {
            throw new TestScriptException("Invalid validation id");
        }
        TestingRunPlayground playground = TestingRunPlaygroundDao.instance.findOne(Filters.eq(Constants.ID, id));
        if (playground == null || !ScriptValidationReplayTemplate.YAML.equals(playground.getTestTemplate())
                || playground.getApiInfoKey() == null) {
            throw new TestScriptException("Validation not found");
        }
        List<Integer> allowed = UsersCollectionsList.getCollectionsIdForUser(user == null ? 0 : user.getId(), Context.accountId.get());
        if (allowed != null && !allowed.contains(playground.getApiInfoKey().getApiCollectionId())) {
            throw new TestScriptException("Validation not found");
        }
        return playground;
    }

    /** Deterministic, so a retried click finds its playground instead of scheduling a second one. */
    static ObjectId playgroundId(String validationId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("script-validation:" + validationId).getBytes(StandardCharsets.UTF_8));
            return new ObjectId(Arrays.copyOf(hash, 12));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The id to put on the run config: a draft saved from the sent body, a library script, or "" (explicitly
     * no script; null would run the account's default script and pollute the validation).
     */
    private String scriptId(String validationId, String javascript, String libraryId, TestScript.Type type, String login)
            throws TestScriptException {
        if (StringUtils.isNotBlank(javascript)) {
            int now = Context.now();
            String id = TestScriptDraft.draftId(validationId, type);
            TestScriptDraftsDao.instance.insertIfAbsent(new TestScriptDraft(id, javascript, type, login, now,
                new Date((now + (long) DRAFT_TTL_SECONDS) * 1000L)));
            return id;
        }
        if (StringUtils.isNotBlank(libraryId)) {
            return scripts.requireLibraryScript(libraryId, type).getId();
        }
        return TestScript.NO_SCRIPT;
    }

    /** The newest captured sample: the replay sends a request Akto saw, never one the client supplies. */
    private static String latestSample(ApiInfo.ApiInfoKey apiInfoKey) throws TestScriptException {
        SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(
            apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod());
        List<String> samples = sampleData == null ? null : sampleData.getSamples();
        if (samples == null || samples.isEmpty()) {
            throw new TestScriptException("No captured request for " + apiInfoKey.getMethod() + " " + apiInfoKey.getUrl()
                + ". A validation replays a captured request, so pick an endpoint that has samples.");
        }
        return samples.get(samples.size() - 1);
    }

    /** The engine applies a role only to endpoints in its logical group; say so instead of failing silently. */
    private static void addRoleWarning(String testRoleId, ApiInfo.ApiInfoKey apiInfoKey, List<String> warnings) {
        if (StringUtils.isBlank(testRoleId)) {
            return;
        }
        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testRoleId)));
        EndpointLogicalGroup group = role == null ? null : role.fetchEndpointLogicalGroup();
        if (group == null || group.getTestingEndpoints() == null || !group.getTestingEndpoints().containsApi(apiInfoKey)) {
            warnings.add("The test role does not cover this endpoint, so its auth was not applied: the replay used the captured request's own auth.");
        }
    }
}
