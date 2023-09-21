package com.akto.dao.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.TestingRunResultSummary;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
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

    public Map<ObjectId, TestingRunResultSummary> fetchLatestTestingRunResultSummaries() {
        Map<ObjectId, TestingRunResultSummary> trss = new HashMap<>();
        try {
            List<Bson> pipeline = new ArrayList<>();
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

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());
        
        Bson testingRunIndex = Indexes.ascending(TestingRunResultSummary.TESTING_RUN_ID);
        createIndexIfAbsent(dbName, getCollName(), testingRunIndex, new IndexOptions().name(getIndexName(testingRunIndex)));

    }

    @Override
    public Class<TestingRunResultSummary> getClassT() {
        return TestingRunResultSummary.class;
    }
}
