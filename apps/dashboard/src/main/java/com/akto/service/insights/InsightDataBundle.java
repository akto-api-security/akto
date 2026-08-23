package com.akto.service.insights;

import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;

import java.util.List;
import java.util.Map;

/**
 * Immutable shared read model — one InsightDataLoader.load() populates this per request, and
 * every provider (LIST and DETAIL alike) reads from the same instance instead of querying Mongo
 * or the threat backend itself. Fields are added one at a time as each provider needs them, via
 * InsightDataBundle.Builder, so this stays the single place new data sources get wired in
 * regardless of which insight needs them first.
 *
 * Every field is nullable: {@code null} means the load failed, a non-null (possibly empty)
 * collection means it succeeded.
 */
public class InsightDataBundle {

    private final List<GuardrailPolicies> policies;
    // (category, subCategory) -> count for the window, unscoped (no latestAttack filter) so every
    // provider that needs a violation-count breakdown shares this one free aggregation rather than
    // each calling get_subcategory_wise_count separately. For guardrail-written events, category
    // is the policy name.
    private final List<ThreatCategoryCount> subCategoryCounts;
    // skillName -> severity breakdown for the window, no user dimension (that needs raw event
    // paging — see fetchAllMaliciousEvents(..., "only")). Cheap enough to always load, like
    // subCategoryCounts, so a LIST-scope provider has a real headline before any DETAIL paging.
    private final List<SkillSeverityCount> skillSeverityCounts;
    // Device label (ModuleInfo.name) -> username, inverse of ModuleInfoDao's own live join —
    // best-effort resolution for the raw device hashes that sometimes show up as an event's actor.
    // A lookup miss just means "no live agent for this device" or "already a real username" —
    // callers fall back to the raw actor string either way, never a hard failure.
    private final Map<String, String> deviceIdToUsername;
    // Non-deactivated collections with a hostName, minimal projection (id/hostName/startTs) — NOT
    // the full projection a future discovery-side "collections" bundle field would carry (tags,
    // skills, risk score); this is scoped to exactly what policy-hygiene coverage checking needs.
    // Mirrors ApiCollectionsDao.fetchAllActiveHosts()'s own filter (hostName exists, not
    // deactivated) rather than inventing a different one, widened only to add startTs.
    private final List<ApiCollection> activeCollections;
    // apiCollectionId -> last-seen-traffic epoch second, from ApiInfoDao.getLastTrafficSeen() — a
    // collection missing here (tag/config-scan-discovered but never directly trafficked) should
    // fall back to its own startTs, not be treated as having no activity at all.
    private final Map<Integer, Integer> collectionLastTrafficSeen;

    private InsightDataBundle(Builder builder) {
        this.policies = builder.policies;
        this.subCategoryCounts = builder.subCategoryCounts;
        this.skillSeverityCounts = builder.skillSeverityCounts;
        this.deviceIdToUsername = builder.deviceIdToUsername;
        this.activeCollections = builder.activeCollections;
        this.collectionLastTrafficSeen = builder.collectionLastTrafficSeen;
    }

    public List<GuardrailPolicies> getPolicies() {
        return policies;
    }

    public List<ThreatCategoryCount> getSubCategoryCounts() {
        return subCategoryCounts;
    }

    public List<SkillSeverityCount> getSkillSeverityCounts() {
        return skillSeverityCounts;
    }

    public Map<String, String> getDeviceIdToUsername() {
        return deviceIdToUsername;
    }

    public List<ApiCollection> getActiveCollections() {
        return activeCollections;
    }

    public Map<Integer, Integer> getCollectionLastTrafficSeen() {
        return collectionLastTrafficSeen;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<GuardrailPolicies> policies;
        private List<ThreatCategoryCount> subCategoryCounts;
        private List<SkillSeverityCount> skillSeverityCounts;
        private Map<String, String> deviceIdToUsername;
        private List<ApiCollection> activeCollections;
        private Map<Integer, Integer> collectionLastTrafficSeen;

        public Builder policies(List<GuardrailPolicies> v) {
            this.policies = v;
            return this;
        }

        public Builder subCategoryCounts(List<ThreatCategoryCount> v) {
            this.subCategoryCounts = v;
            return this;
        }

        public Builder skillSeverityCounts(List<SkillSeverityCount> v) {
            this.skillSeverityCounts = v;
            return this;
        }

        public Builder activeCollections(List<ApiCollection> v) {
            this.activeCollections = v;
            return this;
        }

        public Builder collectionLastTrafficSeen(Map<Integer, Integer> v) {
            this.collectionLastTrafficSeen = v;
            return this;
        }

        public Builder deviceIdToUsername(Map<String, String> v) {
            this.deviceIdToUsername = v;
            return this;
        }

        public InsightDataBundle build() {
            return new InsightDataBundle(this);
        }
    }
}
