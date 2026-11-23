package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.redteam_learnings.RedteamLearningsService;
import com.akto.service.redteam_learnings.RedteamLearningsService.AgentLearnings;
import com.akto.service.redteam_learnings.RedteamLearningsService.DraftUpdate;
import com.akto.service.redteam_learnings.RedteamLearningsService.RunLearnings;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Red-team learnings on the dashboard: what was learned about an agent (collection page), what one run learned (run
 * page), and a template draft's status. Reads need the feature's READ role (struts), and on top of the DAOs' RBAC
 * filter every request is checked against the collections the user may see; when that list cannot be read the
 * request fails instead of falling back to everything.
 */
public class RedteamLearningsAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(RedteamLearningsAction.class, LogDb.DASHBOARD);

    static final String FORBIDDEN = "FORBIDDEN";
    static final String NO_ACCESS_MESSAGE = "You can only view data of the collections assigned to you.";
    static final int MAX_URL_CHARS = 2048;
    static final int MAX_ID_CHARS = 128;
    static final int MAX_TEMPLATE_ID_CHARS = 256;
    private static final Pattern SUMMARY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Set<String> METHODS = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));

    private final RedteamLearningsService service = new RedteamLearningsService();

    @Setter private Integer apiCollectionId;
    @Setter private String url;
    @Setter private String method;
    @Setter private String testingRunResultSummaryId;
    @Setter private String draftId;
    @Setter private String status;
    @Setter private String savedTemplateId;

    @Getter private AgentLearnings agentLearnings;
    @Getter private RunLearnings runLearnings;
    /** The account's setting; null when it could not be read. */
    @Getter private Boolean learningsEnabled;

    /** Collection page: the agent's latest profile, its version history and its open drafts. */
    public String fetchAgentRedteamLearnings() {
        try {
            String error = validateIdentity();
            if (error != null) return fail(error);
            if (!RedteamLearningsService.canSee(allowedCollectionIds(), apiCollectionId)) return forbidden();
            agentLearnings = service.forAgent(apiCollectionId, url, method);
            learningsEnabled = service.accountLearningsEnabled();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to fetch the agent's red-team learnings", e);
        }
    }

    /** Run page: each tested agent's newest version from the run, every version it wrote, and its drafts. */
    public String fetchRunRedteamLearnings() {
        try {
            testingRunResultSummaryId = testingRunResultSummaryId == null ? null : testingRunResultSummaryId.trim();
            if (testingRunResultSummaryId == null || !SUMMARY_ID.matcher(testingRunResultSummaryId).matches()) {
                return fail("testingRunResultSummaryId may only contain letters, digits, '-' and '_' (at most 64 characters)");
            }
            runLearnings = service.forRun(testingRunResultSummaryId, allowedCollectionIds());
            learningsEnabled = service.accountLearningsEnabled();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to fetch the run's red-team learnings", e);
        }
    }

    /** Marks a draft saved (with the template it was saved as), dismissed, or back to a draft. */
    public String updateRedteamDraftStatus() {
        try {
            draftId = trimmed(draftId);
            status = status == null ? null : status.trim().toUpperCase();
            savedTemplateId = trimmed(savedTemplateId);
            if (draftId == null || draftId.length() > MAX_ID_CHARS) return fail("draftId is required (at most " + MAX_ID_CHARS + " characters)");
            if (!RedteamLearningsService.DRAFT_STATUSES.contains(status)) return fail("status must be one of " + RedteamLearningsService.DRAFT_STATUSES);
            if (AgentRedteamTemplateDraft.STATUS_SAVED.equals(status) &&(savedTemplateId == null || savedTemplateId.length() > MAX_TEMPLATE_ID_CHARS)) {
                return fail("savedTemplateId is required to mark a draft saved (at most " + MAX_TEMPLATE_ID_CHARS + " characters)");
            }
            DraftUpdate result = service.updateDraftStatus(draftId, status, savedTemplateId, allowedCollectionIds(), System.currentTimeMillis());
            switch (result) {
                case UPDATED: return SUCCESS.toUpperCase();
                case FORBIDDEN: return forbidden();
                case TEMPLATE_NOT_FOUND: return fail("No custom test template with id " + savedTemplateId);
                case CONFLICT: return fail("The draft was already saved as a test template and cannot be reopened");
                default: return fail("No such draft");
            }
        } catch (Exception e) {
            return error("Failed to update the draft", e);
        }
    }

    private List<Integer> allowedCollectionIds() {
        return UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
    }

    private String validateIdentity() {
        if (apiCollectionId == null) return "apiCollectionId is required";
        url = trimmed(url);
        if (url == null || url.length() > MAX_URL_CHARS) return "url is required (at most " + MAX_URL_CHARS + " characters)";
        method = method == null ? null : method.trim().toUpperCase();
        if (!METHODS.contains(method)) return "method must be one of " + METHODS;
        return null;
    }

    private static String trimmed(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String forbidden() {
        addActionError(NO_ACCESS_MESSAGE);
        return FORBIDDEN;
    }

    private String fail(String message) {
        addActionError(message);
        return ERROR.toUpperCase();
    }

    private String error(String message, Exception e) {
        logger.errorAndAddToDb(e, message + ": " + e.getMessage());
        addActionError(message);
        return ERROR.toUpperCase();
    }
}
