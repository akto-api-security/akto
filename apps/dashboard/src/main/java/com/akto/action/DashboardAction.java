package com.akto.action;

import java.util.*;
import java.util.regex.Pattern;


import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.RuntimeListener;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.ConnectionInfo;
import com.akto.util.IssueTrendType;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.opensymphony.xwork2.Action;

import static com.akto.dto.test_run_findings.TestingRunIssues.KEY_SEVERITY;

public class DashboardAction extends UserAction {

    private int startTimeStamp;
    private int endTimeStamp;
    private Map<Integer,List<IssueTrendType>> issuesTrendMap = new HashMap<>() ;
    private int skip;
    private List<Activity> recentActivities = new ArrayList<>();
    private int totalActivities;
    private Map<String,ConnectionInfo> integratedConnectionsInfo = new HashMap<>();
    private String connectionSkipped;

    private static final LoggerMaker loggerMaker = new LoggerMaker(DashboardAction.class, LogDb.DASHBOARD);

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    private long totalIssuesCount = 0;
    private long oldOpenCount = 0;
    public String findTotalIssues() {
        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(deactivatedCollections);
        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);

        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshopCollection != null) demoCollections.add(juiceshopCollection.getId());


        if (startTimeStamp == 0) startTimeStamp = Context.now() - 24 * 1 * 60 * 60;
        // totoal issues count = issues that were created before endtimestamp and are either still open or fixed but last updated is after endTimestamp
        totalIssuesCount = TestingRunIssuesDao.instance.count(
            Filters.and(
                Filters.lte(TestingRunIssues.CREATION_TIME, endTimeStamp),
                Filters.nin("_id.apiInfoKey.apiCollectionId", demoCollections),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS,  GlobalEnums.TestRunIssueStatus.OPEN))       
            );

        // issues that have been created till start timestamp
        oldOpenCount = TestingRunIssuesDao.instance.count(
                Filters.and(
                        Filters.nin("_id.apiInfoKey.apiCollectionId", demoCollections),
                        Filters.lte(TestingRunIssues.CREATION_TIME, startTimeStamp),
                        Filters.ne(TestingRunIssues.TEST_RUN_ISSUES_STATUS,  GlobalEnums.TestRunIssueStatus.IGNORED)
                )
        );

        return SUCCESS.toUpperCase();
    }


    private List<HistoricalData> finalHistoricalData = new ArrayList<>();
    private List<HistoricalData> initialHistoricalData = new ArrayList<>();
    public String fetchHistoricalData() {
        if (endTimeStamp != 0) {
            this.finalHistoricalData = HistoricalDataDao.instance.findAll(
                    Filters.and(
                            Filters.gte(HistoricalData.TIME, endTimeStamp),
                            Filters.lte(HistoricalData.TIME, endTimeStamp + 24 * 60 * 60)
                    )
            );
        }

        this.initialHistoricalData = HistoricalDataDao.instance.findAll(
                Filters.and(
                        Filters.gte(HistoricalData.TIME, startTimeStamp),
                        Filters.lte(HistoricalData.TIME, startTimeStamp + 24 * 60 * 60)
                )
        );

        return SUCCESS.toUpperCase();
    }

    private List<String> severityToFetch;
    private final Map<String, Map<Integer, Integer>> severityWiseTrendData= new HashMap<>();
    private final Map<Integer, Integer> trendData = new HashMap<>();
    public String fetchCriticalIssuesTrend(){
        if(endTimeStamp == 0) endTimeStamp = Context.now();
        if (severityToFetch == null || severityToFetch.isEmpty()) severityToFetch = Arrays.asList("CRITICAL", "HIGH");

        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(deactivatedCollections);
        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);

        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshopCollection != null) demoCollections.add(juiceshopCollection.getId());
        
        List<GlobalEnums.TestRunIssueStatus> allowedStatus = Arrays.asList(GlobalEnums.TestRunIssueStatus.OPEN, GlobalEnums.TestRunIssueStatus.FIXED);
        Bson issuesFilter = Filters.and(
                Filters.in(KEY_SEVERITY, severityToFetch),
                Filters.gte(TestingRunIssues.CREATION_TIME, startTimeStamp),
                Filters.lte(TestingRunIssues.CREATION_TIME, endTimeStamp),
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, allowedStatus),
                Filters.nin("_id.apiInfoKey.apiCollectionId", demoCollections)
        );

        String dayOfYearFloat = "dayOfYearFloat";
        String dayOfYear = "dayOfYear";

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(issuesFilter));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        pipeline.add(Aggregates.project(
                Projections.fields(
                        Projections.computed(dayOfYearFloat, new BasicDBObject("$divide", new Object[]{"$" + TestingRunIssues.CREATION_TIME, 86400})),
                        Projections.include(KEY_SEVERITY)
                )));

        pipeline.add(Aggregates.project(
                Projections.fields(
                        Projections.computed(dayOfYear, new BasicDBObject("$floor", new Object[]{"$" + dayOfYearFloat})),
                        Projections.include(KEY_SEVERITY)
                )));

        BasicDBObject groupedId = new BasicDBObject(dayOfYear, "$"+dayOfYear)
                                                    .append("url", "$_id.apiInfoKey.url")
                                                    .append("method", "$_id.apiInfoKey.method")
                                                    .append("apiCollectionId", "$_id.apiInfoKey.apiCollectionId")
                                                    .append(KEY_SEVERITY, "$" + KEY_SEVERITY);
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        MongoCursor<BasicDBObject> issuesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        while(issuesCursor.hasNext()){
            BasicDBObject basicDBObject = issuesCursor.next();
            BasicDBObject o = (BasicDBObject) basicDBObject.get("_id");
            String severity = o.getString(KEY_SEVERITY, GlobalEnums.Severity.LOW.name());
            Map<Integer, Integer> trendData = severityWiseTrendData.computeIfAbsent(severity, k -> new HashMap<>());
            int date = o.getInt(dayOfYear);
            int count = trendData.getOrDefault(date,0);
            trendData.put(date, count+1);
            count = this.trendData.getOrDefault(date,0);
            this.trendData.put(date, count+1);
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchIssuesTrend(){
        if(endTimeStamp == 0){
            endTimeStamp = Context.now() ;
        }

        Map<Integer,List<IssueTrendType>> trendMap = new HashMap<>();

        List<Bson> pipeline = TestingRunIssuesDao.instance.buildPipelineForCalculatingTrend(startTimeStamp, endTimeStamp);
        MongoCursor<BasicDBObject> issuesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        
        while(issuesCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = issuesCursor.next();
                int dayEpoch = basicDBObject.getInt("_id");
                BasicDBList categoryList = ((BasicDBList) basicDBObject.get("issuesTrend"));
                List<IssueTrendType> trendList = new ArrayList<>();
                for(Object obj: categoryList){
                    BasicDBObject dbObject = (BasicDBObject) obj;
                    IssueTrendType trendObj = new IssueTrendType(dbObject.getInt("count"), dbObject.getString("subCategory"));
                    trendList.add(trendObj);
                }

                trendMap.put(dayEpoch, trendList);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in getting issues trend " + e.toString(), LogDb.DASHBOARD);
            }
        }
        this.issuesTrendMap = trendMap;
        
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRecentActivities(){
        List<Activity> activities = ActivitiesDao.instance.fetchRecentActivitiesFeed((skip * 5), 5);
        this.recentActivities = activities;
        this.totalActivities = (int) ActivitiesDao.instance.getMCollection().countDocuments();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchIntegratedConnections(){
        Map<String,ConnectionInfo> infoMap = AccountSettingsDao.instance.getIntegratedConnectionsInfo();
        Map<String,ConnectionInfo> finalMap = new HashMap<>();
        finalMap.put(ConnectionInfo.AUTOMATED_TRAFFIC,infoMap.getOrDefault(ConnectionInfo.AUTOMATED_TRAFFIC, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.GITHUB_SSO,infoMap.getOrDefault(ConnectionInfo.GITHUB_SSO, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.SLACK_ALERTS,infoMap.getOrDefault(ConnectionInfo.SLACK_ALERTS, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.CI_CD_INTEGRATIONS,infoMap.getOrDefault(ConnectionInfo.CI_CD_INTEGRATIONS, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.INVITE_MEMBERS,infoMap.getOrDefault(ConnectionInfo.INVITE_MEMBERS, new ConnectionInfo(0, false)));

        this.integratedConnectionsInfo = finalMap;

        return Action.SUCCESS.toUpperCase();
    }

    public String markConnectionAsSkipped(){
        if(connectionSkipped != null){
            AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.CONNECTION_INTEGRATIONS_INFO + "." + connectionSkipped + "." + "lastSkipped", Context.now()));
            return Action.SUCCESS.toUpperCase();
        }else{
            return Action.ERROR.toUpperCase();
        }
    }

    private String username;
    private String organization;
    private final Pattern usernamePattern = Pattern.compile("^[\\w\\s-]{1,}$");
    private final Pattern organizationPattern = Pattern.compile("^[\\w\\s.&-]{1,}$");
    public String updateUsernameAndOrganization() {
        if(username == null || username.trim().isEmpty()) {
            addActionError("Username cannot be empty");
            return Action.ERROR.toUpperCase();
        }
        this.setUsername(username.trim());

        if(!usernamePattern.matcher(username).matches()) {
            addActionError("Username is not valid");
            return Action.ERROR.toUpperCase();
        }

        if(username.length() > 24) {
            addActionError("Username can't be longer than 24 characters");
            return Action.ERROR.toUpperCase();
        }

        User userFromSession = getSUser();
        if (userFromSession == null) {
            addActionError("Invalid user");
            return Action.ERROR.toUpperCase();
        }

        String email = userFromSession.getLogin();

        User user = UsersDao.instance.updateOneNoUpsert(Filters.in(User.LOGIN, email), Updates.combine(
                Updates.set(User.NAME, username),
                Updates.set(User.NAME_LAST_UPDATE, Context.now())
        ));
        RBAC.Role currentRoleForUser = RBACDao.getCurrentRoleForUser(user.getId(), Context.accountId.get());

        if(currentRoleForUser != null && currentRoleForUser.getName().equals(RBAC.Role.ADMIN.getName())) {
            if(organization == null || organization.trim().isEmpty()) {
                addActionError("Organization cannot be empty");
                return Action.ERROR.toUpperCase();
            }

            setOrganization(organization.trim());

            if(!organizationPattern.matcher(organization).matches()) {
                addActionError("Organization is not valid");
                return Action.ERROR.toUpperCase();
            }

            if(organization.length() > 24) {
                addActionError("Organization name can't be longer than 24 characters");
                return Action.ERROR.toUpperCase();
            }

            OrganizationsDao.instance.updateOneNoUpsert(Filters.in(Organization.ACCOUNTS, Context.accountId.get()), Updates.combine(
                    Updates.set(Organization.NAME, organization),
                    Updates.set(Organization.NAME_LAST_UPDATE, Context.now())
            ));
        }

        return Action.SUCCESS.toUpperCase();
    }

    public int getStartTimeStamp() {
        return startTimeStamp;
    }

    public void setStartTimeStamp(int startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
    }

    public int getEndTimeStamp() {
        return endTimeStamp;
    }

    public void setEndTimeStamp(int endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
    }

    public Map<Integer, List<IssueTrendType>> getIssuesTrendMap() {
        return issuesTrendMap;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public List<Activity> getRecentActivities() {
        return recentActivities;
    }

    public int getTotalActivities() {
        return totalActivities;
    }

    public void setTotalActivities(int totalActivities) {
        this.totalActivities = totalActivities;
    }

    public Map<String, ConnectionInfo> getIntegratedConnectionsInfo() {
        return integratedConnectionsInfo;
    }

    public void setIntegratedConnectionsInfo(Map<String, ConnectionInfo> integratedConnectionsInfo) {
        this.integratedConnectionsInfo = integratedConnectionsInfo;
    }

    public String getConnectionSkipped() {
        return connectionSkipped;
    }

    public void setConnectionSkipped(String connectionSkipped) {
        this.connectionSkipped = connectionSkipped;
    }

    public void setSeverityToFetch(List<String> severityToFetch) {
        this.severityToFetch = severityToFetch;
    }

    public Map<Integer, Integer> getTrendData() {
        return trendData;
    }

    public long getTotalIssuesCount() {
        return totalIssuesCount;
    }

    public long getOldOpenCount() {
        return oldOpenCount;
    }

    public List<HistoricalData> getFinalHistoricalData() {
        return finalHistoricalData;
    }

    public List<HistoricalData> getInitialHistoricalData() {
        return initialHistoricalData;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public Map<String, Map<Integer, Integer>> getSeverityWiseTrendData() {
        return severityWiseTrendData;
    }
}
