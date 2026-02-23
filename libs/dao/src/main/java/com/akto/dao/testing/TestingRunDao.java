package com.akto.dao.testing;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingRun;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
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

        fieldNames = new String[]{TestingRun.DASHBOARD_CONTEXT};
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
                Filters.in(TestingRun._API_COLLECTION_ID_IN_LIST, apiCollectionIds),
                Filters.in(TestingRun._API_COLLECTION_IDS_MULTI, apiCollectionIds)
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

    /**
     * Finds test runs with RBAC and context-based filtering for dashboard views.
     * Applies collection access filtering and dashboardContext filtering for context-based dashboards.
     */
    public List<TestingRun> findAllWithRbacAndContext(Bson q, int skip, int limit, Bson sort) {
        Bson finalFilter = addCollectionsFilterForDashboard(q);
        return super.findAll(finalFilter, skip, limit, sort, null);
    }

    /**
     * Counts test runs with RBAC and context-based filtering for dashboard views.
     * Applies collection access filtering and dashboardContext filtering for context-based dashboards.
     */
    public long countWithRbacAndContext(Bson q) {
        Bson finalFilter = addCollectionsFilterForDashboard(q);
        return super.count(finalFilter);
    }

    /**
     * Applies RBAC and context-based filtering for dashboard queries.
     * - Admin users viewing user-based dashboard (contextSource=null): show all
     * - RBAC disabled: filter by dashboardContext if contextSource is set
     * - RBAC enabled: filter by accessible collections, and include matching dashboardContext for admins in context-based dashboards
     */
    private Bson addCollectionsFilterForDashboard(Bson q) {
        
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        boolean isAdmin = RBACDao.getCurrentRoleForUser(Context.userId.get(), Context.accountId.get()) == Role.ADMIN;
        
        // Admin viewing user-based dashboard: show all
        if (isAdmin && contextSource == null) {
            return q;
        }
      
        List<Integer> apiCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                Context.accountId.get()); 
        if (apiCollectionIds == null || (apiCollectionIds.isEmpty() && isAdmin)) {
            // RBAC disabled or admin with empty list: show all if no context, otherwise filter by dashboardContext
            return contextSource == null ? q : Filters.and(q, Filters.eq(TestingRun.DASHBOARD_CONTEXT, contextSource));
        }
        
        if (apiCollectionIds.isEmpty()) {
            // Non-admin with empty list: no access
            return Filters.and(q, Filters.empty());
        }
        
        // Reuse collection filter builder
        Bson collectionFilter = Filters.or(
            Filters.in(TestingRun._API_COLLECTION_ID, apiCollectionIds),
            Filters.in(TestingRun._API_COLLECTION_ID_WORK_FLOW, apiCollectionIds),
            Filters.in(TestingRun._API_COLLECTION_ID_IN_LIST, apiCollectionIds),
            Filters.in(TestingRun._API_COLLECTION_IDS_MULTI, apiCollectionIds)
        );
        
        // Admin viewing context-based dashboard: also include matching dashboardContext (for deleted collections)
        if (contextSource != null && isAdmin) {
            Bson dashboardContextFilter = Filters.eq(TestingRun.DASHBOARD_CONTEXT, contextSource);      
            collectionFilter = Filters.or(collectionFilter, dashboardContextFilter);
        }
        
        return Filters.and(q, collectionFilter);
    }

    @Override
    public String getCollName() {
        return "testing_run";
    }

    /**
     * Creates a filter for finding scheduled testing runs that should have started.
     * This matches the filter used in Main.findPendingTestingRun for scheduled tests.
     * 
     * @param maxScheduleTimestamp Maximum schedule timestamp (typically current time or current time - buffer)
     * @return Bson filter for scheduled testing runs
     */
    public Bson createScheduledTestingRunFilter(int maxScheduleTimestamp) {
        return Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
            Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, maxScheduleTimestamp)
        );
    }

    @Override
    public Class<TestingRun> getClassT() {
        return TestingRun.class;
    }
}
