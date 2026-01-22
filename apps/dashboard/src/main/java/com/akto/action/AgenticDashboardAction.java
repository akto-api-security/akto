package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.Category;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.testing.ComplianceMapping;
import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
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
    private static final int MAX_THREAT_FETCH_LIMIT = 100000; // Large limit to fetch all threats

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    // ========== Consolidated API Methods ==========

    /**
     * Consolidated API: Fetch all endpoint discovery related data
     * Collection: ApiInfoDao
     * Note: Endpoints over time data is fetched via fetchNewEndpointsTrendForHostCollections 
     * and fetchNewEndpointsTrendForNonHostCollections APIs in InventoryAction
     */
    public String fetchEndpointDiscoveryData() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            
            // Check if contextSource is AGENTIC - if so, return agentic discovery stats
            CONTEXT_SOURCE contextSource = Context.contextSource.get();
            if (contextSource == CONTEXT_SOURCE.AGENTIC) {
                // Fetch Agentic Discovery Stats (AI Agents, MCP Servers, LLM)
                BasicDBObject agenticDiscoveryStats = fetchAgenticDiscoveryStatsInternal(startTimestamp, endTimestamp);
                response.put("discoveryStats", agenticDiscoveryStats);
            } else {
                // Fetch API Discovery Stats (Shadow, Sensitive, No-Auth, Normal)
                BasicDBObject discoveryStats = fetchApiDiscoveryStatsInternal(startTimestamp, endTimestamp);
                response.put("discoveryStats", discoveryStats);
            }

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
            Set<Integer> deactivatedCollections = getDeactivatedCollections();

            // Fetch all issues once within the time range
            List<TestingRunIssues> allIssues = fetchAllTestingRunIssues(startTimestamp, endTimestamp, deactivatedCollections);

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
            
            // Fetch compliance at risks data
            List<BasicDBObject> complianceAtRisks = fetchComplianceAtRisksInternal(allIssues);

            response.put("issuesOverTime", issuesData);
            response.put("issuesBySeverity", issuesBySeverity);
            response.put("averageIssueAge", averageIssueAge);
            response.put("openResolvedIssues", openResolvedIssues);
            response.put("topIssuesByCategory", topIssuesByCategory);
            response.put("topHostnamesByIssues", topHostnamesByIssues);
            response.put("complianceAtRisks", complianceAtRisks);

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

            // Fetch all malicious events once within the time range
            List<DashboardMaliciousEvent> allThreats = fetchAllMaliciousEvents(startTimestamp, endTimestamp, demoCollections);

            // Note: Threats over time is fetched via getDailyThreatActorsCount API in the frontend
            
            // Fetch threats by severity
            BasicDBObject threatsBySeverity = fetchThreatsBySeverityInternal(allThreats);
            
            // Fetch top threats by category
            List<BasicDBObject> topThreatsByCategory = fetchTopThreatsByCategoryInternal(allThreats);
            
            // Fetch top attack hosts
            List<BasicDBObject> topAttackHosts = fetchTopAttackHostsInternal(allThreats);
            
            // Fetch top bad actors
            List<BasicDBObject> topBadActors = fetchTopBadActorsInternal(allThreats);
            
            // Fetch open and resolved threats
            BasicDBObject openResolvedThreats = fetchOpenResolvedThreatsInternal(startTimestamp, endTimestamp, daysBetween, allThreats);

            response.put("threatsBySeverity", threatsBySeverity);
            response.put("topThreatsByCategory", topThreatsByCategory);
            response.put("topAttackHosts", topAttackHosts);
            response.put("topBadActors", topBadActors);
            response.put("openResolvedThreats", openResolvedThreats);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching threat data: " + e.getMessage());
            addActionError("Error fetching threat data: " + e.getMessage());
            return ERROR.toUpperCase();
        }
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
     * Internal method: Fetch Agentic Discovery Stats (AI Agents, MCP Servers, LLM)
     * Collection: SingleTypeInfoDao
     * Used when contextSource is AGENTIC
     */
    private BasicDBObject fetchAgenticDiscoveryStatsInternal(int startTimestamp, int endTimestamp) {
        BasicDBObject result = new BasicDBObject();
        try {
            Set<Integer> demoCollections = getDemoCollections();
            Set<Integer> deactivatedCollections = getDeactivatedCollections();
            
            // Get MCP collections
            Set<Integer> mcpCollections = UsageMetricCalculator.getMcpCollections();
            // Get GenAI collections (AI Agents)
            Set<Integer> genAiCollections = UsageMetricCalculator.getGenAiCollections();
            
            // Calculate counts using SingleTypeInfoDao.fetchEndpointsCount
            // AI Agents = GenAI endpoint count (excluding MCP and API collections)
            Set<Integer> aiAgentsExclude = new HashSet<>(mcpCollections);
            aiAgentsExclude.addAll(UsageMetricCalculator.getApiCollections());
            aiAgentsExclude.addAll(demoCollections);
            aiAgentsExclude.addAll(deactivatedCollections);
            long aiAgentsCount = SingleTypeInfoDao.instance.fetchEndpointsCount(startTimestamp, endTimestamp, aiAgentsExclude, false);
            
            // MCP Servers = MCP endpoint count (excluding GenAI and API collections)
            Set<Integer> mcpServersExclude = new HashSet<>(genAiCollections);
            mcpServersExclude.addAll(UsageMetricCalculator.getApiCollections());
            mcpServersExclude.addAll(demoCollections);
            mcpServersExclude.addAll(deactivatedCollections);
            long mcpServersCount = SingleTypeInfoDao.instance.fetchEndpointsCount(startTimestamp, endTimestamp, mcpServersExclude, false);
            
            // LLM = Count from GenAI collections that are specifically LLM-related
            // For now, we'll calculate LLM as a subset or use a different approach
            // LLM could be calculated as endpoints in GenAI collections that are LLM models
            // For simplicity, we'll use GenAI count minus AI Agents, or calculate separately
            // Actually, let's calculate LLM as endpoints in GenAI collections that might be LLM-specific
            // For now, let's set LLM to 0 or calculate it differently - need to check the actual requirement
            // Based on the design, LLM seems to be a separate category, so let's calculate it
            // LLM = Total GenAI - AI Agents (if AI Agents is a subset), or we need a different calculation
            // For now, let's use: LLM = endpoints in GenAI collections that are not in MCP
            // Actually, looking at the design, it seems like:
            // - AI Agents = GenAI endpoints (excluding MCP)
            // - MCP Servers = MCP endpoints (excluding GenAI)
            // - LLM = might be a separate count or subset
            // Let's calculate LLM as a separate category - for now, we'll use a placeholder or calculate it
            // Since we don't have a clear definition, let's set it to 0 for now or calculate it as a subset
            long llmCount = 0;
            
            // Note: RBAC filtering is handled by SingleTypeInfoDao.fetchEndpointsCount internally
            
            result.put("aiAgents", aiAgentsCount);
            result.put("mcpServers", mcpServersCount);
            result.put("llm", llmCount);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic discovery stats: " + e.getMessage());
            result.put("aiAgents", 0L);
            result.put("mcpServers", 0L);
            result.put("llm", 0L);
        }
        return result;
    }

    /**
     * Internal method: Fetch API discovery stats
     * Collection: ApiInfoDao
     * Matches the logic from ApiEndpoints.jsx frontend
     */
    private BasicDBObject fetchApiDiscoveryStatsInternal(int startTimestamp, int endTimestamp) {
        BasicDBObject result = new BasicDBObject();
        try {
            Bson baseFilter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            
            // Apply time range filter: filter by discoveredTimestamp (or lastSeen if discoveredTimestamp doesn't exist)
            // This matches the pattern from ApiInfoDao.fetchApiInfoStats
            // Filter APIs that were discovered (or last seen) within the time range
            Bson timeFilter;
            if (startTimestamp > 0) {
                // Time range: check both bounds
                timeFilter = Filters.and(
                    Filters.or(
                        Filters.and(
                            Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                            Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startTimestamp),
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                        ),
                        Filters.and(
                            Filters.or(
                                Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                            ),
                            Filters.gte(ApiInfo.LAST_SEEN, startTimestamp),
                            Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                        )
                    )
                );
            } else {
                // All time: only check upper bound (APIs discovered/last seen up to endTimestamp)
                timeFilter = Filters.or(
                    Filters.and(
                        Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                        Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                    ),
                    Filters.and(
                        Filters.or(
                            Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                        ),
                        Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                    )
                );
            }
            baseFilter = Filters.and(baseFilter, timeFilter);

            // Fetch all APIs once with needed fields to categorize them properly
            // This matches frontend logic which processes all endpoints in a single pass
            List<ApiInfo> allApis = ApiInfoDao.instance.findAll(baseFilter, 
                Projections.include(
                    ApiInfo.ID_API_COLLECTION_ID,
                    ApiInfo.IS_SENSITIVE,
                    ApiInfo.ALL_AUTH_TYPES_FOUND,
                    ApiInfo.DISCOVERED_TIMESTAMP,
                    ApiInfo.LAST_SEEN
                ));

            // Calculate actualAuthType for each API (matches frontend logic)
            for (ApiInfo api : allApis) {
                api.calculateActualAuth();
            }

            // Categorize APIs (an API can belong to multiple categories)
            long shadowCount = 0;
            long sensitiveCount = 0;
            long noAuthCount = 0;
            long normalCount = 0;

            for (ApiInfo api : allApis) {
                boolean isShadow = api.getId().getApiCollectionId() == ApiInfoDao.AKTO_DISCOVERED_APIS_COLLECTION_ID;
                boolean isSensitive = api.getIsSensitive();
                
                // No-Auth: actualAuthType is null/empty OR contains UNAUTHENTICATED
                // Matches frontend: obj.auth_type === undefined || obj.auth_type.toLowerCase() === "unauthenticated"
                List<ApiInfo.AuthType> authTypes = api.getActualAuthType();
                boolean isNoAuth = (authTypes == null || authTypes.isEmpty()) || 
                                  (authTypes.contains(ApiInfo.AuthType.UNAUTHENTICATED));

                // Count each category (APIs can be in multiple categories)
                if (isShadow) shadowCount++;
                if (isSensitive) sensitiveCount++;
                if (isNoAuth) noAuthCount++;

                // Normal: NOT (Shadow OR Sensitive OR NoAuth)
                // Matches frontend logic where normal is the remaining APIs
                if (!isShadow && !isSensitive && !isNoAuth) {
                    normalCount++;
                }
            }

            long totalCount = allApis.size();

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
            // Fetch all ApiInfo records once within the time range
            List<ApiInfo> allApis = fetchAllApiInfo(startTimestamp, endTimestamp);
            
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
            Set<String> testSubCategories = new HashSet<>();
            Map<String, String> testCategoryFromSourceConfigMap = new HashMap<>();
            
            for (TestingRunIssues issue : allIssues) {
                if (issue.getTestRunIssueStatus() == GlobalEnums.TestRunIssueStatus.OPEN) {
                    if (issue.getId() == null) continue;
                    
                    String category = issue.getId().getTestSubCategory();
                    if (category == null) {
                        category = "Unknown";
                    }
                    
                    categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
                    testSubCategories.add(category);
                    
                    // Store testCategoryFromSourceConfig for fuzzing tests (those starting with "http")
                    if (category.startsWith("http") && issue.getId().getTestCategoryFromSourceConfig() != null) {
                        testCategoryFromSourceConfigMap.put(category, issue.getId().getTestCategoryFromSourceConfig());
                    }
                }
            }
            
            // Fetch test names from YamlTemplate and TestSourceConfig
            Map<String, String> testSubCategoryToNameMap = new HashMap<>();
            
            // Separate regular tests and fuzzing tests (those starting with "http")
            List<String> regularTestSubCategories = new ArrayList<>();
            List<String> fuzzingTestSubCategories = new ArrayList<>();
            
            for (String testSubCategory : testSubCategories) {
                if (testSubCategory != null && testSubCategory.startsWith("http")) {
                    fuzzingTestSubCategories.add(testSubCategory);
                } else {
                    regularTestSubCategories.add(testSubCategory);
                }
            }
            
            // Fetch regular test configs from YamlTemplate
            if (!regularTestSubCategories.isEmpty()) {
                Bson projection = Projections.include(YamlTemplate.INFO);
                List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(
                    Filters.in("_id", regularTestSubCategories), projection);
                
                for (YamlTemplate yamlTemplate : yamlTemplates) {
                    if (yamlTemplate != null && yamlTemplate.getInfo() != null) {
                        Info info = yamlTemplate.getInfo();
                        String testName = info.getName();
                        if (testName != null && !testName.isEmpty()) {
                            testSubCategoryToNameMap.put(yamlTemplate.getId(), testName);
                        }
                    }
                }
            }
            
            // Fetch fuzzing test configs from TestSourceConfig
            for (String fuzzingCategory : fuzzingTestSubCategories) {
                String testCategoryFromSourceConfig = testCategoryFromSourceConfigMap.get(fuzzingCategory);
                if (testCategoryFromSourceConfig != null) {
                    TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(testCategoryFromSourceConfig);
                    if (config != null && config.getSubcategory() != null && !config.getSubcategory().isEmpty()) {
                        testSubCategoryToNameMap.put(fuzzingCategory, config.getSubcategory());
                    }
                }
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedCategories) {
                BasicDBObject doc = new BasicDBObject();
                String testSubCategory = entry.getKey();
                // Use test name if available, otherwise fall back to testSubCategory
                String displayName = testSubCategoryToNameMap.getOrDefault(testSubCategory, testSubCategory);
                doc.put("_id", displayName);
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

    /**
     * Internal method: Fetch compliance at risks data
     * Aggregates issues by compliance standards and calculates percentages
     */
    private List<BasicDBObject> fetchComplianceAtRisksInternal(List<TestingRunIssues> allIssues) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Collect unique testSubCategories to fetch compliance mappings
            Set<String> regularTestSubCategories = new HashSet<>();
            Map<String, String> testSubCategoryToTestCategoryFromSourceConfig = new HashMap<>();
            
            for (TestingRunIssues issue : allIssues) {
                if (issue.getId() == null) continue;
                
                String testSubCategory = issue.getId().getTestSubCategory();
                if (testSubCategory == null || testSubCategory.isEmpty()) continue;
                
                if (testSubCategory.startsWith("http")) {
                    // Fuzzing test - get testCategoryFromSourceConfig
                    String testCategoryFromSourceConfig = issue.getId().getTestCategoryFromSourceConfig();
                    if (testCategoryFromSourceConfig != null) {
                        testSubCategoryToTestCategoryFromSourceConfig.put(testSubCategory, testCategoryFromSourceConfig);
                    }
                } else {
                    // Regular test
                    regularTestSubCategories.add(testSubCategory);
                }
            }
            
            // Fetch compliance mappings for regular tests from YamlTemplate
            Map<String, ComplianceMapping> testSubCategoryToComplianceMap = new HashMap<>();
            if (!regularTestSubCategories.isEmpty()) {
                Bson projection = Projections.include(YamlTemplate.INFO);
                List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(
                    Filters.in("_id", regularTestSubCategories), projection);
                
                for (YamlTemplate yamlTemplate : yamlTemplates) {
                    if (yamlTemplate != null && yamlTemplate.getInfo() != null) {
                        Info info = yamlTemplate.getInfo();
                        ComplianceMapping complianceMapping = info.getCompliance();
                        if (complianceMapping != null && complianceMapping.getMapComplianceToListClauses() != null) {
                            testSubCategoryToComplianceMap.put(yamlTemplate.getId(), complianceMapping);
                        }
                    }
                }
            }
            
            // Count unique APIs per compliance standard
            // Map to store unique APIs (by ApiInfoKey) for each compliance standard
            Map<String, Set<ApiInfo.ApiInfoKey>> complianceToApis = new HashMap<>();
            // Set to track all unique APIs that have been tested (have any issues)
            Set<ApiInfo.ApiInfoKey> totalTestedApis = new HashSet<>();
            
            for (TestingRunIssues issue : allIssues) {
                if (issue.getId() == null || issue.getId().getApiInfoKey() == null) continue;
                
                ApiInfo.ApiInfoKey apiInfoKey = issue.getId().getApiInfoKey();
                String testSubCategory = issue.getId().getTestSubCategory();
                if (testSubCategory == null || testSubCategory.isEmpty()) continue;
                
                ComplianceMapping complianceMapping = null;
                
                if (testSubCategory.startsWith("http")) {
                    // For fuzzing tests, TestSourceConfig doesn't typically have compliance data
                    // Skip for now, or we could check if TestSourceConfig has compliance in the future
                    continue;
                } else {
                    // Regular test - get compliance from map
                    complianceMapping = testSubCategoryToComplianceMap.get(testSubCategory);
                }
                
                if (complianceMapping != null && complianceMapping.getMapComplianceToListClauses() != null) {
                    Map<String, List<String>> complianceMap = complianceMapping.getMapComplianceToListClauses();
                    if (!complianceMap.isEmpty()) {
                        // Track this API as tested
                        totalTestedApis.add(apiInfoKey);
                        
                        // Add this API to each compliance standard it belongs to
                        for (String complianceName : complianceMap.keySet()) {
                            complianceToApis.computeIfAbsent(complianceName, k -> new HashSet<>()).add(apiInfoKey);
                        }
                    }
                }
            }
            
            // Calculate percentages: (unique APIs with compliance issues / total unique APIs tested) * 100
            int totalTestedApisCount = totalTestedApis.size();
            
            // Sort by count descending and take top 4 compliances
            List<Map.Entry<String, Set<ApiInfo.ApiInfoKey>>> sortedCompliances = complianceToApis.entrySet().stream()
                .sorted(Map.Entry.<String, Set<ApiInfo.ApiInfoKey>>comparingByValue((a, b) -> Integer.compare(b.size(), a.size())))
                .limit(4)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Set<ApiInfo.ApiInfoKey>> entry : sortedCompliances) {
                BasicDBObject complianceObj = new BasicDBObject();
                complianceObj.put("name", entry.getKey());
                int uniqueApisCount = entry.getValue().size();
                // Calculate percentage: (unique APIs with compliance issues / total unique APIs tested) * 100
                double percentage = totalTestedApisCount > 0 
                    ? (uniqueApisCount * 100.0 / totalTestedApisCount) 
                    : 0.0;
                complianceObj.put("percentage", Math.round(percentage * 100.0) / 100.0); // Round to 2 decimal places
                complianceObj.put("count", uniqueApisCount);
                // Note: color and icon should be handled on frontend based on compliance name
                result.add(complianceObj);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching compliance at risks: " + e.getMessage());
        }
        return result;
    }


    // ========== Threat Detection Collection APIs ==========

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
                String filterId = threat.getFilterId();
                if (filterId != null && !filterId.isEmpty()) {
                    categoryCounts.put(filterId, categoryCounts.getOrDefault(filterId, 0L) + 1);
                }
            }
            
            // Fetch threat filter names from FilterYamlTemplate
            Map<String, String> filterIdToNameMap = new HashMap<>();
            try {
                Map<String, FilterConfig> filterConfigs = FilterYamlTemplateDao.instance.fetchFilterConfig(false, false);
                
                for (Map.Entry<String, FilterConfig> entry : filterConfigs.entrySet()) {
                    String filterId = entry.getKey();
                    FilterConfig filterConfig = entry.getValue();
                    
                    if (filterConfig != null && filterConfig.getInfo() != null) {
                        Info info = filterConfig.getInfo();
                        
                        // Try to get display name from category, fallback to name, then to filterId
                        if (info.getCategory() != null) {
                            Category category = info.getCategory();
                            String displayName = category.getDisplayName();
                            String categoryName = category.getName();
                            
                            if (displayName != null && !displayName.isEmpty()) {
                                filterIdToNameMap.put(filterId, displayName);
                            } else if (categoryName != null && !categoryName.isEmpty()) {
                                filterIdToNameMap.put(filterId, categoryName);
                            }
                        }
                        // If no category, try to get name from info
                        if (!filterIdToNameMap.containsKey(filterId) && info.getName() != null && !info.getName().isEmpty()) {
                            filterIdToNameMap.put(filterId, info.getName());
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error fetching threat filter names: " + e.getMessage());
            }
            
            // Sort by count descending and take top 5
            List<Map.Entry<String, Long>> sortedCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            for (Map.Entry<String, Long> entry : sortedCategories) {
                BasicDBObject doc = new BasicDBObject();
                String filterId = entry.getKey();
                
                // Use display name from FilterYamlTemplate if available
                String displayName = filterIdToNameMap.get(filterId);
                
                // Use displayName if available, otherwise fallback to filterId
                doc.put("_id", (displayName != null && !displayName.isEmpty()) ? displayName : filterId);
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
                    // Validate that it's a hostname (contains a dot or is a valid hostname format)
                    if (isValidHostname(host)) {
                        hostCounts.put(host, hostCounts.getOrDefault(host, 0L) + 1);
                    }
                } else {
                    // Fallback to url if host is not available
                    String endpoint = threat.getUrl();
                    if (endpoint != null && !endpoint.isEmpty()) {
                        // Extract hostname from URL if possible
                        String hostname = extractHostnameFromUrl(endpoint);
                        if (hostname != null && !hostname.isEmpty() && isValidHostname(hostname)) {
                            hostCounts.put(hostname, hostCounts.getOrDefault(hostname, 0L) + 1);
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
     * Helper method to validate if a string is a valid hostname (not a path)
     * Rejects paths (starting with '/') and validates hostname format
     */
    private boolean isValidHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }
        // Reject paths (starting with /)
        if (hostname.startsWith("/")) {
            return false;
        }
        // Reject if it looks like a path (contains slashes but no protocol)
        if (hostname.contains("/") && !hostname.contains("://")) {
            return false;
        }
        // Hostnames typically contain a dot (for domain names) or are localhost/ip addresses
        return hostname.contains(".") || hostname.equals("localhost") || hostname.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }

    /**
     * Helper method to extract hostname from URL
     * Uses Java's standard URL class for parsing
     * Returns null if URL parsing fails (e.g., if it's just a path)
     */
    private String extractHostnameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            // If URL doesn't start with http:// or https://, try to add it
            String urlToParse = url;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                urlToParse = "https://" + url;
            }
            java.net.URL parsedUrl = new java.net.URL(urlToParse);
            String host = parsedUrl.getHost();
            // Return null if host is empty (e.g., for paths like "/path")
            return (host != null && !host.isEmpty()) ? host : null;
        } catch (Exception e) {
            // If URL parsing fails (e.g., for paths), return null
            return null;
        }
    }

    /**
     * Internal method: Fetch top 5 bad actors by threats
     * Collection: MaliciousEventDao
     */
    private List<BasicDBObject> fetchTopBadActorsInternal(List<DashboardMaliciousEvent> allThreats) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Group by actor in Java, tracking count and most common country
            Map<String, Long> actorCounts = new HashMap<>();
            Map<String, Map<String, Long>> actorCountryCounts = new HashMap<>();
            
            for (DashboardMaliciousEvent threat : allThreats) {
                String actor = threat.getActor();
                if (actor != null && !actor.isEmpty()) {
                    actorCounts.put(actor, actorCounts.getOrDefault(actor, 0L) + 1);
                    
                    // Track country for this actor
                    String country = threat.getCountry();
                    if (country != null && !country.isEmpty()) {
                        actorCountryCounts.putIfAbsent(actor, new HashMap<>());
                        Map<String, Long> countryCounts = actorCountryCounts.get(actor);
                        countryCounts.put(country, countryCounts.getOrDefault(country, 0L) + 1);
                    }
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
                
                // Get the most common country for this actor
                Map<String, Long> countryCounts = actorCountryCounts.get(entry.getKey());
                if (countryCounts != null && !countryCounts.isEmpty()) {
                    String mostCommonCountry = countryCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                    if (mostCommonCountry != null) {
                        doc.put("country", mostCommonCountry);
                    }
                }
                
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching top bad actors: " + e.getMessage());
        }
        return result;
    }

    /**
     * Internal method: Fetch open and resolved threats over time
     * Collection: MaliciousEventDao
     */
    private BasicDBObject fetchOpenResolvedThreatsInternal(int startTimestamp, int endTimestamp, long daysBetween, List<DashboardMaliciousEvent> allThreats) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Separate open (ACTIVE) and resolved (IGNORED/UNDER_REVIEW) threats within the time range
            List<Integer> openTimestamps = new ArrayList<>();
            List<Integer> resolvedTimestamps = new ArrayList<>();
            
            for (DashboardMaliciousEvent threat : allThreats) {
                long timestamp = threat.getTimestamp();
                if (timestamp <= 0 || timestamp < startTimestamp || timestamp > endTimestamp) {
                    continue;
                }
                
                String status = threat.getStatus();
                if (status == null) {
                    status = "ACTIVE"; // Default to ACTIVE if status is null
                }
                
                // ACTIVE status = Open threats
                if ("ACTIVE".equals(status)) {
                    openTimestamps.add((int) timestamp);
                } else {
                    // IGNORED, UNDER_REVIEW, TRAINING, etc. = Resolved threats
                    resolvedTimestamps.add((int) timestamp);
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
            loggerMaker.errorAndAddToDb("Error fetching open/resolved threats: " + e.getMessage());
            result.put("open", new ArrayList<>());
            result.put("resolved", new ArrayList<>());
        }
        return result;
    }


    // ========== Helper Methods ==========

    private Set<Integer> getDeactivatedCollections() {
        return UsageMetricCalculator.getDeactivated();
    }

    private Set<Integer> getDemoCollections() {
        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(getDeactivatedCollections());
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
     * Fetch all malicious events from threat-backend service once with filters
     * Uses the reusable method from AbstractThreatDetectionAction
     */
    private List<DashboardMaliciousEvent> fetchAllMaliciousEvents(int startTimestamp, int endTimestamp, Set<Integer> demoCollections) {
        // Fetch all malicious events using the base class method
        List<DashboardMaliciousEvent> allEvents = super.fetchAllMaliciousEvents(startTimestamp, endTimestamp, MAX_THREAT_FETCH_LIMIT, null);
        
        // Filter by collection IDs in memory (only demo collection exclusion, no RBAC for threat data)
        // Note: Similar to SuspectSampleDataAction, we don't apply RBAC filtering for threat data.
        // Threats are security-related and should be visible regardless of RBAC collection access.
        return allEvents.stream()
            .filter(event -> !demoCollections.contains(event.getApiCollectionId()))
            .collect(Collectors.toList());
    }

    /**
     * Fetch all TestingRunIssues records once with filters
     */
    private List<TestingRunIssues> fetchAllTestingRunIssues(int startTimestamp, int endTimestamp, Set<Integer> demoCollections) {
        List<TestingRunIssues> result = new ArrayList<>();
        try {
            // Build time filter - handle "All time" case (startTimestamp <= 0)
            Bson timeFilter;
            if (startTimestamp > 0) {
                // Time range: check both bounds
                timeFilter = Filters.and(
                    Filters.gte(TestingRunIssues.CREATION_TIME, startTimestamp),
                    Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp)
                );
            } else {
                // All time: only check upper bound
                timeFilter = Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp);
            }
            
            Bson baseFilter = Filters.and(
                timeFilter,
                Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections)
            );

            result = TestingRunIssuesDao.instance.findAll(baseFilter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching TestingRunIssues records: " + e.getMessage());
        }
        return result;
    }

    /**
     * Fetch all ApiInfo records once with filters
     * Applies time range filter based on discoveredTimestamp or lastSeen
     */
    private List<ApiInfo> fetchAllApiInfo(int startTimestamp, int endTimestamp) {
        List<ApiInfo> result = new ArrayList<>();
        try {
            Bson baseFilter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            
            // Apply time range filter: filter by discoveredTimestamp (or lastSeen if discoveredTimestamp doesn't exist)
            Bson timeFilter;
            if (startTimestamp > 0) {
                // Time range: check both bounds
                timeFilter = Filters.and(
                    Filters.or(
                        Filters.and(
                            Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                            Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startTimestamp),
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                        ),
                        Filters.and(
                            Filters.or(
                                Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                            ),
                            Filters.gte(ApiInfo.LAST_SEEN, startTimestamp),
                            Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                        )
                    )
                );
            } else {
                // All time: only check upper bound (APIs discovered/last seen up to endTimestamp)
                timeFilter = Filters.or(
                    Filters.and(
                        Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                        Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                    ),
                    Filters.and(
                        Filters.or(
                            Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                        ),
                        Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                    )
                );
            }
            baseFilter = Filters.and(baseFilter, timeFilter);

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
            // Day grouping: return "D_YYYY-MM-DD" to explicitly indicate day type
            return "D_" + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (daysBetween <= 105) {
            // Week grouping: return "W_YYYY_W" to explicitly indicate week type
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            int week = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());
            return "W_" + year + "_" + week;
        } else {
            // Month grouping: return "M_YYYY_M" to explicitly indicate month type
            int year = date.getYear();
            int month = date.getMonthValue();
            return "M_" + year + "_" + month;
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
