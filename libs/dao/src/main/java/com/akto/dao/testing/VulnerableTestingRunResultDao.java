package com.akto.dao.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dao.insights.agentic.AgentFindingGroupAggregation;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.insights.agentic.DailyCount;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UnwindOptions;
import java.util.Arrays;

public class VulnerableTestingRunResultDao extends TestingRunResultDao {

    public static final VulnerableTestingRunResultDao instance = new VulnerableTestingRunResultDao();

    @Override
    public void createIndicesIfAbsent() {
        
        String dbName = Context.accountId.get()+"";

        CreateCollectionOptions createCollectionOptions = new CreateCollectionOptions();
        createCollectionIfAbsent(dbName, getCollName(), createCollectionOptions);

        
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID }, false);
        
        String[] fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_RESULTS+"."+GenericTestResult._CONFIDENCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_SUPER_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index for querying by apiInfoKey + testSubType (without testRunResultSummaryId)
        // Used by bulkUpdateTestResultsSeverity endpoint
        fieldNames = new String[]{TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public boolean isStoredInVulnerableCollection(ObjectId objectId, boolean isSummary){
        if(!isSummary){
            return TestingRunDao.instance.isStoredInVulnerableCollection(objectId);
        }else {
            try {
                Bson filter = Filters.and(
                    Filters.eq(Constants.ID, objectId),
                    Filters.or(
                        Filters.eq(TestingRunResultSummary.IS_NEW_TESTING_RUN_RESULT_SUMMARY, true),
                        Filters.eq(TestingRunResultSummary.IS_NEW_TESTING_RUN_RESULT_SUMMARY_OLD, true)
                    )
                );
                boolean isNew = TestingRunResultSummariesDao.instance.count(filter) > 0;
                return isNew;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public int countFromDb(Bson filter, boolean isVulnerable){
        if(isVulnerable){
            int count = (int) instance.count(filter);
            if(count != 0){
                return count;
            }
        }
        return (int) TestingRunResultDao.instance.count(filter);
    }

    public List<TestingRunResult> fetchLatestTestingRunResultWithCustomAggregations(Bson filters, int limit, int skip, Bson customSort, ObjectId summaryId, boolean isVulnerable) {
        if(isVulnerable && instance.isStoredInVulnerableCollection(summaryId, true)){
            return instance.fetchLatestTestingRunResultWithCustomAggregations(filters, limit, skip, customSort);
        }else{
            if (isVulnerable) {
                filters = Filters.and(filters, Filters.eq(TestingRunResult.VULNERABLE, true));
            }
            return TestingRunResultDao.instance.fetchLatestTestingRunResultWithCustomAggregations(filters, limit, skip, customSort);
        }
    }

    public TestingRunResult findOneWithComparison(Bson q, Bson projection) {
        TestingRunResult tr = super.findOneNoRbacFilter(q, projection);
        if(tr == null){
            return TestingRunResultDao.instance.findOneNoRbacFilter(q, projection);
        }
        return tr;
    }

    public List<TestingRunResult> findAll(Bson q, Bson projection, boolean isStoredInVulnerableCollection) {
        if(isStoredInVulnerableCollection){
            return instance.findAll(q,projection);
        }
        return TestingRunResultDao.instance.findAll(q, projection);
    }

    public List<ObjectId> summaryIdsStoredForVulnerableTests(){
        String groupedId = "summaries";
        List<Bson> pipeLine = new ArrayList<>();
        pipeLine.add(
            Aggregates.group(groupedId, Accumulators.addToSet("summaryIds", "$" + TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID))
        );
        try {
            MongoCursor<BasicDBObject> cursor = instance.getMCollection().aggregate(pipeLine, BasicDBObject.class).cursor();
            List<ObjectId> uniqueSummaries = new ArrayList<>();
            while (cursor.hasNext()) {
                BasicDBObject dbObject = cursor.next();
                uniqueSummaries = (List<ObjectId>) dbObject.get("summaryIds");
            }
            return uniqueSummaries;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        
    }

    /**
     * Argus (AGENTIC) red-team read: vulnerable results scoped to a small, already-time-windowed
     * `testRunResultSummaryId` set (see TestingRunResultSummariesDao#summaryIdsInWindow — this
     * collection has no endTimestamp index of its own, so the window is applied there, against a
     * small/indexed collection, rather than here) and to the agentic collection ids, using the
     * same `{testRunResultSummaryId, apiInfoKey, testSubType}` index this DAO already declares.
     * RBAC + dashboard-context scoped via modifyFilters (ignoreGroupFilter=false,
     * ignoreSummaryIdFilter=false), the same call findAllWithSummaryContext already makes.
     *
     * Three facets over one $match, never the raw documents: per-{collection, testSubType}
     * counts/lastSeen (the finding groups — same AgentFindingGroupAggregation shape
     * TestingRunIssuesDao/McpAuditInfoDao use, since a scalar addToSet works for their sample
     * fields but NOT here), per-{collection, testSubType} conversationId samples (needs its own
     * $unwind first — testResults is an array, so a plain addToSet on testResults.conversationId
     * would add one array-of-arrays per group instead of the flattened ids AgentFindingGroup
     * expects), and per-{collection, day} trend counts — merged in Java rather than flattening
     * nested arrays inside the aggregation itself.
     */
    public Pair<List<AgentFindingGroup>, List<DailyCount>> redTeamAggregates(List<ObjectId> summaryIds, List<Integer> collectionIds, int convIdsPerGroupCap) {
        if (summaryIds == null || summaryIds.isEmpty() || collectionIds == null || collectionIds.isEmpty()) {
            return new Pair<>(new ArrayList<>(), new ArrayList<>());
        }

        Bson filter = modifyFilters(Filters.and(
                Filters.eq(TestingRunResult.VULNERABLE, true),
                Filters.in(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryIds),
                Filters.in(getFilterKeyString(), collectionIds)), false, false);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        // Project once, before the facets fork, so every facet groups on the same short field
        // names instead of re-deriving "$apiInfoKey.apiCollectionId" three times.
        pipeline.add(Aggregates.project(Projections.fields(
                Projections.excludeId(),
                Projections.computed("collId", "$" + getFilterKeyString()),
                Projections.computed("subType", "$" + TestingRunResult.TEST_SUB_TYPE),
                Projections.include(TestingRunResult.END_TIMESTAMP),
                Projections.include(TestingRunResult.TEST_RESULTS + "." + TestResult.CONVERSATION_ID))));

        Bson groupKey = new BasicDBObject("collId", "$collId").append("subType", "$subType");
        pipeline.add(Aggregates.facet(
                new Facet("counts", AgentFindingGroupAggregation.groupAndProject(
                        "collId", "subType", null, TestingRunResult.END_TIMESTAMP, null, 0).toArray(new Bson[0])),
                new Facet("conversations",
                        Aggregates.unwind("$" + TestingRunResult.TEST_RESULTS, new UnwindOptions().preserveNullAndEmptyArrays(true)),
                        Aggregates.match(Filters.exists(TestingRunResult.TEST_RESULTS + "." + TestResult.CONVERSATION_ID, true)),
                        Aggregates.sort(Sorts.descending(TestingRunResult.END_TIMESTAMP)),
                        Aggregates.group(groupKey,
                                Accumulators.push("conversationIds", "$" + TestingRunResult.TEST_RESULTS + "." + TestResult.CONVERSATION_ID)),
                        // _id (the {collId, subType} group key) must survive this project — the Java
                        // side below reads doc.get("_id") to key conversationIdsByKey. Do NOT add
                        // Projections.excludeId() here: with _id excluded, doc.get("_id") returns
                        // null and the very next .getInt() call on it NPEs.
                        Aggregates.project(Projections.fields(Projections.include("conversationIds"),
                                Projections.computed("sliced", new BasicDBObject("$slice", Arrays.asList("$conversationIds", convIdsPerGroupCap)))))),
                new Facet("trend",
                        Aggregates.project(Projections.fields(Projections.include("collId"),
                                Projections.computed("day", new BasicDBObject("$floor", new BasicDBObject("$divide",
                                        Arrays.asList("$" + TestingRunResult.END_TIMESTAMP, 86400)))))),
                        Aggregates.group(new BasicDBObject("collId", "$collId").append("day", "$day"), Accumulators.sum("count", 1)))));

        BasicDBObject facets = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).first();
        List<AgentFindingGroup> groups = new ArrayList<>();
        List<DailyCount> trend = new ArrayList<>();
        if (facets == null) {
            return new Pair<>(groups, trend);
        }

        Map<String, List<String>> conversationIdsByKey = new HashMap<>();
        for (BasicDBObject doc : safeList(facets.get("conversations"))) {
            BasicDBObject id = (BasicDBObject) doc.get("_id");
            List<String> convIds = new ArrayList<>();
            Object sliced = doc.get("sliced");
            if (sliced instanceof List) {
                for (Object o : (List<?>) sliced) if (o != null) convIds.add(o.toString());
            }
            conversationIdsByKey.put(keyOf(id.getInt("collId"), id.getString("subType")), convIds);
        }

        for (AgentFindingGroup g : AgentFindingGroupAggregation.parse(safeList(facets.get("counts")))) {
            List<String> convIds = conversationIdsByKey.getOrDefault(keyOf(g.getCollectionId(), g.getType()), new ArrayList<>());
            groups.add(new AgentFindingGroup(g.getCollectionId(), g.getType(), null, g.getCount(), g.getLastSeen(), convIds));
        }

        for (BasicDBObject doc : safeList(facets.get("trend"))) {
            BasicDBObject id = (BasicDBObject) doc.get("_id");
            trend.add(new DailyCount(id.getInt("collId"), id.getInt("day"), doc.getInt("count")));
        }

        return new Pair<>(groups, trend);
    }

    /**
     * A handful of real conversationIds behind one specific {agent, vulnType} pair — used by the
     * Argus "attack flow" insight card to ground its AI narrative in an actual validated
     * conversation instead of just aggregate counts. Same {@code apiInfoKey.apiCollectionId,
     * testSubType} match shape redTeamAggregates already uses, just scoped to one pair (cheaper,
     * not a new query shape) rather than $in lists over a whole summaryId/collectionId set.
     * testSubType here is the same classification value as TestingRunIssues' own testSubCategory
     * (AgentFindingGroup#getType()) — the two collections share that value space.
     */
    public List<String> conversationIdsForIssue(int apiCollectionId, String testSubType, int limit) {
        if (testSubType == null) return new ArrayList<>();

        Bson filter = modifyFilters(Filters.and(
                Filters.eq(getFilterKeyString(), apiCollectionId),
                Filters.eq(TestingRunResult.TEST_SUB_TYPE, testSubType)), false, false);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.sort(Sorts.descending(Constants.ID)));
        pipeline.add(Aggregates.limit(Math.max(limit, 1) * 5)); // a few raw docs before the unwind fans them out
        pipeline.add(Aggregates.unwind("$" + TestingRunResult.TEST_RESULTS, new UnwindOptions().preserveNullAndEmptyArrays(true)));
        pipeline.add(Aggregates.match(Filters.exists(TestingRunResult.TEST_RESULTS + "." + TestResult.CONVERSATION_ID, true)));
        pipeline.add(Aggregates.project(Projections.fields(Projections.excludeId(),
                Projections.include(TestingRunResult.TEST_RESULTS + "." + TestResult.CONVERSATION_ID))));
        pipeline.add(Aggregates.limit(limit));

        List<String> conversationIds = new ArrayList<>();
        for (BasicDBObject doc : instance.getMCollection().aggregate(pipeline, BasicDBObject.class).into(new ArrayList<>())) {
            Object testResults = doc.get(TestingRunResult.TEST_RESULTS);
            if (!(testResults instanceof BasicDBObject)) continue;
            Object conversationId = ((BasicDBObject) testResults).get(TestResult.CONVERSATION_ID);
            if (conversationId != null) conversationIds.add(conversationId.toString());
        }
        return conversationIds;
    }

    @SuppressWarnings("unchecked")
    private List<BasicDBObject> safeList(Object o) {
        return o instanceof List ? (List<BasicDBObject>) o : new ArrayList<>();
    }

    private String keyOf(int collId, String type) {
        return collId + "|" + type;
    }

    @Override
    public String getCollName() {
        return "vulnerable_testing_run_results";
    }
}
