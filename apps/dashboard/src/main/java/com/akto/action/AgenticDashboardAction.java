package com.akto.action;

import com.akto.ProtoMessageUtils;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

public class AgenticDashboardAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgenticDashboardAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final int MAX_THREAT_FETCH_LIMIT = 100000; // Large limit to fetch all threats

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    private final Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    // ========== Consolidated API Methods ==========

    /**
     * Consolidated API: Fetch all endpoint discovery related data
     * Collection: ApiInfoDao, SingleTypeInfoDao
     */
    public String fetchEndpointDiscoveryData() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            long daysBetween = (endTimestamp - startTimestamp) / Constants.ONE_DAY_TIMESTAMP;
            Set<Integer> demoCollections = getDemoCollections();

            // Fetch API Endpoints Discovered over time
            List<BasicDBObject> endpointsData = fetchEndpointsOverTime(startTimestamp, endTimestamp, daysBetween, demoCollections);
            
            // Fetch API Discovery Stats (Shadow, Sensitive, No-Auth, Normal)
            BasicDBObject discoveryStats = fetchApiDiscoveryStatsInternal();

            response.put("endpointsDiscovered", endpointsData);
            response.put("discoveryStats", discoveryStats);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching endpoint discovery data: " + e.getMessage());
            addActionError("Error fetching endpoint discovery data: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Consolidated API: Fetch all issues related data
     * Collection: TestingRunIssuesDao
     */
    public String fetchIssuesData() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            long daysBetween = (endTimestamp - startTimestamp) / Constants.ONE_DAY_TIMESTAMP;
            Set<Integer> demoCollections = getDemoCollections();

            // Fetch all issues once with maximum range needed (0 to max(currentTime, endTimestamp))
            // This covers both time-range queries and all-time queries
            int currentTime = Context.now();
            int maxEndTimestamp = Math.max(currentTime, endTimestamp);
            List<TestingRunIssues> allIssues = fetchAllTestingRunIssues(0, maxEndTimestamp, demoCollections);

            // Fetch issues over time
            List<BasicDBObject> issuesData = fetchIssuesOverTime(startTimestamp, endTimestamp, daysBetween, allIssues);
            
            // Fetch issues by severity
            BasicDBObject issuesBySeverity = fetchIssuesBySeverityInternal(allIssues);
            
            // Fetch average issue age
            BasicDBObject averageIssueAge = fetchAverageIssueAgeInternal(allIssues);
            
            // Fetch open and resolved issues
            BasicDBObject openResolvedIssues = fetchOpenResolvedIssuesInternal(startTimestamp, endTimestamp, daysBetween, allIssues);
            
            // Fetch top issues by category
            List<BasicDBObject> topIssuesByCategory = fetchTopIssuesByCategoryInternal(allIssues);
            
            // Fetch top hostnames by issues
            List<BasicDBObject> topHostnamesByIssues = fetchTopHostnamesByIssuesInternal(allIssues);

            response.put("issuesOverTime", issuesData);
            response.put("issuesBySeverity", issuesBySeverity);
            response.put("averageIssueAge", averageIssueAge);
            response.put("openResolvedIssues", openResolvedIssues);
            response.put("topIssuesByCategory", topIssuesByCategory);
            response.put("topHostnamesByIssues", topHostnamesByIssues);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching issues data: " + e.getMessage());
            addActionError("Error fetching issues data: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Consolidated API: Fetch all testing related data
     * Collection: ApiInfoDao
     */
    public String fetchTestingData() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            long daysBetween = (endTimestamp - startTimestamp) / Constants.ONE_DAY_TIMESTAMP;

            // Fetch tested vs non-tested APIs
            BasicDBObject testedVsNonTested = fetchTestedVsNonTestedApisInternal(startTimestamp, endTimestamp, daysBetween);

            response.put("testedVsNonTested", testedVsNonTested);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching testing data: " + e.getMessage());
            addActionError("Error fetching testing data: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Consolidated API: Fetch all threat related data
     * Collection: MaliciousEventDao
     */
    public String fetchThreatData() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            long daysBetween = (endTimestamp - startTimestamp) / Constants.ONE_DAY_TIMESTAMP;
            Set<Integer> demoCollections = getDemoCollections();

            // Fetch all malicious events once with maximum range needed
            int currentTime = Context.now();
            int maxEndTimestamp = Math.max(currentTime, endTimestamp);
            List<DashboardMaliciousEvent> allThreats = fetchAllMaliciousEvents(0, maxEndTimestamp, demoCollections);

            // Fetch threats over time
            List<BasicDBObject> threatsOverTime = fetchThreatsOverTimeInternal(startTimestamp, endTimestamp, daysBetween, allThreats);
            
            // Fetch threats by severity
            BasicDBObject threatsBySeverity = fetchThreatsBySeverityInternal(allThreats);
            
            // Fetch top threats by category
            List<BasicDBObject> topThreatsByCategory = fetchTopThreatsByCategoryInternal(allThreats);
            
            // Fetch top attack hosts
            List<BasicDBObject> topAttackHosts = fetchTopAttackHostsInternal(allThreats);
            
            // Fetch top bad actors
            List<BasicDBObject> topBadActors = fetchTopBadActorsInternal(allThreats);

            response.put("threatsOverTime", threatsOverTime);
            response.put("threatsBySeverity", threatsBySeverity);
            response.put("topThreatsByCategory", topThreatsByCategory);
            response.put("topAttackHosts", topAttackHosts);
            response.put("topBadActors", topBadActors);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching threat data: " + e.getMessage());
            addActionError("Error fetching threat data: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }


    private List<BasicDBObject> fetchEndpointsOverTime(int startTimestamp, int endTimestamp, long daysBetween, Set<Integer> demoCollections) {
        List<BasicDBObject> result = new ArrayList<>();
        
        try {
            // Fetch all SingleTypeInfo records once
            List<SingleTypeInfo> allRecords = fetchAllSingleTypeInfo(startTimestamp, endTimestamp, demoCollections);
            
            // Group by unique endpoint (apiCollectionId, url, method) and get min timestamp
            Map<String, Integer> endpointFirstSeen = new HashMap<>();
            for (SingleTypeInfo sti : allRecords) {
                String endpointKey = sti.getApiCollectionId() + "|" + sti.getUrl() + "|" + sti.getMethod();
                int timestamp = sti.getTimestamp();
                
                if (!endpointFirstSeen.containsKey(endpointKey) || timestamp < endpointFirstSeen.get(endpointKey)) {
                    endpointFirstSeen.put(endpointKey, timestamp);
                }
            }
            
            // Filter endpoints that were first seen within the time range
            List<Integer> firstSeenTimestamps = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : endpointFirstSeen.entrySet()) {
                int firstSeen = entry.getValue();
                if (startTimestamp <= 0) {
                    // All time: only check upper bound
                    if (firstSeen <= endTimestamp) {
                        firstSeenTimestamps.add(firstSeen);
                    }
                } else {
                    // Time range: check both bounds
                    if (firstSeen >= startTimestamp && firstSeen <= endTimestamp) {
                        firstSeenTimestamps.add(firstSeen);
                    }
                }
            }
            
            // Group by time period in Java
            Map<String, Long> timePeriodMap = groupByTimePeriod(firstSeenTimestamps, daysBetween);
            
            // Convert to result format
            result = convertTimePeriodMapToResult(timePeriodMap, daysBetween);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching endpoints over time: " + e.getMessage() + ", stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
        }

        return result;
    }

    private List<BasicDBObject> fetchIssuesOverTime(int startTimestamp, int endTimestamp, long daysBetween, List<TestingRunIssues> allIssues) {
        List<BasicDBObject> result = new ArrayList<>();
        
        try {
            // Filter for OPEN issues within the time range
            List<Integer> creationTimestamps = allIssues.stream()
                .filter(issue -> issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN)
                .map(TestingRunIssues::getCreationTime)
                .filter(time -> time > 0 && time >= startTimestamp && time <= endTimestamp)
                .collect(Collectors.toList());
            
            // Group by time period in Java
            Map<String, Long> timePeriodMap = groupByTimePeriod(creationTimestamps, daysBetween);
            
            // Convert to result format
            result = convertTimePeriodMapToResult(timePeriodMap, daysBetween);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching issues over time: " + e.getMessage());
        }

        return result;
    }


    // ========== API Info Collection APIs ==========

    /**
     * Internal method: Fetch API discovery stats
     * Collection: ApiInfoDao
     */
    private BasicDBObject fetchApiDiscoveryStatsInternal() {
        BasicDBObject result = new BasicDBObject();
        try {
            Bson baseFilter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            
            // Apply RBAC filter
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (collectionIds != null) {
                    baseFilter = Filters.and(baseFilter, Filters.in("collectionIds", collectionIds));
                }
            } catch (Exception e) {
                // Ignore
            }

            // Shadow APIs: apiCollectionId == AKTO_DISCOVERED_APIS_COLLECTION_ID
            long shadowCount = ApiInfoDao.instance.count(Filters.and(
                baseFilter,
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, ApiInfoDao.AKTO_DISCOVERED_APIS_COLLECTION_ID)
            ));

            // Sensitive APIs: isSensitive == true
            long sensitiveCount = ApiInfoDao.instance.count(Filters.and(
                baseFilter,
                Filters.eq(ApiInfo.IS_SENSITIVE, true)
            ));

            // No-Auth APIs: actualAuthType contains UNAUTHENTICATED
            // Check all APIs and filter those with UNAUTHENTICATED auth type
            List<ApiInfo> allApis = ApiInfoDao.instance.findAll(baseFilter, Projections.include(ApiInfo.ALL_AUTH_TYPES_FOUND));
            long noAuthCount = allApis.stream()
                .filter(api -> {
                    List<ApiInfo.AuthType> authTypes = api.getActualAuthType();
                    return authTypes != null && authTypes.contains(ApiInfo.AuthType.UNAUTHENTICATED);
                })
                .count();

            // Total APIs
            long totalCount = ApiInfoDao.instance.count(baseFilter);
            
            // Normal APIs = Total - Shadow - Sensitive - NoAuth (with overlap handling)
            // Note: There may be overlap between categories, so normal is approximate
            long normalCount = Math.max(0, totalCount - shadowCount - sensitiveCount - noAuthCount);

            result.put("shadow", shadowCount);
            result.put("sensitive", sensitiveCount);
            result.put("noAuth", noAuthCount);
            result.put("normal", normalCount);
            result.put("total", totalCount);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching API discovery stats: " + e.getMessage());
            result.put("shadow", 0L);
            result.put("sensitive", 0L);
            result.put("noAuth", 0L);
            result.put("normal", 0L);
            result.put("total", 0L);
        }
        return result;
    }


    /**
     * Internal method: Fetch tested vs non-tested APIs over time
     * Collection: ApiInfoDao
     */
    private BasicDBObject fetchTestedVsNonTestedApisInternal(int startTimestamp, int endTimestamp, long daysBetween) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Fetch all ApiInfo records once
            List<ApiInfo> allApis = fetchAllApiInfo();
            
            // Separate tested and non-tested APIs
            List<Integer> testedTimestamps = new ArrayList<>();
            List<Integer> nonTestedTimestamps = new ArrayList<>();
            
            for (ApiInfo api : allApis) {
                Integer lastTested = api.getLastTested();
                Integer discoveredTimestamp = api.getDiscoveredTimestamp();
                Integer lastSeen = api.getLastSeen();
                
                // Use discoveredTimestamp if available, otherwise lastSeen
                Integer discoveredTs = discoveredTimestamp != null && discoveredTimestamp > 0 
                    ? discoveredTimestamp 
                    : (lastSeen != null && lastSeen > 0 ? lastSeen : null);
                
                if (lastTested != null && lastTested > 0 && 
                    lastTested >= startTimestamp && lastTested <= endTimestamp) {
                    // Tested API
                    testedTimestamps.add(lastTested);
                } else if ((lastTested == null || lastTested == 0) && discoveredTs != null &&
                    discoveredTs >= startTimestamp && discoveredTs <= endTimestamp) {
                    // Non-tested API
                    nonTestedTimestamps.add(discoveredTs);
                }
            }
            
            // Group by time period in Java
            Map<String, Long> testedTimePeriodMap = groupByTimePeriod(testedTimestamps, daysBetween);
            Map<String, Long> nonTestedTimePeriodMap = groupByTimePeriod(nonTestedTimestamps, daysBetween);
            
            // Convert to result format
            List<BasicDBObject> testedData = convertTimePeriodMapToResult(testedTimePeriodMap, daysBetween);
            List<BasicDBObject> nonTestedData = convertTimePeriodMapToResult(nonTestedTimePeriodMap, daysBetween);

            result.put("tested", testedData);
            result.put("nonTested", nonTestedData);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching tested vs non-tested APIs: " + e.getMessage());
            result.put("tested", new ArrayList<>());
            result.put("nonTested", new ArrayList<>());
        }
        return result;
    }


    // ========== TestingRunIssues Collection APIs ==========

    /**
     * Internal method: Fetch total issues by severity
     * Collection: TestingRunIssuesDao
     */
    private BasicDBObject fetchIssuesBySeverityInternal(List<TestingRunIssues> allIssues) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Filter for OPEN issues and group by severity in Java
            Map<String, Long> severityCounts = new HashMap<>();
            severityCounts.put("CRITICAL", 0L);
            severityCounts.put("HIGH", 0L);
            severityCounts.put("MEDIUM", 0L);
            severityCounts.put("LOW", 0L);
            
            for (TestingRunIssues issue : allIssues) {
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    GlobalEnums.Severity severity = issue.getSeverity();
                    if (severity != null) {
                        String severityStr = severity.name();
                        if (severityCounts.containsKey(severityStr)) {
                            severityCounts.put(severityStr, severityCounts.get(severityStr) + 1);
                        }
                    }
                }
            }

            result.put("critical", severityCounts.get("CRITICAL"));
            result.put("high", severityCounts.get("HIGH"));
            result.put("medium", severityCounts.get("MEDIUM"));
            result.put("low", severityCounts.get("LOW"));
            result.put("total", severityCounts.values().stream().mapToLong(Long::longValue).sum());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching issues by severity: " + e.getMessage());
            result.put("critical", 0L);
            result.put("high", 0L);
            result.put("medium", 0L);
            result.put("low", 0L);
            result.put("total", 0L);
        }
        return result;
    }


    /**
     * Internal method: Fetch average issue age by severity
     * Collection: TestingRunIssuesDao
     */
    private BasicDBObject fetchAverageIssueAgeInternal(List<TestingRunIssues> allIssues) {
        BasicDBObject result = new BasicDBObject();
        try {
            int currentTime = Context.now();
            
            // Filter for OPEN issues and calculate average age by severity in Java
            Map<String, List<Double>> ageBySeverity = new HashMap<>();
            ageBySeverity.put("CRITICAL", new ArrayList<>());
            ageBySeverity.put("HIGH", new ArrayList<>());
            ageBySeverity.put("MEDIUM", new ArrayList<>());
            ageBySeverity.put("LOW", new ArrayList<>());
            
            for (TestingRunIssues issue : allIssues) {
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    GlobalEnums.Severity severity = issue.getSeverity();
                    int creationTime = issue.getCreationTime();
                    if (severity != null && creationTime > 0) {
                        String severityStr = severity.name();
                        if (ageBySeverity.containsKey(severityStr)) {
                            double ageInDays = (currentTime - creationTime) / 86400.0;
                            ageBySeverity.get(severityStr).add(ageInDays);
                        }
                    }
                }
            }
            
            Map<String, Double> avgAgeBySeverity = new HashMap<>();
            for (Map.Entry<String, List<Double>> entry : ageBySeverity.entrySet()) {
                List<Double> ages = entry.getValue();
                if (ages.isEmpty()) {
                    avgAgeBySeverity.put(entry.getKey(), 0.0);
                } else {
                    double avg = ages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    avgAgeBySeverity.put(entry.getKey(), Math.round(avg * 10.0) / 10.0);
                }
            }

            result.put("critical", avgAgeBySeverity.getOrDefault("CRITICAL", 0.0));
            result.put("high", avgAgeBySeverity.getOrDefault("HIGH", 0.0));
            result.put("medium", avgAgeBySeverity.getOrDefault("MEDIUM", 0.0));
            result.put("low", avgAgeBySeverity.getOrDefault("LOW", 0.0));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching average issue age: " + e.getMessage());
            result.put("critical", 0.0);
            result.put("high", 0.0);
            result.put("medium", 0.0);
            result.put("low", 0.0);
        }
        return result;
    }


    /**
     * Internal method: Fetch open and resolved issues over time
     * Collection: TestingRunIssuesDao
     */
    private BasicDBObject fetchOpenResolvedIssuesInternal(int startTimestamp, int endTimestamp, long daysBetween, List<TestingRunIssues> allIssues) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Separate open and resolved issues within the time range
            List<Integer> openTimestamps = new ArrayList<>();
            List<Integer> resolvedTimestamps = new ArrayList<>();
            
            for (TestingRunIssues issue : allIssues) {
                int creationTime = issue.getCreationTime();
                if (creationTime <= 0 || creationTime < startTimestamp || creationTime > endTimestamp) {
                    continue;
                }
                
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    openTimestamps.add(creationTime);
                } else if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.FIXED) {
                    resolvedTimestamps.add(creationTime);
                }
            }
            
            // Group by time period in Java
            Map<String, Long> openTimePeriodMap = groupByTimePeriod(openTimestamps, daysBetween);
            Map<String, Long> resolvedTimePeriodMap = groupByTimePeriod(resolvedTimestamps, daysBetween);
            
            // Convert to result format
            List<BasicDBObject> openData = convertTimePeriodMapToResult(openTimePeriodMap, daysBetween);
            List<BasicDBObject> resolvedData = convertTimePeriodMapToResult(resolvedTimePeriodMap, daysBetween);

            result.put("open", openData);
            result.put("resolved", resolvedData);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching open/resolved issues: " + e.getMessage());
            result.put("open", new ArrayList<>());
            result.put("resolved", new ArrayList<>());
        }
        return result;
    }


    /**
     * Internal method: Fetch top 5 issues by category
     * Collection: TestingRunIssuesDao
     */
    private List<BasicDBObject> fetchTopIssuesByCategoryInternal(List<TestingRunIssues> allIssues) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Group by testSubCategory in Java
            Map<String, Long> categoryCounts = new HashMap<>();
            for (TestingRunIssues issue : allIssues) {
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    String category = issue.getId() != null && issue.getId().getTestSubCategory() != null 
                        ? issue.getId().getTestSubCategory() 
                        : "Unknown";
                    categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
                }
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedCategories) {
                BasicDBObject doc = new BasicDBObject();
                doc.put("_id", entry.getKey());
                doc.put("count", entry.getValue());
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top issues by category: " + e.getMessage());
        }
        return result;
    }


    /**
     * Internal method: Fetch top 5 hostnames with critical and high issues
     * Collection: TestingRunIssuesDao (joined with ApiCollection)
     */
    private List<BasicDBObject> fetchTopHostnamesByIssuesInternal(List<TestingRunIssues> allIssues) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Filter for OPEN issues with CRITICAL or HIGH severity and group by collectionId in Java
            Map<Integer, Long> collectionCounts = new HashMap<>();
            for (TestingRunIssues issue : allIssues) {
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    GlobalEnums.Severity severity = issue.getSeverity();
                    if (severity != null && (severity == GlobalEnums.Severity.CRITICAL || 
                        severity == GlobalEnums.Severity.HIGH)) {
                        int collectionId = issue.getId() != null && issue.getId().getApiInfoKey() != null
                            ? issue.getId().getApiInfoKey().getApiCollectionId()
                            : -1;
                        if (collectionId > 0) {
                            collectionCounts.put(collectionId, collectionCounts.getOrDefault(collectionId, 0L) + 1);
                        }
                    }
                }
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<Integer, Long>> sortedCollections = collectionCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<Integer, Long> entry : sortedCollections) {
                int collectionId = entry.getKey();
                long count = entry.getValue();
                
                ApiCollection collection = ApiCollectionsDao.instance.findOne(
                    Filters.eq(Constants.ID, collectionId),
                    Projections.include(ApiCollection.HOST_NAME, ApiCollection.NAME)
                );
                
                BasicDBObject hostnameResult = new BasicDBObject();
                hostnameResult.put("hostname", collection != null ? 
                    (collection.getHostName() != null ? collection.getHostName() : collection.getName()) : 
                    "Unknown");
                hostnameResult.put("count", count);
                result.add(hostnameResult);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top hostnames by issues: " + e.getMessage());
        }
        return result;
    }


    // ========== Threat Detection Collection APIs ==========

    /**
     * Internal method: Fetch threats over time
     * Collection: MaliciousEventDao
     */
    private List<BasicDBObject> fetchThreatsOverTimeInternal(int startTimestamp, int endTimestamp, long daysBetween, List<DashboardMaliciousEvent> allThreats) {
        List<BasicDBObject> result = new ArrayList<>();
        
        try {
            // Filter threats within the time range
            // Note: timestamp is long, but we convert to int for time grouping
            List<Integer> detectedTimestamps = allThreats.stream()
                .map(DashboardMaliciousEvent::getTimestamp)
                .filter(time -> time > 0 && time >= startTimestamp && time <= endTimestamp)
                .map(Long::intValue)
                .collect(Collectors.toList());
            
            // Group by time period in Java
            Map<String, Long> timePeriodMap = groupByTimePeriod(detectedTimestamps, daysBetween);
            
            // Convert to result format
            result = convertTimePeriodMapToResult(timePeriodMap, daysBetween);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching threats over time: " + e.getMessage());
        }

        return result;
    }

    /**
     * Internal method: Fetch total threats by severity
     * Collection: MaliciousEventDao
     */
    private BasicDBObject fetchThreatsBySeverityInternal(List<DashboardMaliciousEvent> allThreats) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Group by severity in Java
            Map<String, Long> severityCounts = new HashMap<>();
            severityCounts.put("CRITICAL", 0L);
            severityCounts.put("HIGH", 0L);
            severityCounts.put("MEDIUM", 0L);
            severityCounts.put("LOW", 0L);
            
            for (DashboardMaliciousEvent threat : allThreats) {
                String severity = threat.getSeverity();
                if (severity != null && severityCounts.containsKey(severity)) {
                    severityCounts.put(severity, severityCounts.get(severity) + 1);
                }
            }

            result.put("critical", severityCounts.get("CRITICAL"));
            result.put("high", severityCounts.get("HIGH"));
            result.put("medium", severityCounts.get("MEDIUM"));
            result.put("low", severityCounts.get("LOW"));
            result.put("total", severityCounts.values().stream().mapToLong(Long::longValue).sum());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching threats by severity: " + e.getMessage());
            result.put("critical", 0L);
            result.put("high", 0L);
            result.put("medium", 0L);
            result.put("low", 0L);
            result.put("total", 0L);
        }
        return result;
    }

    /**
     * Internal method: Fetch top 5 threats by category
     * Collection: MaliciousEventDao
     */
    private List<BasicDBObject> fetchTopThreatsByCategoryInternal(List<DashboardMaliciousEvent> allThreats) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Group by filterId (category) in Java
            Map<String, Long> categoryCounts = new HashMap<>();
            for (DashboardMaliciousEvent threat : allThreats) {
                String category = threat.getFilterId() != null ? threat.getFilterId() : "Unknown";
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedCategories) {
                BasicDBObject doc = new BasicDBObject();
                doc.put("_id", entry.getKey());
                doc.put("count", entry.getValue());
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top threats by category: " + e.getMessage());
        }
        return result;
    }

    /**
     * Internal method: Fetch top 5 attacked hosts by threats
     * Collection: MaliciousEventDao
     */
    private List<BasicDBObject> fetchTopAttackHostsInternal(List<DashboardMaliciousEvent> allThreats) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Group by host in Java
            Map<String, Long> hostCounts = new HashMap<>();
            for (DashboardMaliciousEvent threat : allThreats) {
                String host = threat.getHost();
                if (host != null && !host.isEmpty()) {
                    hostCounts.put(host, hostCounts.getOrDefault(host, 0L) + 1);
                } else {
                    // Fallback to url if host is not available
                    String endpoint = threat.getUrl();
                    if (endpoint != null && !endpoint.isEmpty()) {
                        // Extract hostname from URL if possible
                        try {
                            java.net.URL url = new java.net.URL(endpoint);
                            String hostname = url.getHost();
                            if (hostname != null && !hostname.isEmpty()) {
                                hostCounts.put(hostname, hostCounts.getOrDefault(hostname, 0L) + 1);
                            }
                        } catch (Exception e) {
                            // If URL parsing fails, use endpoint as-is
                            hostCounts.put(endpoint, hostCounts.getOrDefault(endpoint, 0L) + 1);
                        }
                    }
                }
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedHosts = hostCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedHosts) {
                BasicDBObject doc = new BasicDBObject();
                doc.put("hostname", entry.getKey());
                doc.put("count", entry.getValue());
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top attack hosts: " + e.getMessage());
        }
        return result;
    }

    /**
     * Internal method: Fetch top 5 bad actors by threats
     * Collection: MaliciousEventDao
     */
    private List<BasicDBObject> fetchTopBadActorsInternal(List<DashboardMaliciousEvent> allThreats) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Group by actor in Java
            Map<String, Long> actorCounts = new HashMap<>();
            for (DashboardMaliciousEvent threat : allThreats) {
                String actor = threat.getActor();
                if (actor != null && !actor.isEmpty()) {
                    actorCounts.put(actor, actorCounts.getOrDefault(actor, 0L) + 1);
                }
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedActors = actorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedActors) {
                BasicDBObject doc = new BasicDBObject();
                doc.put("actor", entry.getKey());
                doc.put("count", entry.getValue());
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top bad actors: " + e.getMessage());
        }
        return result;
    }


    // ========== Helper Methods ==========

    private Set<Integer> getDemoCollections() {
        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(deactivatedCollections);
        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);

        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshopCollection != null) {
            demoCollections.add(juiceshopCollection.getId());
        }
        return demoCollections;
    }

    // ========== Data Fetching Helper Methods ==========

    /**
     * Fetch all SingleTypeInfo records for API endpoints once with filters
     * Only fetches host header records which represent actual API endpoints
     */
    private List<SingleTypeInfo> fetchAllSingleTypeInfo(int startTimestamp, int endTimestamp, Set<Integer> demoCollections) {
        List<SingleTypeInfo> result = new ArrayList<>();
        try {
            // Get collections to exclude (non-traffic + demo collections)
            List<Integer> excludeCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
            excludeCollectionIds.addAll(demoCollections);

            // Host header filter - these represent actual API endpoints
            Bson hostHeaderFilter = SingleTypeInfoDao.filterForHostHeader(0, false);
            
            // Timestamp filter
            Bson timestampFilter = startTimestamp > 0
                ? Filters.and(
                    Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                    Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp)
                  )
                : Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp);

            // Build base filter
            List<Bson> filterList = new ArrayList<>();
            filterList.add(hostHeaderFilter);
            filterList.add(timestampFilter);
            
            // Exclude non-traffic and demo collections
            if (!excludeCollectionIds.isEmpty()) {
                filterList.add(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludeCollectionIds));
            }

            // Apply RBAC filter
            try {
                List<Integer> userCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (userCollectionIds != null && !userCollectionIds.isEmpty()) {
                    filterList.add(Filters.in("collectionIds", userCollectionIds));
                }
            } catch (Exception e) {
                // Ignore RBAC errors
            }

            // Combine all filters and fetch in single query
            Bson finalFilter = Filters.and(filterList);
            
            // Only project fields we need: apiCollectionId, url, method, timestamp
            Bson projection = Projections.include(
                "apiCollectionId",
                "url",
                "method",
                "timestamp"
            );

            result = SingleTypeInfoDao.instance.findAll(finalFilter, projection);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching SingleTypeInfo records: " + e.getMessage());
        }
        return result;
    }

    /**
     * Fetch all malicious events from threat-backend service once with filters
     * Uses the same pattern as AdxIntegrationAction.fetchMaliciousEventsForExport()
     */
    private List<DashboardMaliciousEvent> fetchAllMaliciousEvents(int startTimestamp, int endTimestamp, Set<Integer> demoCollections) {
        List<DashboardMaliciousEvent> result = new ArrayList<>();
        try {
            String url = String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl());
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            Map<String, Object> filter = new HashMap<>();
            
            // Time range filter
            Map<String, Integer> time_range = new HashMap<>();
            if (startTimestamp > 0) {
                time_range.put("start", startTimestamp);
            }
            if (endTimestamp > 0) {
                time_range.put("end", endTimestamp);
            }
            filter.put("detected_at_time_range", time_range);
            
            // Filter for THREAT label
            filter.put("label", "THREAT");
            
            // Exclude demo collections via API collection filter
            try {
                List<Integer> userCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (userCollectionIds != null && !userCollectionIds.isEmpty()) {
                    // Remove demo collections from user collection IDs
                    List<Integer> filteredCollectionIds = userCollectionIds.stream()
                        .filter(id -> !demoCollections.contains(id))
                        .collect(Collectors.toList());
                    if (!filteredCollectionIds.isEmpty()) {
                        filter.put("apiCollectionId", filteredCollectionIds);
                    }
                } else if (!demoCollections.isEmpty()) {
                    // If no RBAC filter, we can't exclude demo collections via API
                    // This is a limitation - we'll filter in memory after fetching
                }
            } catch (Exception e) {
                // Ignore RBAC errors
            }

            Map<String, Object> body = new HashMap<String, Object>() {
                {
                    put("skip", 0);
                    put("limit", MAX_THREAT_FETCH_LIMIT);
                    put("sort", new HashMap<String, Integer>() {{ put("detectedAt", -1); }});
                    put("filter", filter);
                }
            };

            String msg = objectMapper.valueToTree(body).toString();
            RequestBody requestBody = RequestBody.create(msg, JSON);
            Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + this.getApiToken())
                .addHeader("Content-Type", "application/json")
                .addHeader("x-context-source", Context.contextSource.get() != null ? Context.contextSource.get().toString() : "")
                .build();

            List<DashboardMaliciousEvent> tempResult = new ArrayList<>();
            try (Response resp = httpClient.newCall(request).execute()) {
                String responseBody = resp.body() != null ? resp.body().string() : "";

                ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
                    ListMaliciousRequestsResponse.class, responseBody
                ).ifPresent(m -> {
                    tempResult.addAll(m.getMaliciousEventsList().stream()
                        .map(smr -> new DashboardMaliciousEvent(
                            smr.getId(),
                            smr.getActor(),
                            smr.getFilterId(),
                            smr.getEndpoint(),
                            com.akto.dto.type.URLMethods.Method.fromString(smr.getMethod()),
                            smr.getApiCollectionId(),
                            smr.getIp(),
                            smr.getCountry(),
                            smr.getDetectedAt(),
                            smr.getType(),
                            smr.getRefId(),
                            smr.getCategory(),
                            smr.getSubCategory(),
                            smr.getEventTypeVal(),
                            smr.getPayload(),
                            smr.getMetadata(),
                            smr.getSuccessfulExploit(),
                            smr.getStatus(),
                            smr.getLabel(),
                            smr.getHost(),
                            smr.getJiraTicketUrl(),
                            smr.getSeverity()
                        ))
                        .collect(Collectors.toList())
                    );
                });
            }
            
            // Filter out demo collections if not already filtered via API
            if (!demoCollections.isEmpty()) {
                result = tempResult.stream()
                    .filter(event -> !demoCollections.contains(event.getApiCollectionId()))
                    .collect(Collectors.toList());
            } else {
                result = tempResult;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching malicious events from threat-backend: " + e.getMessage());
        }
        return result;
    }

    /**
     * Fetch all TestingRunIssues records once with filters
     */
    private List<TestingRunIssues> fetchAllTestingRunIssues(int startTimestamp, int endTimestamp, Set<Integer> demoCollections) {
        List<TestingRunIssues> result = new ArrayList<>();
        try {
            Bson baseFilter = Filters.and(
                Filters.gte(TestingRunIssues.CREATION_TIME, startTimestamp),
                Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp),
                Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections)
            );

            // Apply RBAC filter
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (collectionIds != null && !collectionIds.isEmpty()) {
                    baseFilter = Filters.and(baseFilter, 
                        Filters.in(TestingRunIssuesDao.instance.getFilterKeyString(), collectionIds));
                }
            } catch (Exception e) {
                // Ignore
            }

            result = TestingRunIssuesDao.instance.findAll(baseFilter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching TestingRunIssues records: " + e.getMessage());
        }
        return result;
    }

    /**
     * Fetch all ApiInfo records once with filters
     */
    private List<ApiInfo> fetchAllApiInfo() {
        List<ApiInfo> result = new ArrayList<>();
        try {
            Bson baseFilter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            
            // Apply RBAC filter
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (collectionIds != null && !collectionIds.isEmpty()) {
                    baseFilter = Filters.and(baseFilter, Filters.in("collectionIds", collectionIds));
                }
            } catch (Exception e) {
                // Ignore
            }

            result = ApiInfoDao.instance.findAll(baseFilter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching ApiInfo records: " + e.getMessage());
        }
        return result;
    }

    // ========== Java-based Time Grouping Utilities ==========

    /**
     * Get time period key for grouping (day/week/month based on daysBetween)
     */
    private String getTimePeriodKey(int timestamp, long daysBetween) {
        LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        
        if (daysBetween <= 15) {
            // Day grouping: return "YYYY-MM-DD"
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (daysBetween <= 105) {
            // Week grouping: return week number
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            int week = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());
            return year + "_" + week;
        } else {
            // Month grouping: return month number
            int year = date.getYear();
            int month = date.getMonthValue();
            return year + "_" + month;
        }
    }

    /**
     * Group data by time period in Java
     */
    private Map<String, Long> groupByTimePeriod(List<Integer> timestamps, long daysBetween) {
        Map<String, Long> result = new HashMap<>();
        for (Integer timestamp : timestamps) {
            if (timestamp != null && timestamp > 0) {
                String key = getTimePeriodKey(timestamp, daysBetween);
                result.put(key, result.getOrDefault(key, 0L) + 1);
            }
        }
        return result;
    }

    /**
     * Convert time period map to BasicDBObject list format
     */
    private List<BasicDBObject> convertTimePeriodMapToResult(Map<String, Long> timePeriodMap, long daysBetween) {
        List<BasicDBObject> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : timePeriodMap.entrySet()) {
            BasicDBObject doc = new BasicDBObject();
            doc.put("_id", entry.getKey());
            doc.put("count", entry.getValue());
            result.add(doc);
        }
        
        // Sort by time period
        result.sort((a, b) -> {
            String keyA = a.getString("_id");
            String keyB = b.getString("_id");
            return keyA.compareTo(keyB);
        });
        
        return result;
    }
}
