package com.akto.dao.redteam_learnings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dto.redteam_learnings.AgentRedteamObservation;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.GridCell;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.TemplateGridKey;
import com.akto.dto.redteam_learnings.VerdictCounts;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;

public class AgentRedteamObservationDao extends AgentRedteamDao<AgentRedteamObservation> {

    public static final String COLLECTION_NAME = "agent_redteam_observations";
    /** Serves counting a run's judged conversations per agent, and the per-cell aggregation. */
    public static final String RUN_IDENTITY_OUTCOME_INDEX = "testingRunResultSummaryId_apiCollectionId_url_method_outcome";
    public static final String EXPIRE_AT_TTL_INDEX = "expireAt_ttl";

    private static final String GRID = "$" + AgentRedteamObservation.GRID_KEY + ".";

    public static final AgentRedteamObservationDao instance = new AgentRedteamObservationDao();

    private AgentRedteamObservationDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentRedteamObservation> getClassT() {
        return AgentRedteamObservation.class;
    }

    @Override
    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(RUN_IDENTITY_OUTCOME_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID,
                AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD,
                AgentRedteamObservation.OUTCOME));
        createTtlIndex(EXPIRE_AT_TTL_INDEX, AgentRedteamObservation.EXPIRE_AT);
    }

    @Override
    protected List<String> indexNames() {
        return Arrays.asList(RUN_IDENTITY_OUTCOME_INDEX, EXPIRE_AT_TTL_INDEX);
    }

    /** Writes each observation whole under its conversation id, so a retried batch replaces rather than adds. */
    public void upsertAll(List<AgentRedteamObservation> observations) {
        if (observations == null || observations.isEmpty()) return;
        List<WriteModel<AgentRedteamObservation>> ops = new ArrayList<>();
        for (AgentRedteamObservation observation : observations) {
            ops.add(new ReplaceOneModel<>(Filters.eq("_id", observation.getId()), observation, new ReplaceOptions().upsert(true)));
        }
        bulkWrite(ops, new BulkWriteOptions().ordered(false));
    }

    public long countJudged(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        return count(Filters.and(runFilter(testingRunResultSummaryId, apiCollectionId, url, method),
                Filters.eq(AgentRedteamObservation.OUTCOME, AgentRedteamObservation.OUTCOME_JUDGED)));
    }

    /** Conversations without a verdict: target errors and judge failures. */
    public long countUnjudged(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        return count(Filters.and(runFilter(testingRunResultSummaryId, apiCollectionId, url, method),
                Filters.ne(AgentRedteamObservation.OUTCOME, AgentRedteamObservation.OUTCOME_JUDGED)));
    }

    /** Verdict counts over all of the run's judged conversations for the agent (not limited like the cells). */
    public VerdictCounts countVerdicts(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(runFilter(testingRunResultSummaryId, apiCollectionId, url, method),
                        Filters.eq(AgentRedteamObservation.OUTCOME, AgentRedteamObservation.OUTCOME_JUDGED))),
                Aggregates.group("$" + AgentRedteamObservation.VERDICT, Accumulators.sum("count", 1)));
        VerdictCounts counts = new VerdictCounts();
        for (Document row : getMCollection().aggregate(pipeline, Document.class)) {
            int count = row.getInteger("count");
            counts.setJudged(counts.getJudged() + count);
            String verdict = row.getString("_id");
            if (AgentRedteamObservation.VERDICT_CRITICAL.equals(verdict)) counts.setCritical(count);
            else if (AgentRedteamObservation.VERDICT_ATTEMPTED.equals(verdict)) counts.setAttempted(count);
            else if (AgentRedteamObservation.VERDICT_NORMAL.equals(verdict)) counts.setNormal(count);
        }
        return counts;
    }

    /**
     * Verdict counts per base prompt x strategy over the run's judged conversations, largest cells first. A template
     * with neither wordlist forms a cell of its own, keyed by its test sub type.
     */
    public List<GridCell> aggregateCells(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        Document cellKey = new Document(TemplateGridKey.BASE_PROMPT_KEY, GRID + TemplateGridKey.BASE_PROMPT_KEY)
                .append(TemplateGridKey.STRATEGY_KEY, GRID + TemplateGridKey.STRATEGY_KEY)
                .append(AgentRedteamObservation.TEST_SUB_TYPE, new Document("$cond", Arrays.asList(
                        new Document("$and", Arrays.asList(isNull(GRID + TemplateGridKey.BASE_PROMPT_KEY),
                                isNull(GRID + TemplateGridKey.STRATEGY_KEY))),
                        "$" + AgentRedteamObservation.TEST_SUB_TYPE,
                        null)));
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(runFilter(testingRunResultSummaryId, apiCollectionId, url, method),
                        Filters.eq(AgentRedteamObservation.OUTCOME, AgentRedteamObservation.OUTCOME_JUDGED))),
                Aggregates.group(cellKey,
                        Accumulators.first("basePromptLabel", GRID + "basePromptLabel"),
                        Accumulators.first("strategyLabel", GRID + "strategyLabel"),
                        Accumulators.sum("judged", 1),
                        Accumulators.sum("critical", verdictIs(AgentRedteamObservation.VERDICT_CRITICAL)),
                        Accumulators.sum("attempted", verdictIs(AgentRedteamObservation.VERDICT_ATTEMPTED)),
                        Accumulators.sum("normal", verdictIs(AgentRedteamObservation.VERDICT_NORMAL))),
                Aggregates.sort(Sorts.descending("judged")),
                Aggregates.limit(AgentRedteamProfile.MAX_CELLS));

        List<GridCell> cells = new ArrayList<>();
        for (Document row : getMCollection().aggregate(pipeline, Document.class)) {
            Document key = row.get("_id", Document.class);
            GridCell cell = new GridCell();
            cell.getGridKey().setBasePromptKey(key.getString(TemplateGridKey.BASE_PROMPT_KEY));
            cell.getGridKey().setBasePromptLabel(row.getString("basePromptLabel"));
            cell.getGridKey().setStrategyKey(key.getString(TemplateGridKey.STRATEGY_KEY));
            cell.getGridKey().setStrategyLabel(row.getString("strategyLabel"));
            cell.setTestSubType(key.getString(AgentRedteamObservation.TEST_SUB_TYPE));
            cell.getCounts().setJudged(row.getInteger("judged"));
            cell.getCounts().setCritical(row.getInteger("critical"));
            cell.getCounts().setAttempted(row.getInteger("attempted"));
            cell.getCounts().setNormal(row.getInteger("normal"));
            cells.add(cell);
        }
        return cells;
    }

    /** The run's most recent judged conversations with one verdict, carrying only what consolidation reads. */
    public List<AgentRedteamObservation> findSamples(String testingRunResultSummaryId, int apiCollectionId, String url,
                                                     String method, String verdict, int limit) {
        return findAll(Filters.and(runFilter(testingRunResultSummaryId, apiCollectionId, url, method),
                        Filters.eq(AgentRedteamObservation.OUTCOME, AgentRedteamObservation.OUTCOME_JUDGED),
                        Filters.eq(AgentRedteamObservation.VERDICT, verdict)),
                0, limit, Sorts.descending(AgentRedteamObservation.EVENT_TIME_MS),
                Projections.include(AgentRedteamObservation.VERDICT, AgentRedteamObservation.AGENT_BEHAVIOR_CLASS,
                        AgentRedteamObservation.TEST_SUB_TYPE, AgentRedteamObservation.GRID_KEY,
                        AgentRedteamObservation.EVIDENCE_QUOTE, AgentRedteamObservation.JUDGE_REASONING,
                        AgentRedteamObservation.TOOLS_SEEN, AgentRedteamObservation.EVENT_TIME_MS));
    }

    /** A missing field counts as null, which a plain $eq against null would not do. */
    private static Document isNull(String fieldPath) {
        return new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList(fieldPath, null)), null));
    }

    private static Document verdictIs(String verdict) {
        return new Document("$cond", Arrays.asList(
                new Document("$eq", Arrays.asList("$" + AgentRedteamObservation.VERDICT, verdict)), 1, 0));
    }
}
