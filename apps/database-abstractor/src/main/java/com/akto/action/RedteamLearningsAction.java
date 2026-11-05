package com.akto.action;

import com.akto.dao.redteam_learnings.AgentRedteamObservationDao;
import com.akto.dao.redteam_learnings.AgentRedteamProfileDao;
import com.akto.dao.redteam_learnings.AgentRedteamTemplateDraftDao;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dto.redteam_learnings.AgentRedteamObservation;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.Insight;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.dto.redteam_learnings.TemplateGridKey;
import com.akto.dto.redteam_learnings.VerdictCounts;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * What red teaming (test-editor-services, through the utility server) reads and writes about a target agent, which
 * is identified by {apiCollectionId, url, method}. Every input is validated and capped here; ids are built here, never
 * taken from the caller; and a profile's counts are computed from the stored observations, never sent by the caller.
 */
public class RedteamLearningsAction extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(RedteamLearningsAction.class, LogDb.DB_ABS);

    static final int MAX_OBSERVATIONS_PER_REQUEST = 200;
    static final int MAX_ID_CHARS = 128;
    static final int MAX_URL_CHARS = 2048;
    static final int MAX_KEY_CHARS = 64;
    static final int MAX_SHORT_TEXT_CHARS = 200;
    static final int MAX_LONG_TEXT_CHARS = 2000;
    static final int MAX_YAML_CHARS = 20000;
    static final int MAX_TURNS = 50;
    static final int MAX_REFERENCED_IDS = 20;
    static final int MAX_STRATEGIES_PER_DRAFT = 13;
    static final int MAX_IMPORTANCE = 100;
    static final int MAX_BATCH_NO = 10000;
    static final int DEFAULT_SAMPLES_PER_VERDICT = 8;
    static final int MAX_SAMPLES_PER_VERDICT = 15;
    /** An event time later than this past now is a clock or caller error, not a real time. */
    static final long MAX_FUTURE_SKEW_MS = TimeUnit.DAYS.toMillis(1);

    static final String STATE_NONE = "NONE";
    static final String STATE_READY = "READY";

    private static final Pattern SUMMARY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Set<String> METHODS = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
    private static final Set<String> OUTCOMES = new HashSet<>(Arrays.asList(AgentRedteamObservation.OUTCOME_JUDGED,
            AgentRedteamObservation.OUTCOME_TARGET_ERROR, AgentRedteamObservation.OUTCOME_JUDGE_ERROR));
    private static final Set<String> VERDICTS = new HashSet<>(Arrays.asList(AgentRedteamObservation.VERDICT_NORMAL,
            AgentRedteamObservation.VERDICT_ATTEMPTED, AgentRedteamObservation.VERDICT_CRITICAL));
    private static final Set<String> INSIGHT_KINDS = new HashSet<>(Arrays.asList(Insight.KIND_REFUSAL_STYLE,
            Insight.KIND_BYPASS, Insight.KIND_BLOCKED, Insight.KIND_TOOLS, Insight.KIND_ADVICE));
    /** Most telling first: what got through, then what nearly did, then what was refused. */
    private static final List<String> SAMPLE_VERDICT_ORDER = Arrays.asList(AgentRedteamObservation.VERDICT_CRITICAL,
            AgentRedteamObservation.VERDICT_ATTEMPTED, AgentRedteamObservation.VERDICT_NORMAL);

    @Setter private Integer apiCollectionId;
    @Setter private String url;
    @Setter private String method;
    @Setter private String testingRunResultSummaryId;

    @Setter private List<AgentRedteamObservation> observations;
    @Setter private Integer samplesPerVerdict;
    @Setter private Integer batchNo;
    @Setter private Boolean finalVersion;
    @Setter private List<Insight> insights;
    @Setter private List<AgentRedteamTemplateDraft> drafts;

    @Getter private Map<String, Object> response;

    /** Stores finished conversations' observations; responds with how many were stored, which were rejected and why, and the run's counts. */
    public String storeRedteamObservations() {
        try {
            String error = firstNonNull(validateIdentity(), validateSummaryId());
            if (error == null && (observations == null || observations.isEmpty())) error = "observations are required";
            if (error == null && observations.size() > MAX_OBSERVATIONS_PER_REQUEST) {
                error = "at most " + MAX_OBSERVATIONS_PER_REQUEST + " observations per request";
            }
            if (error != null) return fail(error);

            long now = System.currentTimeMillis();
            List<AgentRedteamObservation> valid = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            for (int i = 0; i < observations.size(); i++) {
                AgentRedteamObservation observation = observations.get(i);
                String reason = observation == null ? "missing" : prepareObservation(observation, now);
                if (reason == null) valid.add(observation);
                else rejected.add(i + ": " + reason);
            }

            AgentRedteamObservationDao dao = AgentRedteamObservationDao.instance;
            dao.ensureIndicesOnce();
            dao.upsertAll(valid);

            response = new LinkedHashMap<>();
            response.put("stored", valid.size());
            response.put("rejected", rejected);
            response.put("judgedCount", dao.countJudged(testingRunResultSummaryId, apiCollectionId, url, method));
            response.put("unjudgedCount", dao.countUnjudged(testingRunResultSummaryId, apiCollectionId, url, method));
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to store red-team observations", e);
        }
    }

    /**
     * What a consolidation of one agent in one run starts from: the run's counts and grid (from the database), the most
     * telling recent conversations with their stored turns, the agent's latest profile from any run, and what this run
     * has consolidated already.
     */
    public String fetchRedteamConsolidationInput() {
        try {
            String error = firstNonNull(validateIdentity(), validateSummaryId());
            int perVerdict = samplesPerVerdict == null ? DEFAULT_SAMPLES_PER_VERDICT : samplesPerVerdict;
            if (error == null && (perVerdict < 1 || perVerdict > MAX_SAMPLES_PER_VERDICT)) {
                error = "samplesPerVerdict must be between 1 and " + MAX_SAMPLES_PER_VERDICT;
            }
            if (error != null) return fail(error);

            AgentRedteamObservationDao observationDao = AgentRedteamObservationDao.instance;
            AgentRedteamProfileDao profileDao = AgentRedteamProfileDao.instance;
            observationDao.ensureIndicesOnce();
            profileDao.ensureIndicesOnce();

            List<AgentRedteamObservation> sampled = new ArrayList<>();
            for (String verdict : SAMPLE_VERDICT_ORDER) {
                sampled.addAll(observationDao.findSamples(testingRunResultSummaryId, apiCollectionId, url, method, verdict, perVerdict));
            }
            List<String> conversationIds = new ArrayList<>();
            for (AgentRedteamObservation observation : sampled) conversationIds.add(observation.getId());
            Map<String, List<AgentConversationResult>> turns = AgentConversationResultDao.instance.findTurns(conversationIds);

            List<Map<String, Object>> samples = new ArrayList<>();
            for (AgentRedteamObservation observation : sampled) {
                samples.add(sample(observation, turns.getOrDefault(observation.getId(), Collections.emptyList())));
            }

            AgentRedteamProfile latestInRun = profileDao.findLatestInRun(testingRunResultSummaryId, apiCollectionId, url, method);
            response = new LinkedHashMap<>();
            response.put("totals", observationDao.countVerdicts(testingRunResultSummaryId, apiCollectionId, url, method));
            response.put("unjudgedCount", observationDao.countUnjudged(testingRunResultSummaryId, apiCollectionId, url, method));
            response.put("cells", observationDao.aggregateCells(testingRunResultSummaryId, apiCollectionId, url, method));
            response.put("samples", samples);
            response.put("latestProfile", profileDao.findLatest(apiCollectionId, url, method));
            response.put("lastBatchNoInRun", latestInRun == null ? null : latestInRun.getBatchNo());
            response.put("finalVersionSavedInRun", latestInRun != null && latestInRun.isFinalVersion());
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to fetch red-team consolidation input", e);
        }
    }

    /**
     * Writes one profile version (its counts and grid computed here from the stored observations) and the run's
     * template drafts. A version with nothing judged or without insights is refused, so a failed or empty
     * consolidation never becomes the latest version. Repeating the call rewrites the same version, and a draft that
     * already exists keeps its status.
     */
    public String saveRedteamProfile() {
        try {
            String error = firstNonNull(validateIdentity(), validateSummaryId());
            if (error == null && (batchNo == null || batchNo < 0 || batchNo > MAX_BATCH_NO)) {
                error = "batchNo must be between 0 and " + MAX_BATCH_NO;
            }
            if (error == null) error = prepareInsights();
            if (error == null) error = prepareDrafts();
            if (error != null) return fail(error);

            AgentRedteamObservationDao observationDao = AgentRedteamObservationDao.instance;
            VerdictCounts totals = observationDao.countVerdicts(testingRunResultSummaryId, apiCollectionId, url, method);
            if (totals.getJudged() == 0) {
                return fail("nothing has been judged for this agent in this run; an empty version is never saved");
            }

            long now = System.currentTimeMillis();
            AgentRedteamProfile profile = new AgentRedteamProfile();
            setIdentity(profile, now);
            profile.setId(AgentRedteamProfile.buildVersionId(apiCollectionId, method, url, testingRunResultSummaryId, batchNo));
            profile.setBatchNo(batchNo);
            profile.setFinalVersion(Boolean.TRUE.equals(finalVersion));
            profile.setTotals(totals);
            profile.setUnjudgedConversations((int) observationDao.countUnjudged(testingRunResultSummaryId, apiCollectionId, url, method));
            profile.setCells(observationDao.aggregateCells(testingRunResultSummaryId, apiCollectionId, url, method));
            profile.setInsights(insights);

            AgentRedteamProfileDao.instance.ensureIndicesOnce();
            AgentRedteamProfileDao.instance.saveVersion(profile);

            int draftsInserted = 0;
            if (drafts != null && !drafts.isEmpty()) {
                for (AgentRedteamTemplateDraft draft : drafts) {
                    setIdentity(draft, now);
                    draft.setProfileVersionId(profile.getId());
                    draft.setUpdatedAtMs(now);
                }
                AgentRedteamTemplateDraftDao.instance.ensureIndicesOnce();
                draftsInserted = AgentRedteamTemplateDraftDao.instance.insertNew(drafts);
            }

            response = new LinkedHashMap<>();
            response.put("profileVersionId", profile.getId());
            response.put("totals", totals);
            response.put("draftsInserted", draftsInserted);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to save red-team profile", e);
        }
    }

    /** The agent's latest profile (without grid cells) for the attack enhancer; state NONE when nothing has been learned. */
    public String fetchLatestRedteamProfile() {
        try {
            String error = validateIdentity();
            if (error != null) return fail(error);
            AgentRedteamProfile latest = AgentRedteamProfileDao.instance.findLatest(apiCollectionId, url, method);
            response = new LinkedHashMap<>();
            response.put("state", latest == null ? STATE_NONE : STATE_READY);
            response.put("profile", latest);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return error("Failed to fetch red-team profile", e);
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    String validateIdentity() {
        if (apiCollectionId == null) return "apiCollectionId is required";
        url = clean(url, Integer.MAX_VALUE);
        if (url == null) return "url is required";
        if (url.length() > MAX_URL_CHARS) return "url is longer than " + MAX_URL_CHARS + " characters";
        method = method == null ? null : method.trim().toUpperCase();
        if (method == null || !METHODS.contains(method)) return "method must be one of " + METHODS;
        return null;
    }

    String validateSummaryId() {
        testingRunResultSummaryId = clean(testingRunResultSummaryId, Integer.MAX_VALUE);
        if (testingRunResultSummaryId == null || !SUMMARY_ID.matcher(testingRunResultSummaryId).matches()) {
            return "testingRunResultSummaryId may only contain letters, digits, '-' and '_' (at most 64 characters)";
        }
        return null;
    }

    /** Validates and caps one observation and sets what the server owns; returns why it is rejected, or null. */
    String prepareObservation(AgentRedteamObservation o, long now) {
        String id = clean(o.getId(), Integer.MAX_VALUE);
        if (id == null || id.length() > MAX_ID_CHARS) return "id is required (at most " + MAX_ID_CHARS + " characters)";
        o.setId(id);
        if (!OUTCOMES.contains(o.getOutcome())) return "outcome must be one of " + OUTCOMES;
        if (AgentRedteamObservation.OUTCOME_JUDGED.equals(o.getOutcome())) {
            if (!VERDICTS.contains(o.getVerdict())) return "a judged observation needs a verdict of 0, 1 or 2";
        } else {
            o.setVerdict(null); // only a judged conversation has a verdict
        }
        Integer confidence = o.getJudgeConfidence();
        if (confidence != null && (confidence < 0 || confidence > 100)) return "judgeConfidence must be between 0 and 100";
        if (o.getTurns() < 0 || o.getTurns() > MAX_TURNS) return "turns must be between 0 and " + MAX_TURNS;
        if (o.getResponseChars() < 0) return "responseChars must not be negative";
        if (o.getEventTimeMs() <= 0 || o.getEventTimeMs() > now + MAX_FUTURE_SKEW_MS) return "eventTimeMs must be a real event time in milliseconds";

        o.setAgentBehaviorClass(clean(o.getAgentBehaviorClass(), MAX_KEY_CHARS));
        o.setTestSubType(clean(o.getTestSubType(), MAX_SHORT_TEXT_CHARS));
        o.setTestCategory(clean(o.getTestCategory(), MAX_SHORT_TEXT_CHARS));
        o.setGridKey(cleanGridKey(o.getGridKey()));
        o.setEvidenceQuote(clean(o.getEvidenceQuote(), AgentRedteamObservation.MAX_EVIDENCE_QUOTE_CHARS));
        o.setJudgeReasoning(clean(o.getJudgeReasoning(), AgentRedteamObservation.MAX_JUDGE_REASONING_CHARS));
        o.setToolsSeen(cleanList(o.getToolsSeen(), AgentRedteamObservation.MAX_TOOLS_SEEN, MAX_SHORT_TEXT_CHARS));

        setIdentity(o, now);
        o.setExpireAt(new Date(o.getEventTimeMs() + TimeUnit.DAYS.toMillis(AgentRedteamObservation.RETENTION_DAYS)));
        return null;
    }

    /** Insights must be well-formed and grounded in at least one observation; a version without any is refused. */
    String prepareInsights() {
        if (insights == null || insights.isEmpty()) return "insights are required; a version without insights is never saved";
        if (insights.size() > AgentRedteamProfile.MAX_INSIGHTS) return "at most " + AgentRedteamProfile.MAX_INSIGHTS + " insights";
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < insights.size(); i++) {
            Insight insight = insights.get(i);
            if (insight == null) return "insight " + i + ": missing";
            insight.setId(clean(insight.getId(), Integer.MAX_VALUE));
            if (insight.getId() == null || insight.getId().length() > MAX_KEY_CHARS || !ids.add(insight.getId())) {
                return "insight " + i + ": id is required, unique, and at most " + MAX_KEY_CHARS + " characters";
            }
            if (!INSIGHT_KINDS.contains(insight.getKind())) return "insight " + i + ": kind must be one of " + INSIGHT_KINDS;
            insight.setText(clean(insight.getText(), Insight.MAX_TEXT_CHARS));
            if (insight.getText() == null) return "insight " + i + ": text is required";
            if (insight.getImportance() < 1 || insight.getImportance() > MAX_IMPORTANCE) {
                return "insight " + i + ": importance must be between 1 and " + MAX_IMPORTANCE;
            }
            insight.setEvidenceObservationIds(cleanList(insight.getEvidenceObservationIds(), MAX_REFERENCED_IDS, MAX_ID_CHARS));
            if (insight.getEvidenceObservationIds().isEmpty()) return "insight " + i + ": cites no observation";
        }
        return null;
    }

    /** Drafts must be complete, within the caps, and based only on insights in this request; their ids are built here. */
    String prepareDrafts() {
        if (drafts == null || drafts.isEmpty()) return null;
        if (drafts.size() > AgentRedteamTemplateDraft.MAX_DRAFTS_PER_RUN) {
            return "at most " + AgentRedteamTemplateDraft.MAX_DRAFTS_PER_RUN + " drafts per run";
        }
        Set<String> insightIds = new HashSet<>();
        for (Insight insight : insights) insightIds.add(insight.getId());
        for (int i = 0; i < drafts.size(); i++) {
            AgentRedteamTemplateDraft d = drafts.get(i);
            if (d == null) return "draft " + i + ": missing";
            d.setName(clean(d.getName(), Integer.MAX_VALUE));
            if (d.getName() == null || d.getName().length() > MAX_SHORT_TEXT_CHARS) return "draft " + i + ": name is required (at most " + MAX_SHORT_TEXT_CHARS + " characters)";
            d.setBasePrompt(clean(d.getBasePrompt(), Integer.MAX_VALUE));
            if (d.getBasePrompt() == null || d.getBasePrompt().length() > AgentRedteamTemplateDraft.MAX_BASE_PROMPT_CHARS) {
                return "draft " + i + ": basePrompt is required (at most " + AgentRedteamTemplateDraft.MAX_BASE_PROMPT_CHARS + " characters)";
            }
            d.setYamlContent(clean(d.getYamlContent(), Integer.MAX_VALUE));
            if (d.getYamlContent() == null || d.getYamlContent().length() > MAX_YAML_CHARS) return "draft " + i + ": yamlContent is required (at most " + MAX_YAML_CHARS + " characters)";
            List<String> strategies = cleanList(d.getStrategyKeys(), Integer.MAX_VALUE, Integer.MAX_VALUE);
            if (strategies.isEmpty() || strategies.size() > MAX_STRATEGIES_PER_DRAFT) return "draft " + i + ": between 1 and " + MAX_STRATEGIES_PER_DRAFT + " strategyKeys";
            for (String key : strategies) {
                if (key.length() > MAX_KEY_CHARS) return "draft " + i + ": a strategy key is longer than " + MAX_KEY_CHARS + " characters";
            }
            d.setStrategyKeys(strategies);
            d.setBasedOnInsightIds(cleanList(d.getBasedOnInsightIds(), MAX_REFERENCED_IDS, MAX_KEY_CHARS));
            if (d.getBasedOnInsightIds().isEmpty() || !insightIds.containsAll(d.getBasedOnInsightIds())) {
                return "draft " + i + ": basedOnInsightIds must name insights in this request";
            }
            d.setEvidenceObservationIds(cleanList(d.getEvidenceObservationIds(), MAX_REFERENCED_IDS, MAX_ID_CHARS));
            d.setMode(clean(d.getMode(), MAX_KEY_CHARS));
            d.setDescription(clean(d.getDescription(), MAX_LONG_TEXT_CHARS));
            d.setRationale(clean(d.getRationale(), MAX_LONG_TEXT_CHARS));
            d.setTestCategory(clean(d.getTestCategory(), MAX_SHORT_TEXT_CHARS));
            d.setBasedOnTestSubType(clean(d.getBasedOnTestSubType(), MAX_SHORT_TEXT_CHARS));

            d.setId(AgentRedteamTemplateDraft.buildId(apiCollectionId, method, url, d.getBasePrompt(), d.getStrategyKeys(), d.getMode()));
            d.setSource(AgentRedteamTemplateDraft.SOURCE_REDTEAM_LEARNING);
            d.setStatus(AgentRedteamTemplateDraft.STATUS_DRAFT);
            d.setSavedTemplateId(null);
        }
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setIdentity(AgentRedteamRecord record, long now) {
        record.setApiCollectionId(apiCollectionId);
        record.setUrl(url);
        record.setMethod(method);
        record.setTestingRunResultSummaryId(testingRunResultSummaryId);
        record.setCreatedAtMs(now);
    }

    private static Map<String, Object> sample(AgentRedteamObservation observation, List<AgentConversationResult> stored) {
        List<Map<String, Object>> turns = new ArrayList<>();
        for (AgentConversationResult turn : stored) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("sentPrompt", turn.getFinalSentPrompt());
            t.put("response", turn.getResponse());
            turns.add(t);
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("observation", observation);
        sample.put("turns", turns);
        // The turns are written when the whole conversation ends, so a very recent one may not be there yet.
        sample.put("turnsAvailable", !turns.isEmpty());
        return sample;
    }

    private static TemplateGridKey cleanGridKey(TemplateGridKey key) {
        TemplateGridKey cleaned = new TemplateGridKey();
        if (key == null) return cleaned;
        cleaned.setBasePromptKey(clean(key.getBasePromptKey(), MAX_KEY_CHARS));
        cleaned.setBasePromptLabel(clean(key.getBasePromptLabel(), TemplateGridKey.MAX_LABEL_CHARS));
        cleaned.setStrategyKey(clean(key.getStrategyKey(), MAX_KEY_CHARS));
        cleaned.setStrategyLabel(clean(key.getStrategyLabel(), TemplateGridKey.MAX_LABEL_CHARS));
        return cleaned;
    }

    /** Trimmed and capped; blank becomes null, because an empty string is never stored. */
    static String clean(String value, int maxChars) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > maxChars ? trimmed.substring(0, maxChars) : trimmed;
    }

    /** Distinct non-blank values, each capped, at most maxItems of them; never null. */
    static List<String> cleanList(List<String> values, int maxItems, int maxChars) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) return cleaned;
        for (String value : values) {
            String c = clean(value, maxChars);
            if (c != null && !cleaned.contains(c)) cleaned.add(c);
            if (cleaned.size() >= maxItems) break;
        }
        return cleaned;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private String fail(String message) {
        addActionError(message);
        return Action.ERROR.toUpperCase();
    }

    private String error(String message, Exception e) {
        logger.errorAndAddToDb(e, message + ": " + e.getMessage());
        addActionError(message);
        return Action.ERROR.toUpperCase();
    }
}
