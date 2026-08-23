package com.akto.service.insights;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the InsightDataBundle for one request — the "ONE pass over Mongo (+ one free threat-
 * backend aggregation)" referred to across this package. {@code threatClient} is the calling
 * InsightsAction itself (it extends AbstractThreatDetectionAction for exactly this reason) —
 * passed in rather than constructed here so this loader stays a plain, testable function of
 * (ctx, client) -> bundle.
 */
public class InsightDataLoader {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InsightDataLoader.class, LogDb.DASHBOARD);

    private InsightDataLoader() {}

    public static InsightDataBundle load(InsightContext ctx, AbstractThreatDetectionAction threatClient) {
        return InsightDataBundle.builder()
                .policies(loadPolicies())
                .subCategoryCounts(loadSubCategoryCounts(ctx, threatClient))
                .skillSeverityCounts(loadSkillSeverityCounts(ctx, threatClient))
                .deviceIdToUsername(loadDeviceIdToUsername())
                .activeCollections(loadActiveCollections())
                .collectionLastTrafficSeen(loadCollectionLastTrafficSeen())
                .build();
    }

    private static List<GuardrailPolicies> loadPolicies() {
        try {
            List<GuardrailPolicies> policies = GuardrailPoliciesDao.instance.findAll(
                    com.mongodb.client.model.Filters.empty());
            return policies != null ? policies : new ArrayList<>();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load guardrail policies");
            return null;
        }
    }

    // Matches list_malicious_requests' own server-side default (ThreatDetectionConstants.ACTIVE)
    // so a LIST-scope aggregate and a DETAIL-scope fetchAllMaliciousEvents page are counting the
    // same status population — passing null here would silently include IGNORED/UNDER_REVIEW/
    // TRAINING rows in the aggregate while the paged events stay ACTIVE-only, making "empty page
    // despite N known violations" an unreliable failure signal.
    private static final String ACTIVE_STATUS_FILTER = "ACTIVE";

    private static List<ThreatCategoryCount> loadSubCategoryCounts(
            InsightContext ctx, AbstractThreatDetectionAction threatClient) {
        try {
            List<ThreatCategoryCount> counts = threatClient.fetchSubcategoryWiseCounts(
                    ctx.getStartTs(), ctx.getEndTs(), Collections.emptyList(), ACTIVE_STATUS_FILTER);
            return counts != null ? counts : new ArrayList<>();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load subcategory-wise violation counts");
            return null;
        }
    }

    private static List<SkillSeverityCount> loadSkillSeverityCounts(
            InsightContext ctx, AbstractThreatDetectionAction threatClient) {
        try {
            List<SkillSeverityCount> counts = threatClient.fetchSkillSeverityCounts(ctx.getStartTs(), ctx.getEndTs());
            return counts != null ? counts : new ArrayList<>();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load skill severity counts");
            return null;
        }
    }

    private static List<ApiCollection> loadActiveCollections() {
        try {
            // Mirrors ApiCollectionsDao.fetchAllActiveHosts()'s own filter (hostName exists, not
            // deactivated) rather than inventing a different one — widened only to add startTs,
            // which that helper's hardcoded projection omits and policy-hygiene coverage needs for
            // its "no direct traffic entry yet" fallback.
            Bson filter = Filters.and(Filters.exists(ApiCollection.HOST_NAME, true), Filters.ne(ApiCollection._DEACTIVATED, true));
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    filter, Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.START_TS));
            return collections != null ? collections : new ArrayList<>();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load active collections");
            return null;
        }
    }

    private static Map<Integer, Integer> loadCollectionLastTrafficSeen() {
        try {
            Map<Integer, Integer> lastSeen = ApiInfoDao.instance.getLastTrafficSeen();
            return lastSeen != null ? lastSeen : new HashMap<>();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load collection last-traffic-seen map");
            return null;
        }
    }

    private static Map<String, String> loadDeviceIdToUsername() {
        try {
            Map<String, Set<String>> usernameToDevices = ModuleInfoDao.instance.fetchUsernameToDeviceIdsForEndpointShield();
            Map<String, String> inverse = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : usernameToDevices.entrySet()) {
                for (String deviceId : e.getValue()) {
                    inverse.putIfAbsent(deviceId, e.getKey());
                }
            }
            return inverse;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "InsightDataLoader: failed to load device-to-username map");
            return null;
        }
    }
}
