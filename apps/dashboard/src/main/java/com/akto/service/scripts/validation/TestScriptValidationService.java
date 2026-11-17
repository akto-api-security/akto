package com.akto.service.scripts.validation;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.config.TestScriptDraftsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptDraft;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.scripts.TestScriptException;
import com.akto.service.scripts.TestScriptService;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;

/**
 * Validate an Ask Akto script: replay one endpoint's newest captured request with a pre and/or post
 * script. Runs inline in the dashboard module, like the test editor does. Access is the same as the
 * script library's (route feature label + usage, plus requireWriteAccess). See CLAUDE.md in this package.
 */
public class TestScriptValidationService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestScriptValidationService.class, LogDb.DASHBOARD);

    static final int DRAFT_TTL_SECONDS = 24 * 60 * 60;

    private final ReplayRunner runner;
    private final TestScriptService scripts = new TestScriptService();
    private final TestScriptValidationInputValidator inputValidator = new TestScriptValidationInputValidator();

    public TestScriptValidationService() {
        this(new DashboardReplayRunner());
    }

    TestScriptValidationService(ReplayRunner runner) {
        this.runner = runner;
    }

    public ReplayOutcome validate(ScriptValidationRequest request, User user) throws TestScriptException {
        inputValidator.validate(request);
        scripts.requireWriteAccess(user, Context.accountId.get());
        if (StringUtils.isNotBlank(request.getMiniTestingName())) {
            // Next: schedule the replay template as a test-editor playground and poll it like the test editor.
            throw new TestScriptException("Validating in a mini-testing module is not available yet; use the dashboard module");
        }
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

        ReplayOutcome outcome;
        try {
            outcome = runner.replay(apiInfoKey, sample, config);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("script validation replay failed: " + e.getClass().getSimpleName());
            throw new TestScriptException("The replay failed: " + StringUtils.abbreviate(String.valueOf(e.getMessage()), 500));
        }
        if (!outcome.wasSent()) {
            throw new TestScriptException("The test engine did not send the request: "
                + (outcome.getWarnings().isEmpty() ? "no reason given" : String.join("; ", outcome.getWarnings())));
        }
        addRoleWarning(request.getTestRoleId(), apiInfoKey, outcome.getWarnings());
        return outcome;
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
