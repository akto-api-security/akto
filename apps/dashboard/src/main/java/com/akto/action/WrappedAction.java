package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import java.util.*;

import org.bson.conversions.Bson;

public class WrappedAction extends UserAction {

    private Map<String, Object> data = new HashMap<>();

    public String execute() {
        int accountId = Context.accountId.get();
        int startOf2025 = 1735689600; // 1 Jan 2025

        Set<Integer> demoCollections = UsageMetricCalculator.getDemos();
        demoCollections.add(0);
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.and(
                        Filters.gte(ApiCollection.START_TS, startOf2025),
                        Filters.eq(ApiCollection.AUTOMATED, false),
                        Filters.ne("type", ApiCollection.Type.API_GROUP.name()),
                        Filters.nin(Constants.ID, demoCollections)
                )
        );

        Set<Integer> totalCollections = new HashSet<>();
        for (ApiCollection collection : collections) {
            totalCollections.add(collection.getId());
        }

        int newCollectionsCount = collections.size();

        long totalTests = TestingRunDao.instance.count(
                Filters.and(
                        Filters.in("testingEndpoints.apiCollectionId", totalCollections),
                        Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startOf2025)
                )
        );


        long criticalissues = VulnerableTestingRunResultDao.instance.count(
                Filters.and(
                        Filters.in("apiInfoKey.apiCollectionId", totalCollections),
                        Filters.gte(TestingRunResult.END_TIMESTAMP, startOf2025)
                )
        );

        List<User> users = UsersDao.instance.findAll(Filters.exists("accounts." + accountId));
        Organization org = OrganizationsDao.instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
        String orgDomain = null;
        if (org != null) {
            orgDomain = org.getAdminEmail().substring(org.getAdminEmail().indexOf("@") + 1).toLowerCase();
        }

        int teamSize = 0;
        if (orgDomain != null) {
            for (User user : users) {
                if (user == null) {
                    continue;
                }
                String email = user.getLogin();
                String userDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                if (orgDomain.equals(userDomain)) {
                    teamSize++;
                }
            }
        } else {
            teamSize = users.size();
        }

        if (totalCollections.isEmpty()) {
            data.put("newCollections", newCollectionsCount);
            data.put("totalTests", totalTests);
            data.put("criticalIssues", criticalissues);
            data.put("teamSize", teamSize);
            data.put("year", 2025);
            data.put("totalEndpoints", 0L);
            data.put("testCoverage", 0.0);
            data.put("testedEndpoints", 0L);
            data.put("topVulnType", "None");
            data.put("topVulnCount", 0);
            data.put("fixedIssues", 0L);
            data.put("fixedPercent", 0.0);
            data.put("primaryApiType", "REST");
            data.put("primaryApiTypeCount", 0);
            return SUCCESS.toUpperCase();
        }

        long totalEndpoints = ApiInfoDao.instance.count(
                Filters.and(
                        Filters.in(ApiInfo.ID_API_COLLECTION_ID, totalCollections),
                        Filters.or(
                                Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startOf2025),
                                Filters.gte(ApiInfo.LAST_SEEN, startOf2025)
                        )
                ));

        long testedEndpoints = ApiInfoDao.instance.count(
                Filters.and(
                        Filters.in(ApiInfo.ID_API_COLLECTION_ID, totalCollections),
                        Filters.gt(ApiInfo.LAST_TESTED, 0),
                        Filters.or(
                                Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startOf2025),
                                Filters.and(
                                        Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                        Filters.gte(ApiInfo.LAST_SEEN, startOf2025)
                                )
                        )
                ));

        double testCoveragePercent = totalEndpoints > 0
                ? Math.round((testedEndpoints * 100.0 / totalEndpoints) * 10) / 10.0
                : 0.0;

        List<Bson> vulnPipeline = new ArrayList<>();
        vulnPipeline.add(Aggregates.match(
                Filters.and(
                        Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, totalCollections),
                        Filters.gte("lastSeen", startOf2025),
                        Filters.eq("testRunIssueStatus", GlobalEnums.TestRunIssueStatus.OPEN.name())
                )));
        BasicDBObject groupedId = new BasicDBObject("subCategory", "$_id.testSubCategory");
        vulnPipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));
        vulnPipeline.add(Aggregates.sort(Sorts.descending("count")));
        vulnPipeline.add(Aggregates.limit(1));

        MongoCursor<BasicDBObject> vulnCursor = TestingRunIssuesDao.instance
                .getMCollection()
                .aggregate(vulnPipeline, BasicDBObject.class)
                .cursor();

        String topVulnType = "None";
        int topVulnCount = 0;
        if (vulnCursor.hasNext()) {
            BasicDBObject result = vulnCursor.next();
            BasicDBObject id = (BasicDBObject) result.get("_id");
            topVulnType = id.getString("subCategory");
            topVulnCount = result.getInt("count");
        }
        vulnCursor.close();

        long fixedIssues = TestingRunIssuesDao.instance.count(
                Filters.and(
                        Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, totalCollections),
                        Filters.gte("creationTime", startOf2025),
                        Filters.eq("testRunIssueStatus", GlobalEnums.TestRunIssueStatus.FIXED.name())
                ));

        long totalIssuesFound = TestingRunIssuesDao.instance.count(
                Filters.and(
                        Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, totalCollections),
                        Filters.gte("creationTime", startOf2025)
                ));

        double fixedPercent = totalIssuesFound > 0
                ? Math.round((fixedIssues * 100.0 / totalIssuesFound) * 10) / 10.0
                : 0.0;

        List<Bson> apiTypePipeline = new ArrayList<>();
        apiTypePipeline.add(Aggregates.match(
                Filters.and(
                        Filters.in(ApiInfo.ID_API_COLLECTION_ID, totalCollections),
                        Filters.or(
                                Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startOf2025),
                                Filters.and(
                                        Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                        Filters.gte(ApiInfo.LAST_SEEN, startOf2025)
                                )
                        )
                )));
        apiTypePipeline.add(Aggregates.group("$"+ApiInfo.API_TYPE, Accumulators.sum("count", 1)));
        apiTypePipeline.add(Aggregates.sort(Sorts.descending("count")));
        apiTypePipeline.add(Aggregates.limit(1));

        MongoCursor<BasicDBObject> apiTypeCursor = ApiInfoDao.instance
                .getMCollection()
                .aggregate(apiTypePipeline, BasicDBObject.class)
                .cursor();

        String primaryApiType = "REST";
        int primaryApiTypeCount = 0;
        if (apiTypeCursor.hasNext()) {
            BasicDBObject result = apiTypeCursor.next();
            String type = result.getString("_id");
            primaryApiType = type != null ? type : "REST";
            primaryApiTypeCount = result.getInt("count");
        }
        apiTypeCursor.close();

        data.put("newCollections", newCollectionsCount);
        data.put("totalTests", totalTests);
        data.put("criticalIssues", criticalissues);
        data.put("teamSize", teamSize);
        data.put("year", 2025);
        data.put("totalEndpoints", totalEndpoints);
        data.put("testCoverage", testCoveragePercent);
        data.put("testedEndpoints", testedEndpoints);
        data.put("topVulnType", topVulnType);
        data.put("topVulnCount", topVulnCount);
        data.put("fixedIssues", fixedIssues);
        data.put("fixedPercent", fixedPercent);
        data.put("primaryApiType", primaryApiType);
        data.put("primaryApiTypeCount", primaryApiTypeCount);

        return SUCCESS.toUpperCase();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
