package com.akto.dao.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.MCollection;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class TestingRunResultSummariesDao extends AccountsContextDao<TestingRunResultSummary> {

    public static final TestingRunResultSummariesDao instance = new TestingRunResultSummariesDao();

    private TestingRunResultSummariesDao() {}

    @Override
    public String getCollName() {
        return "testing_run_result_summaries";
    }

    public Map<ObjectId, TestingRunResultSummary> fetchLatestTestingRunResultSummaries(List<ObjectId> testingRunHexIds) {
        Map<ObjectId, TestingRunResultSummary> trss = new HashMap<>();
        try {
            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(Filters.in(TestingRunResultSummary.TESTING_RUN_ID,testingRunHexIds)));
            BasicDBObject groupedId = new BasicDBObject(TestingRunResultSummary.TESTING_RUN_ID, "$testingRunId");
            pipeline.add(Aggregates.sort(Sorts.descending(TestingRunResultSummary.START_TIMESTAMP)));
            pipeline.add(Aggregates.group(groupedId,
                    Accumulators.first("data", "$$ROOT")));
            pipeline.add(Aggregates.replaceRoot("$data"));
            MongoCursor<TestingRunResultSummary> endpointsCursor = TestingRunResultSummariesDao.instance
                    .getMCollection().aggregate(pipeline, TestingRunResultSummary.class).cursor();
            while (endpointsCursor.hasNext()) {
                TestingRunResultSummary temp = endpointsCursor.next();
                trss.put(temp.getTestingRunId(), temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return trss;
    }

    public Map<String, Set<String>> fetchMetadataFilters(List<String> filterKeys) {
        Map<String, Set<String>> ret = new HashMap<>();

        try {
            List<Bson> pipeline = new ArrayList<>();
            BasicDBObject groupedId = new BasicDBObject(Constants.ID, "0");

            List<Bson> filters = new ArrayList<>();
            List<BsonField> acc = new ArrayList<>();
            for(String filter : filterKeys){
                filters.add(Filters.exists(TestingRunResultSummary.METADATA_STRING + "." + filter));
                acc.add(Accumulators.addToSet(filter, "$" + TestingRunResultSummary.METADATA_STRING + "." + filter));
            }

            pipeline.add(Aggregates.match(Filters.or(filters)));
            pipeline.add(Aggregates.group(groupedId, acc));
            MongoCursor<BasicDBObject> endpointsCursor = TestingRunResultSummariesDao.instance
                    .getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            while (endpointsCursor.hasNext()) {
                BasicDBObject temp = endpointsCursor.next();
                
                for(String filter : filterKeys){
                    BasicDBList list = (BasicDBList) temp.get(filter);
                    Set<String> dataSet = ret.getOrDefault(filter, new HashSet<>());
                    for(Object data: list){
                        dataSet.add((String)data);
                    }
                    ret.put(filter, dataSet);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    public void bulkUpdateTestingRunResultSummariesCount(Map<ObjectId,Map<String,Integer>> summaryWiseCountMap){

        ArrayList<WriteModel<TestingRunResultSummary>> bulkUpdates = new ArrayList<>();
        for(ObjectId summaryId: summaryWiseCountMap.keySet()){

            Map<String,Integer> countIssuesMap = summaryWiseCountMap.get(summaryId);

            Bson update = Updates.combine(
                    Updates.inc("countIssues.HIGH", (-1 * countIssuesMap.get("HIGH"))),
                    Updates.inc("countIssues.MEDIUM", (-1 * countIssuesMap.get("MEDIUM"))),
                    Updates.inc("countIssues.LOW", (-1 * countIssuesMap.get("LOW")))
                );

            bulkUpdates.add(
                new UpdateOneModel<>(Filters.eq("_id",summaryId), update, new UpdateOptions().upsert(false))
            );
        }

        instance.getMCollection().bulkWrite(bulkUpdates);

    }

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        Bson testingRunIndex = Indexes.ascending(TestingRunResultSummary.TESTING_RUN_ID);
        createIndexIfAbsent(dbName, getCollName(), testingRunIndex, new IndexOptions().name("testingRunId_1"));

        String[] fieldNames = {TestingRunResultSummary.START_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRunResultSummary.END_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        Bson compoundIndex = Indexes.compoundIndex(
                Indexes.ascending(TestingRunResultSummary.TESTING_RUN_ID),
                Indexes.descending(TestingRunResultSummary.START_TIMESTAMP)
        );
        createIndexIfAbsent(dbName, getCollName(), compoundIndex,
                new IndexOptions().name("testingRunId_1_startTimestamp_-1"));

        IndexOptions sparseIndex = new IndexOptions().sparse(true);

        Bson branchIndex = Indexes.ascending("metadata.branch");
        createIndexIfAbsent(dbName, getCollName(), branchIndex, sparseIndex.name("metadata.branch_1"));
        Bson repositoryIndex = Indexes.ascending("metadata.repository");
        createIndexIfAbsent(dbName, getCollName(), repositoryIndex, sparseIndex.name("metadata.repository_1"));

    }

    public List<TestingRunResultSummary> getCurrentRunningTestsSummaries(){
        int filterTime = Context.now() - 12 * 60 * 60;
        List<TestingRunResultSummary> trrs = TestingRunResultSummariesDao.instance.findAll(
            Filters.and(
                Filters.eq(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                Filters.gte(TestingRunResultSummary.START_TIMESTAMP, filterTime)
            ),
            Projections.include(TestingRunResultSummary.TESTING_RUN_ID, TestingRunResultSummary.TOTAL_APIS, TestingRunResultSummary.TESTS_INITIATED_COUNT, TestingRunResultSummary.TEST_RESULTS_COUNT)
        );
        return trrs;
    }

    @Override
    public Class<TestingRunResultSummary> getClassT() {
        return TestingRunResultSummary.class;
    }
}
