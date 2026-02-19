package com.akto.util;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.threat_detection.ThreatComplianceInfosDao;
import com.akto.dto.threat_detection.ThreatComplianceInfo;
import com.akto.util.enums.GlobalEnums;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.Category;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;


public class GuardrailMetricsProcessor {
    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailMetricsProcessor.class, LogDb.DASHBOARD);

    public static Map<String, ThreatComplianceInfo> fetchThreatComplianceMap() {
        try {
            Bson emptyFilter = Filters.empty();
            List<ThreatComplianceInfo> threatComplianceList =
                    ThreatComplianceInfosDao.instance.findAll(emptyFilter);

            return threatComplianceList.stream()
                    .filter(info -> info.getId() != null)
                    .collect(Collectors.toMap(
                            ThreatComplianceInfo::getId,
                            info -> info
                    ));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching threat compliance map: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public static class GuardrailMetrics {
        public List<BasicDBObject> topGuardrailPolicies;
        public BasicDBObject dataProtectionTrends;
        public double avgThreatScore;
        public long sensitiveCount;
        public long successfulExploits;
        public List<BasicDBObject> complianceAtRisks;
        public List<BasicDBObject> attackFlows;
    }

    public static GuardrailMetrics processGuardrailMetrics(
            List<DashboardMaliciousEvent> allThreats,
            int startTimestamp,
            int endTimestamp,
            long daysBetween,
            Map<String, ThreatComplianceInfo> threatComplianceMap
    ) {
        GuardrailMetrics metrics = new GuardrailMetrics();


        Map<String, Long> policyCounts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();
        Map<String, List<Integer>> categoryTimestamps = new HashMap<>();
        Map<String, Set<String>> complianceEndpoints = new HashMap<>();
        Set<String> totalGuardrailEndpoints = new HashSet<>();
        double totalScore = 0.0;
        int scoreCount = 0;
        long sensitiveEventCount = 0;
        long successfulExploitCount = 0;

        // Track attack flows for world map: "sourceCountry|destCountry" -> attackType -> count
        Map<String, Map<String, Long>> countryFlowAttackCounts = new HashMap<>();
        Map<String, String> countryFlowMapping = new HashMap<>(); 


        for (DashboardMaliciousEvent event : allThreats) {
            // 1. Count guardrail policies
            String filterId = event.getFilterId();
            if (filterId != null && !filterId.isEmpty()) {
                policyCounts.put(filterId, policyCounts.getOrDefault(filterId, 0L) + 1);
            }

            // 2. Collect data protection trends data
            long timestamp = event.getTimestamp();
            if (timestamp > 0 && timestamp >= startTimestamp && timestamp <= endTimestamp) {
                String category = event.getSubCategory();
                if (category == null || category.isEmpty()) {
                    category = event.getFilterId();
                }
                if (category == null || category.isEmpty()) {
                    category = "Unknown";
                }

                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
                categoryTimestamps.computeIfAbsent(category, k -> new ArrayList<>()).add((int) timestamp);
            }

            // 3. Calculate average threat score (all events)
            String severityStr = event.getSeverity();
            if (severityStr != null && !severityStr.isEmpty()) {
                float score = getSeverityScore(severityStr);
                if (score > 0) {
                    totalScore += score;
                    scoreCount++;
                }
            }

            // 4. Count sensitive data events
            String subCategory = event.getSubCategory();
            boolean isPIIFilter = ThreatDetectionConstants.PII_DATA_LEAK_FILTER_ID.equals(filterId);
            boolean isPIISubCategory = subCategory != null &&
                    ThreatDetectionConstants.PII_SUBCATEGORIES.contains(subCategory);
            if (isPIIFilter || isPIISubCategory) {
                sensitiveEventCount++;
            }

            // 5. Count successful exploits
            if (event.getSuccessfulExploit()) {
                successfulExploitCount++;
            }

            // 6. Collect compliance data
            if (filterId != null && !filterId.isEmpty()) {
                // Create unique endpoint identifier
                String endpointKey = event.getMethod() + "|" + event.getUrl();

                // Track all unique guardrail endpoints
                totalGuardrailEndpoints.add(endpointKey);

                // Map to compliance standards if compliance data is available
                if (threatComplianceMap != null) {
                    String complianceKey = "threat_compliance/" + filterId + ".conf";
                    ThreatComplianceInfo complianceInfo = threatComplianceMap.get(complianceKey);

                    if (complianceInfo != null && complianceInfo.getMapComplianceToListClauses() != null) {
                        Map<String, List<String>> complianceToClausesMap = complianceInfo.getMapComplianceToListClauses();

                        // Add this endpoint to each compliance standard it maps to
                        for (String complianceStandard : complianceToClausesMap.keySet()) {
                            complianceEndpoints.computeIfAbsent(complianceStandard, k -> new HashSet<>()).add(endpointKey);
                        }
                    }
                }
            }

            // 7. Collect attack flows for world map
            String sourceCountry = event.getCountry();
            String destCountry = event.getDestCountry();

            if (sourceCountry != null && !sourceCountry.isEmpty() &&
                destCountry != null && !destCountry.isEmpty()) {

                String flowKey = sourceCountry + "|" + destCountry;

                String attackType = event.getSubCategory();
                if (attackType == null || attackType.isEmpty()) {
                    attackType = event.getCategory();
                }
                if (attackType == null || attackType.isEmpty()) {
                    attackType = "Unknown";
                }

                countryFlowAttackCounts.putIfAbsent(flowKey, new HashMap<>());
                Map<String, Long> attackCounts = countryFlowAttackCounts.get(flowKey);
                attackCounts.put(attackType, attackCounts.getOrDefault(attackType, 0L) + 1);

                countryFlowMapping.put(flowKey, sourceCountry + "|" + destCountry);
            }
        }

        // Process collected data
        metrics.topGuardrailPolicies = buildTopGuardrailPolicies(policyCounts);
        metrics.dataProtectionTrends = buildDataProtectionTrends(categoryCounts, categoryTimestamps, daysBetween);
        metrics.avgThreatScore = scoreCount > 0 ? Math.round((totalScore / scoreCount) * 10.0) / 10.0 : 0.0;
        metrics.sensitiveCount = sensitiveEventCount;
        metrics.successfulExploits = successfulExploitCount;
        metrics.complianceAtRisks = buildComplianceAtRisks(complianceEndpoints, totalGuardrailEndpoints.size());
        metrics.attackFlows = buildAttackFlows(countryFlowAttackCounts, countryFlowMapping);

        return metrics;
    }


    private static List<BasicDBObject> buildTopGuardrailPolicies(Map<String, Long> policyCounts) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
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
                loggerMaker.errorAndAddToDb("Error fetching guardrail filter names: " + e.getMessage());
            }

            // Sort by count descending and take top 4
            List<Map.Entry<String, Long>> sortedPolicies = policyCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(4)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Long> entry : sortedPolicies) {
                BasicDBObject doc = new BasicDBObject();
                String filterId = entry.getKey();

                // Use display name from FilterYamlTemplate if available
                String displayName = filterIdToNameMap.get(filterId);

                // Use displayName if available, otherwise fallback to filterId
                doc.put("name", (displayName != null && !displayName.isEmpty()) ? displayName : filterId);
                doc.put("count", entry.getValue());
                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building top guardrail policies: " + e.getMessage());
        }
        return result;
    }

    private static BasicDBObject buildDataProtectionTrends(
            Map<String, Long> categoryCounts,
            Map<String, List<Integer>> categoryTimestamps,
            long daysBetween
    ) {
        BasicDBObject result = new BasicDBObject();
        try {
            // Sort categories by count and take top 3
            List<String> topCategories = categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // Create time-series data for each top category
            for (String category : topCategories) {
                List<Integer> timestamps = categoryTimestamps.get(category);
                if (timestamps != null && !timestamps.isEmpty()) {
                    Map<String, Long> timePeriodMap = TimePeriodUtils.groupByTimePeriod(timestamps, daysBetween);
                    List<List<Object>> timeSeriesData = TimePeriodUtils.convertTimePeriodMapToTimeSeriesData(timePeriodMap, daysBetween);
                    result.put(category, timeSeriesData);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building data protection trends: " + e.getMessage());
        }
        return result;
    }

    private static List<BasicDBObject> buildComplianceAtRisks(Map<String, Set<String>> complianceEndpoints, int totalEndpointsCount) {
        List<BasicDBObject> result = new ArrayList<>();
        try {
            // Sort by number of unique endpoints descending and take top 4
            List<Map.Entry<String, Set<String>>> sortedCompliance = complianceEndpoints.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                    .limit(4)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Set<String>> entry : sortedCompliance) {
                BasicDBObject doc = new BasicDBObject();
                doc.put("name", entry.getKey());
                int uniqueEndpointsCount = entry.getValue().size();
                doc.put("count", uniqueEndpointsCount);

                // Calculate percentage: (unique endpoints with compliance issues / total unique endpoints) * 100
                double percentage = totalEndpointsCount > 0
                    ? (uniqueEndpointsCount * 100.0 / totalEndpointsCount)
                    : 0.0;
                doc.put("percentage", Math.round(percentage * 100.0) / 100.0); // Round to 2 decimal places

                result.add(doc);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building compliance at risks: " + e.getMessage());
        }
        return result;
    }

    private static float getSeverityScore(String severityStr) {
        try {
            GlobalEnums.Severity severity = GlobalEnums.Severity.valueOf(severityStr.toUpperCase());
            switch (severity) {
                case CRITICAL:
                    return 1.0f;
                case HIGH:
                    return 0.75f;
                case MEDIUM:
                    return 0.5f;
                case LOW:
                    return 0.25f;
                default:
                    return 0.0f;
            }
        } catch (Exception e) {
            return 0.0f;
        }
    }


    private static List<BasicDBObject> buildAttackFlows(
            Map<String, Map<String, Long>> countryFlowAttackCounts,
            Map<String, String> countryFlowMapping) {
        List<BasicDBObject> attackFlows = new ArrayList<>();
        try {
            for (Map.Entry<String, Map<String, Long>> flowEntry : countryFlowAttackCounts.entrySet()) {
                String flowKey = flowEntry.getKey(); 
                Map<String, Long> attackCounts = flowEntry.getValue();

                String[] countries = flowKey.split("\\|");
                if (countries.length != 2) {
                    continue; 
                }
                String sourceCountry = countries[0];
                String destinationCountry = countries[1];

                Map.Entry<String, Long> topAttack = attackCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElse(null);

                if (topAttack != null) {
                    BasicDBObject flow = new BasicDBObject();
                    flow.put("sourceCountry", sourceCountry);
                    flow.put("destinationCountry", destinationCountry);
                    flow.put("attackType", topAttack.getKey());
                    flow.put("count", topAttack.getValue());
                    attackFlows.add(flow);
                }
            }

            attackFlows.sort((a, b) -> {
                long countA = a.getLong("count", 0L);
                long countB = b.getLong("count", 0L);
                return Long.compare(countB, countA);
            });

            if (attackFlows.size() > 20) {
                attackFlows = attackFlows.subList(0, 20);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building attack flows: " + e.getMessage());
        }
        return attackFlows;
    }
}
