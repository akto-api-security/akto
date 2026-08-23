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
 */
public class InsightDataBundle {

    private final Loaded<List<GuardrailPolicies>> policies;
    // (category, subCategory) -> count for the window, unscoped (no latestAttack filter) so every
    // provider that needs a violation-count breakdown shares this one free aggregation rather than
    // each calling get_subcategory_wise_count separately. For guardrail-written events, category
    // is the policy name.
    private final Loaded<List<ThreatCategoryCount>> subCategoryCounts;
    // skillName -> severity breakdown for the window, no user dimension (that needs raw event
    // paging — see fetchAllMaliciousEvents(..., "only")). Cheap enough to always load, like
    // subCategoryCounts, so a LIST-scope provider has a real headline before any DETAIL paging.
    private final Loaded<List<SkillSeverityCount>> skillSeverityCounts;
    // Device label (ModuleInfo.name) -> username, inverse of ModuleInfoDao's own live join —
    // best-effort resolution for the raw device hashes that sometimes show up as an event's actor.
    // A lookup miss just means "no live agent for this device" or "already a real username" —
    // callers fall back to the raw actor string either way, never a hard failure.
    private final Loaded<Map<String, String>> deviceIdToUsername;
    // Non-deactivated collections with a hostName, minimal projection (id/hostName/startTs) — NOT
    // the full projection a future discovery-side "collections" bundle field would carry (tags,
    // skills, risk score); this is scoped to exactly what policy-hygiene coverage checking needs.
    // Mirrors ApiCollectionsDao.fetchAllActiveHosts()'s own filter (hostName exists, not
    // deactivated) rather than inventing a different one, widened only to add startTs.
    private final Loaded<List<ApiCollection>> activeCollections;
    // apiCollectionId -> last-seen-traffic epoch second, from ApiInfoDao.getLastTrafficSeen() — a
    // collection missing here (tag/config-scan-discovered but never directly trafficked) should
    // fall back to its own startTs, not be treated as having no activity at all.
    private final Loaded<Map<Integer, Integer>> collectionLastTrafficSeen;

    private InsightDataBundle(Builder builder) {
        this.policies = builder.policies;
        this.subCategoryCounts = builder.subCategoryCounts;
        this.skillSeverityCounts = builder.skillSeverityCounts;
        this.deviceIdToUsername = builder.deviceIdToUsername;
        this.activeCollections = builder.activeCollections;
        this.collectionLastTrafficSeen = builder.collectionLastTrafficSeen;
    }

    public Loaded<List<GuardrailPolicies>> getPolicies() {
        return policies;
    }

    public Loaded<List<ThreatCategoryCount>> getSubCategoryCounts() {
        return subCategoryCounts;
    }

    public Loaded<List<SkillSeverityCount>> getSkillSeverityCounts() {
        return skillSeverityCounts;
    }

    public Loaded<Map<String, String>> getDeviceIdToUsername() {
        return deviceIdToUsername;
    }

    public Loaded<List<ApiCollection>> getActiveCollections() {
        return activeCollections;
    }

    public Loaded<Map<Integer, Integer>> getCollectionLastTrafficSeen() {
        return collectionLastTrafficSeen;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Loaded<List<GuardrailPolicies>> policies = Loaded.failed("not loaded");
        private Loaded<List<ThreatCategoryCount>> subCategoryCounts = Loaded.failed("not loaded");
        private Loaded<List<SkillSeverityCount>> skillSeverityCounts = Loaded.failed("not loaded");
        private Loaded<Map<String, String>> deviceIdToUsername = Loaded.failed("not loaded");
        private Loaded<List<ApiCollection>> activeCollections = Loaded.failed("not loaded");
        private Loaded<Map<Integer, Integer>> collectionLastTrafficSeen = Loaded.failed("not loaded");

        public Builder policies(Loaded<List<GuardrailPolicies>> v) {
            this.policies = v;
            return this;
        }

        public Builder subCategoryCounts(Loaded<List<ThreatCategoryCount>> v) {
            this.subCategoryCounts = v;
            return this;
        }

        public Builder skillSeverityCounts(Loaded<List<SkillSeverityCount>> v) {
            this.skillSeverityCounts = v;
            return this;
        }

        public Builder activeCollections(Loaded<List<ApiCollection>> v) {
            this.activeCollections = v;
            return this;
        }

        public Builder collectionLastTrafficSeen(Loaded<Map<Integer, Integer>> v) {
            this.collectionLastTrafficSeen = v;
            return this;
        }

        public Builder deviceIdToUsername(Loaded<Map<String, String>> v) {
            this.deviceIdToUsername = v;
            return this;
        }

        public InsightDataBundle build() {
            return new InsightDataBundle(this);
        }
    }
}
