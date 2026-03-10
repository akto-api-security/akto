package com.akto.dao.testing_run_findings;

import java.util.*;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.MCollection;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UnwindOptions;
import org.bson.types.ObjectId;
import java.util.stream.Collectors;

public class TestingRunIssuesDao extends AccountsContextDaoWithRbac<TestingRunIssues> {

    public static final TestingRunIssuesDao instance = new TestingRunIssuesDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.CREATION_TIME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
        
        fieldNames = new String[]{TestingRunIssues.TEST_RUN_ISSUES_STATUS, "_id.apiInfoKey.apiCollectionId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);    
        
        fieldNames = new String[]{TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.LAST_SEEN};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[] {TestingRunIssues.TEST_RUN_ISSUES_STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
        fieldNames = new String[] {TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames =  new String[] {Constants.ID, TestingRunIssues.TEST_RUN_ISSUES_STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{TestingRunIssues.TICKET_PROJECT_KEY, TestingRunIssues.TICKET_SOURCE,
            TestingRunIssues.LAST_UPDATED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{TestingRunIssues.TICKET_PROJECT_KEY, TestingRunIssues.TICKET_SOURCE,
            TestingRunIssues.TICKET_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        // Index for fetchUrlsByIssues - improves grouping and sorting by testSubCategory
        fieldNames = new String[]{TestingRunIssues.TEST_RUN_ISSUES_STATUS, "_id." + TestingIssuesId.TEST_SUB_CATEGORY};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

    private List<Bson> getPipelineForSeverityCount(Bson filter, boolean expandApiGroups, BasicDBObject groupedId) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN")));

        if(filter!=null){
            pipeline.add(Aggregates.match(filter));
        }

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        if (expandApiGroups) {
            UnwindOptions unwindOptions = new UnwindOptions();
            unwindOptions.preserveNullAndEmptyArrays(false);
            pipeline.add(Aggregates.unwind("$" + SingleTypeInfo._COLLECTION_IDS, unwindOptions));
            groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + SingleTypeInfo._COLLECTION_IDS)
                        .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        }

        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));
        return pipeline;
    }

    public Map<Integer,Map<String,Integer>> getSeveritiesMapForCollections(){
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        return getSeveritiesMapForCollections(null, true, groupedId);
    }

    public Map<Integer,Map<String,Integer>> getSeveritiesMapForCollections(Bson filter, boolean expandApiGroups, BasicDBObject groupedId){
        Map<Integer,Map<String,Integer>> resultMap = new HashMap<>() ;
        List<Bson> pipeline = getPipelineForSeverityCount(filter, expandApiGroups, groupedId);
        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                String severity = ((BasicDBObject) basicDBObject.get(Constants.ID)).getString(TestingRunIssues.KEY_SEVERITY);
                int apiCollectionId = ((BasicDBObject) basicDBObject.get(Constants.ID)).getInt(SingleTypeInfo._API_COLLECTION_ID);
                int count = basicDBObject.getInt("count");
                if(resultMap.containsKey(apiCollectionId)){
                    Map<String,Integer> severityMap = resultMap.get(apiCollectionId);
                    severityMap.put(severity, count);
                }else{
                    Map<String,Integer> severityMap = new HashMap<>();
                    severityMap.put(severity, count);
                    resultMap.put(apiCollectionId, severityMap);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultMap;
    }

    public Map<ApiInfoKey, Map<String, Integer>> getSeveritiesMapForApiInfoKeys(Bson filter, boolean expandApiGroups) {
        Map<ApiInfoKey, Map<String, Integer>> resultMap = new HashMap<>();
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                .append(SingleTypeInfo._URL, "$" + TestingRunIssues.ID_URL)
                .append(SingleTypeInfo._METHOD, "$" + TestingRunIssues.ID_METHOD)
                .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        List<Bson> pipeline = getPipelineForSeverityCount(filter, expandApiGroups, groupedId);
        if(pipeline.isEmpty()){
            return resultMap;
        }
        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                BasicDBObject id = (BasicDBObject) basicDBObject.get(Constants.ID);

                String severity = id.getString(TestingRunIssues.KEY_SEVERITY);
                int apiCollectionId = id.getInt(SingleTypeInfo._API_COLLECTION_ID);
                String url = id.getString(SingleTypeInfo._URL);
                String method = id.getString(SingleTypeInfo._METHOD);

                int count = basicDBObject.getInt("count");
                ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, Method.valueOf(method));
                if(resultMap.containsKey(apiInfoKey)){
                    Map<String,Integer> severityMap = resultMap.get(apiInfoKey);
                    severityMap.put(severity, count);
                }else{
                    Map<String,Integer> severityMap = new HashMap<>();
                    severityMap.put(severity, count);
                    resultMap.put(apiInfoKey, severityMap);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultMap;

    }
  
    public Map<String, Integer> getTotalSubcategoriesCountMap(int startTimeStamp, int endTimeStamp, Set<Integer> deactivatedCollections){
        List<Bson> pipeline = new ArrayList<>();
        if(deactivatedCollections == null) deactivatedCollections = new HashSet<>();

        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                Filters.lte(TestingRunIssues.LAST_SEEN, endTimeStamp),
                Filters.gte(TestingRunIssues.LAST_SEEN, startTimeStamp),
                Filters.nin("_id.apiInfoKey.apiCollectionId", deactivatedCollections)
            )
        ));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        BasicDBObject groupedId = new BasicDBObject("subCategory", "$_id.testSubCategory");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        Map<String,Integer> result = new HashMap<>();
        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                String subCategory = ((BasicDBObject) basicDBObject.get("_id")).getString("subCategory");
                int count = basicDBObject.getInt("count");
                result.put(subCategory, count);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        return result;
    }

