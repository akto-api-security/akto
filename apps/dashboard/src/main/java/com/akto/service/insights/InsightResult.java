package com.akto.service.insights;

import com.mongodb.BasicDBObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One insight's computed result. Every number here is Java-computed; `markdown` (when
 * present) is the only AI-generated field, and it is validated to only ever repeat
 * numbers that already appear in `metrics`/`evidence` (see InsightNarrativeHandler).
 * Metric/Gap/Cta/Evidence nest here rather than each getting their own file — none of
 * them means anything outside an InsightResult.
 */
@Getter
@Setter
public class InsightResult {
    private String insightId;
    private String title;
    private String category;
    private String status;              // Status
    private String severity;
    private String headline;            // Java-built one-liner. NEVER from the LLM.
    private boolean metricsComplete = true;
    private List<Metric> metrics = new ArrayList<>();
    private List<Evidence> evidence = new ArrayList<>();
    private List<Cta> ctas = new ArrayList<>();
    private List<Gap> dataGaps = new ArrayList<>();
    private List<String> caveats = new ArrayList<>();
    private BasicDBObject narrativeInput;
    private String markdown;
    private String narrativeStatus = "UNAVAILABLE"; // OK | UNAVAILABLE

    public void addMetric(Metric m) { metrics.add(m); }
    public void addEvidence(Evidence e) { evidence.add(e); }
    public void addCta(Cta c) { ctas.add(c); }
    public void addDataGap(Gap g) { dataGaps.add(g); }
    public void addCaveat(String c) { caveats.add(c); }

    /** READY / PARTIAL / NO_DATA. */
    public enum Status { READY, PARTIAL, NO_DATA }

    /**
     * One headline number. `formatted` is load-bearing: the narrative prompt tells the
     * model to copy it verbatim, and the numeric guard validates every number in the
     * model's output against the set of `formatted` strings across all metrics + evidence.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Metric {
        private String key;          // stable id, referenced by the model's factsUsed
        private String label;
        private Number value;
        private Number denominator;  // nullable — lets the model say "12 of 340" without dividing
        private String unit;         // count | percent | tokens | days
        private String formatted;    // the model copies this verbatim
        private String severity;     // nullable

        public Metric(String key, String label, Number value, String unit, String formatted) {
            this.key = key; this.label = label; this.value = value; this.unit = unit; this.formatted = formatted;
        }
    }

    /** A metric that could not be computed. Never rendered as 0 — always reported here instead. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Gap {
        private String source;  // THREAT_BACKEND | SEARCH_BACKEND | TEAM_TAGS | USER_ANALYSIS | DEVICE_IDENTITY | AGENT_DESCRIPTION
        private String reason;  // NOT_CONFIGURED | REQUEST_FAILED | NO_ROWS | DEFERRED_TO_DETAIL
        private String impact;  // rendered verbatim by the narrative
    }

    /**
     * A deep-link descriptor only — the backend never performs the action. route must be
     * one of InsightRoutes' constants; params are always Java-computed values.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Cta {
        private String id;
        private String label;
        private String kind;     // NAVIGATE | GUARDRAIL_TEMPLATE | BULK_ACTION
        private String route;
        private Map<String, Object> params;
        private boolean primary;
    }

    /**
     * A bounded evidence table. `rows` is capped at InsightService.EVIDENCE_ROW_CAP;
     * `totalRowCount` carries the real count so "showing 20 of 143" stays honest.
     */
    @Getter @Setter @NoArgsConstructor
    public static class Evidence {
        private String id;
        private String title;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private int totalRowCount;
        private boolean truncated;

        public Evidence(String id, String title, List<String> columns, List<Map<String, Object>> rows, int totalRowCount) {
            this.id = id; this.title = title; this.columns = columns; this.rows = rows;
            this.totalRowCount = totalRowCount;
            this.truncated = rows != null && rows.size() < totalRowCount;
        }
    }
}
