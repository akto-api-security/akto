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
import com.mongodb.client.model.Sorts;

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

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());
        
        Bson testingRunIndex = Indexes.ascending(TestingRunResultSummary.TESTING_RUN_ID);
        createIndexIfAbsent(dbName, getCollName(), testingRunIndex, new IndexOptions().name("testingRunId_1"));

        String[] fieldNames = {TestingRunResultSummary.START_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRunResultSummary.END_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);


        IndexOptions sparseIndex = new IndexOptions().sparse(true);

        Bson branchIndex = Indexes.ascending("metadata.branch");
        createIndexIfAbsent(dbName, getCollName(), branchIndex, sparseIndex.name("metadata.branch_1"));
        Bson repositoryIndex = Indexes.ascending("metadata.repository");
        createIndexIfAbsent(dbName, getCollName(), repositoryIndex, sparseIndex.name("metadata.repository_1"));

    }

    @Override
    public Class<TestingRunResultSummary> getClassT() {
        return TestingRunResultSummary.class;
    }
}
