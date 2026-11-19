package com.akto.dto.threat_detection;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One doc per compliance framework: which of its ComplianceSubClauseCatalog sub-clauses have
 * actually been observed in real guardrail-violation traffic, and when. Written by
 * ComplianceClauseScanService, read by PostureService#frameworkReadiness to compute
 * clausesCovered/totalClauses for that panel — replacing the earlier enforcingPolicies/totalPolicies
 * metric, which measured "are my policies switched on" rather than "how ready am I for this
 * framework".
 *
 * clauseHits keeps a per-hit timestamp (not just a count) specifically so the readiness panel can
 * filter hits into the page's own [trendStartTs, trendEndTs] window — see CLAUDE.md design
 * principle 1 ("bucket over the page's selected date range, not a fixed lookback").
 *
 * suggestedClauses is a separate, non-scoring bucket: real violations the LLM judged as belonging
 * to this framework but matching none of ComplianceSubClauseCatalog's fixed sub-clauses — see
 * GuardrailClauseAttributionHandler's javadoc for why that's treated as catalog-incompleteness
 * signal for a human to review, not either forced into the nearest clause or silently dropped.
 * PostureService#frameworkReadiness never reads this field; it exists purely for catalog curation.
 */
@Getter
@Setter
@NoArgsConstructor
public class ComplianceClauseCoverage {

    @BsonId
    private String id; // canonical framework name (ComplianceSubClauseCatalog.canonicalFramework)

    public static final String CLAUSE_HITS = "clauseHits";
    private Map<String, List<ClauseHit>> clauseHits; // sub-clause text -> hits

    public static final String TOTAL_CLAUSES = "totalClauses";
    private int totalClauses;

    public static final String LAST_SCANNED_AT = "lastScannedAt";
    private int lastScannedAt;

    public static final String SCAN_START_TS = "scanStartTs";
    private int scanStartTs;

    public static final String SCAN_END_TS = "scanEndTs";
    private int scanEndTs;

    public static final String SUGGESTED_CLAUSES = "suggestedClauses";
    private List<SuggestedClause> suggestedClauses;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ClauseHit {
        public static final String REF_ID = "refId";
        private String refId;

        public static final String TIMESTAMP = "timestamp";
        private int timestamp;

        public static final String POLICY_NAME = "policyName";
        private String policyName;

        public ClauseHit(String refId, int timestamp, String policyName) {
            this.refId = refId;
            this.timestamp = timestamp;
            this.policyName = policyName;
        }
    }

    /** One LLM-proposed sub-clause not in the catalog, for a human to review — see this class's
     *  javadoc. Deliberately a flat list, not deduplicated/aggregated by text: proposals are free
     *  text and near-duplicates are expected, and a curator reading raw examples is more useful
     *  than a merged count. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SuggestedClause {
        public static final String TEXT = "text";
        private String text;

        public static final String REF_ID = "refId";
        private String refId;

        public static final String TIMESTAMP = "timestamp";
        private int timestamp;

        public static final String POLICY_NAME = "policyName";
        private String policyName;

        public SuggestedClause(String text, String refId, int timestamp, String policyName) {
            this.text = text;
            this.refId = refId;
            this.timestamp = timestamp;
            this.policyName = policyName;
        }
    }
}