    public List<Bson> buildPipelineForCalculatingTrend(int startTimestamp, int endTimestamp){
        // this functions make a pipeline for calculating a list map to the epoch value in day.
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.gte(TestingRunIssues.LAST_SEEN, startTimestamp)));
        pipeline.add(Aggregates.match(Filters.lte(TestingRunIssues.LAST_SEEN, endTimestamp)));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        pipeline.add(Aggregates.project(Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$lastSeen", 86400}))));
        pipeline.add(Aggregates.project(Projections.computed("dayOfYear", new BasicDBObject("$floor", new Object[]{"$dayOfYearFloat"}))));

        BasicDBObject groupedId = new BasicDBObject("dayOfYear", "$dayOfYear").append("subCategory", "$_id.testSubCategory");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        BasicDBObject bd = new BasicDBObject("subCategory", "$_id.subCategory").append("count", "$count");
        pipeline.add(Aggregates.group("$_id.dayOfYear", Accumulators.addToSet("issuesTrend", bd)));

        return pipeline;
    }

    public Map<String,Integer> countIssuesMapForPrivilegeEscalations(int timestamp){
        Map<String,Integer> finalMap = new HashMap<>();

        List<String> highSeverityList = new ArrayList<>();
        highSeverityList.add(GlobalEnums.TestCategory.BFLA.getName());
        highSeverityList.add(GlobalEnums.TestCategory.BOLA.getName());
        highSeverityList.add(GlobalEnums.TestCategory.NO_AUTH.getName());

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(
            Aggregates.match(
                Filters.and(
                    Filters.eq(YamlTemplate.AUTHOR, "AKTO"),
                    Filters.in(YamlTemplate.INFO + ".name", highSeverityList)
                )
            )
        );
        pipeline.add(
            Aggregates.project(
                Projections.fields(Projections.include("_id"), Projections.computed("categoryName", "$info.category.name"))
            )
        );
        Map<String, List<String>> categoryMap = new HashMap<>();

        MongoCursor<BasicDBObject> cursor = YamlTemplateDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (cursor.hasNext()) {
            BasicDBObject bdObject = (BasicDBObject) cursor.next();
            String testId = bdObject.getString("_id");
            List<String> currentList = categoryMap.getOrDefault(testId, new ArrayList<>());
            currentList.add(bdObject.getString("categoryName"));
            categoryMap.put(testId, currentList);
        }


        for(Map.Entry<String, List<String>> it: categoryMap.entrySet()){
            if(!it.getValue().isEmpty()){
                int countOfIssues = (int) TestingRunIssuesDao.instance.count(
                    Filters.and(
                        Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN.name()),
                        Filters.gte(TestingRunIssues.CREATION_TIME, timestamp),
                        Filters.in("_id." + TestingIssuesId.TEST_SUB_CATEGORY, it.getValue())
                    )
                );
                if(countOfIssues > 0){
                    finalMap.put(it.getKey(), countOfIssues);
                }
            }
        }

        return finalMap;
    }


    public MongoCollection<Document> getRawCollection() {
        return clients[0].getDatabase(getDBName()).getCollection(getCollName(), Document.class);
    }

    private TestingRunIssuesDao() {}
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_ISSUES.getCollectionName();
    }

    @Override
    public Class<TestingRunIssues> getClassT() {
        return TestingRunIssues.class;
    }

    @Override
    public String getFilterKeyString(){
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiInfoKey_ApiCollectionId) + ApiInfoKey.API_COLLECTION_ID;
    }

    /**
     * Gets filter for issues based on dashboardContext from test runs.
     * Issues are linked via: latestTestingRunSummaryId -> TestingRunResultSummary.testingRunId -> TestingRun.dashboardContext
     * Returns Filters.in(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, matchingSummaryIds) or Filters.empty() if no matches
     */
    private Bson getDashboardContextFilterForIssues(CONTEXT_SOURCE contextSource) {
        // If contextSource is null, return empty filter (no dashboard context filtering)
        if (contextSource == null) {
            return Filters.empty();
        }
        
        // Find all test runs with exact matching dashboardContext
        List<ObjectId> matchingTestRunIds = TestingRunDao.instance.getMCollection().find(
            Filters.eq(TestingRun.DASHBOARD_CONTEXT, contextSource)
        ).projection(Projections.include(Constants.ID))
        .into(new ArrayList<>())
        .stream()
            .map(TestingRun::getId)
            .collect(Collectors.toList());
        
        if (matchingTestRunIds.isEmpty()) {
            return Filters.empty();
        }
        
        // Find all summaries for those test runs
        List<ObjectId> matchingSummaryIds = TestingRunResultSummariesDao.instance.getMCollection().find(
            Filters.in(TestingRunResultSummary.TESTING_RUN_ID, matchingTestRunIds)
        ).projection(Projections.include(Constants.ID))
        .into(new ArrayList<>())
        .stream()
            .map(TestingRunResultSummary::getId)
            .collect(Collectors.toList());
        
        if (matchingSummaryIds.isEmpty()) {
            return Filters.empty();
        }
        
        return Filters.in(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, matchingSummaryIds);
    }

    /**
     * Applies RBAC and dashboard context filtering for dashboard queries.
     * - Shows all issues including deleted/deactivated collections
     * - If contextSource is set: filters by dashboardContext from test runs
     * - Admin: can see all issues (filtered by dashboardContext if contextSource is set)
     * - Non-admin: only accessible collections (filtered by dashboardContext if contextSource is set)
     * 
     * Note: contextSource is set by UserDetailsFilter for HTTP requests (defaults to API if not provided).
     * It may be null in background jobs/async threads, but for dashboard requests it should always be set.
     */
    public Bson addCollectionsFilterForDashboard(Bson q) {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        Integer userId = Context.userId.get();
        Integer accountId = Context.accountId.get();
        
        // Handle test scenarios where userId/accountId might be null
        if (userId == null || accountId == null) {
            // In tests/background jobs: no filtering (show all)
            return q;
        }
        
        boolean isAdmin = RBACDao.getCurrentRoleForUser(userId, accountId) == Role.ADMIN;
        
        // Admin viewing user-based dashboard (no contextSource): show all
        if (isAdmin && contextSource == null) {
            return q;
        }
      
        List<Integer> apiCollectionIds = UsersCollectionsList.getCollectionsIdForUser(userId, accountId); 

        // RBAC disabled or admin with empty list
        if (apiCollectionIds == null || (apiCollectionIds.isEmpty() && isAdmin)) {
            // No contextSource: show all
            if (contextSource == null) {
                return q;
            }
            // Filter by dashboardContext only (getCollectionsIdForUser already filtered by context)
            Bson dashboardContextFilter = getDashboardContextFilterForIssues(contextSource);
            return Filters.and(q, dashboardContextFilter);
        }
        
        // Non-admin with empty list: no access
        if (apiCollectionIds.isEmpty()) {
            return Filters.and(q, Filters.empty());
        }
        
        // Build filter for accessible collections (already filtered by context via getCollectionsIdForUser)
        // Only check _id.apiInfoKey.apiCollectionId (primary field) - collectionIds is derived from it
        Bson accessibleCollectionsFilter = Filters.in(getFilterKeyString(), apiCollectionIds);
        
        // Filter by dashboardContext if contextSource is set (for deleted collections)
        if (contextSource != null && isAdmin) {
            // Admin: accessible collections OR issues with matching dashboardContext
            Bson dashboardContextFilter = getDashboardContextFilterForIssues(contextSource);
            if (dashboardContextFilter.equals(Filters.empty())) {
                return Filters.and(q, accessibleCollectionsFilter);
            }
            return Filters.and(q, Filters.or(accessibleCollectionsFilter, dashboardContextFilter));
        }
        
        // No contextSource: return accessible collections only
        return Filters.and(q, accessibleCollectionsFilter);
    }
}
