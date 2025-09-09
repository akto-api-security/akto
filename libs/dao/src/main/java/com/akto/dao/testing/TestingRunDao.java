package com.akto.dao.testing;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingRun;
import com.mongodb.client.model.CreateCollectionOptions;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class TestingRunDao extends AccountsContextDao<TestingRun> {

    public static final TestingRunDao instance = new TestingRunDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] fieldNames = {TestingRun.SCHEDULE_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRun.END_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRun._API_COLLECTION_ID, TestingRun.END_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRun.NAME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);
    }
    
    public List<Integer> getTestConfigIdsToDelete(List<ObjectId> testingRunIds){
        // this function is to get list of testConfigIds from testingRunIds for deleting from testing_run_config collection in DB.
        Bson filter = Filters.in("_id", testingRunIds);
        MongoCursor<TestingRun> cursor = instance.getMCollection().find(filter).projection(Projections.include("testIdConfig")).cursor();
        List<Integer> testConfigIds = new ArrayList<>();

        try {
            while(cursor.hasNext()){
                TestingRun testingRun = cursor.next();
                Integer testConfigId = testingRun.getTestIdConfig();
                testConfigIds.add(testConfigId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return testConfigIds;
    }

    public List<ObjectId> getSummaryIdsFromRunIds(List<ObjectId> testRunIds){
        //this function is to get list of summaryids from run ids

        Bson filter = Filters.in(TestingRunResultSummary.TESTING_RUN_ID, testRunIds);

        MongoCursor<TestingRunResultSummary> cursor = TestingRunResultSummariesDao.instance.getMCollection().find(filter).projection(Projections.include("_id")).cursor();
        List<ObjectId> testingSummaryIds = new ArrayList<>();

        try {
            while(cursor.hasNext()){
                TestingRunResultSummary testingRunResultSummary = cursor.next();
                ObjectId id = testingRunResultSummary.getId();
                testingSummaryIds.add(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return testingSummaryIds;
    }

    public boolean isStoredInVulnerableCollection(ObjectId testingRunId){
        if(testingRunId == null){
            return false;
        }
        return instance.count(
            Filters.and(
                Filters.eq(Constants.ID, testingRunId),
                Filters.eq(TestingRun.IS_NEW_TESTING_RUN, true)
            )
        ) > 0;
    }

    private Bson addCollectionsFilterForIAM(Bson q) {
        List<Integer> apiCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                Context.accountId.get());
        Bson collectionFilter = Filters.or(
                Filters.in(TestingRun._API_COLLECTION_ID, apiCollectionIds),
                Filters.in(TestingRun._API_COLLECTION_ID_WORK_FLOW, apiCollectionIds),
                Filters.in(TestingRun._API_COLLECTION_ID_IN_LIST, apiCollectionIds)
        );
        return Filters.and(q, collectionFilter);
    }

    @Override
    public List<TestingRun> findAll(Bson q, int skip, int limit, Bson sort, Bson projection) {
        Bson finalFilter = Filters.and(q, addCollectionsFilterForIAM(q));
        return super.findAll(finalFilter, skip, limit, sort, projection);
    }

    @Override
    public long count(Bson q) {
        Bson finalFilter = Filters.and(q, addCollectionsFilterForIAM(q));
        return super.count(finalFilter);
    }

    @Override
    public String getCollName() {
        return "testing_run";
    }

    @Override
    public Class<TestingRun> getClassT() {
        return TestingRun.class;
    }
}
