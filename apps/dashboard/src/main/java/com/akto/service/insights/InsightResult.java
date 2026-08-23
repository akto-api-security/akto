package com.akto.service.insights;

import com.mongodb.BasicDBObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class InsightResult {
    private String insightId;       // InsightId.name()
    private String title;
    private String category;        // InsightId.Category.name()
    private String status;          // Status.name()
    private String severity;        // Java-computed, nullable
    private String headline;        // Java-built one-liner. NEVER from the LLM.
    private boolean metricsComplete; // false when LIST scope skipped deep work
    private List<Metric> metrics;
    private List<Evidence> evidence;
    private List<Cta> ctas;
    private List<Gap> dataGaps;           // non-empty iff status == PARTIAL
    private List<String> caveats;         // sentences the narrative must include verbatim
    private BasicDBObject narrativeInput; // exactly what goes to the LLM; hashed for the cache key
    private String markdown;              // detail only; null when the LLM is unavailable/not yet wired
    private String narrativeStatus;       // OK | UNAVAILABLE

    /** The result for an insight with no registered provider yet, or whose provider threw. */
    public static InsightResult noData(InsightId id, String reason) {
        InsightResult r = new InsightResult();
        r.insightId = id.name();
        r.title = id.getDefaultTitle();
        r.category = id.getCategory().name();
        r.status = Status.NO_DATA.name();
        r.metricsComplete = false;
        r.metrics = new ArrayList<>();
        r.evidence = new ArrayList<>();
        r.ctas = new ArrayList<>();
        r.dataGaps = Collections.singletonList(new Gap("PROVIDER", "NOT_IMPLEMENTED", reason));
        r.caveats = new ArrayList<>();
        r.narrativeStatus = "UNAVAILABLE";
        return r;
    }

    public enum Status {
        READY,
        PARTIAL,
        NO_DATA
    }

    /**
     * One number in an InsightResult. The narrative prompt copies {@code formatted} verbatim rather
     * than computing from {@code value}/{@code denominator} itself. {@code key} is stable and is what
     * a generated narrative's factsUsed references back to.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metric {
        private String key;
        private String label;
        private Number value;
        private Number denominator; // nullable — lets prose say "12 of 340" without dividing itself
        private String unit;        // count | percent | tokens | days
        private String formatted;   // "37 devices" — copied verbatim by the model
        private String severity;    // nullable
    }

    /** Non-empty on an InsightResult iff its status is PARTIAL. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Gap {
        private String source; // e.g. THREAT_BACKEND | POLICY_STORE | PROVIDER
        private String reason; // e.g. NOT_CONFIGURED | REQUEST_FAILED | NO_ROWS | DEFERRED_TO_DETAIL
        private String impact; // rendered verbatim in the narrative's closing line
    }

    /**
     * A deep-link descriptor only — the backend never performs the action. The frontend maps
     * kind + route + params to a link or a prefilled modal.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cta {
        private String id;                  // stable, e.g. "retire_policy"
        private String label;               // "Retire policy"
        private String kind;                // NAVIGATE | GUARDRAIL_TEMPLATE | BULK_ACTION
        private String route;               // e.g. "/dashboard/guardrails/policies"
        private Map<String, Object> params; // e.g. {"policyId": "..."}
        private boolean primary;
    }

    /** A bounded (<= EVIDENCE_ROW_CAP-ish) sample table backing an insight's claims. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String id;
        private String title;
        private List<String> columns;
        private List<Row> rows;    // capped — see the provider for its own row cap
        private int totalRowCount; // true count before capping, so "showing 20 of 61" is honest

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Row {
            private List<String> cellsFormatted;  // one per Evidence.columns, display-ready
            private Map<String, Object> cellsRaw; // optional — structured values a CTA can bind params from
        }
    }
}
