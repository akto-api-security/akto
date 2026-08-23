package com.akto.service.insights;

import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class InsightResult {
    private String insightId;       // InsightId.name()
    private String title;
    private String category;        // InsightCategory.name()
    private String status;          // InsightStatus.name()
    private String severity;        // Java-computed, nullable
    private String headline;        // Java-built one-liner. NEVER from the LLM.
    private boolean metricsComplete; // false when LIST scope skipped deep work
    private List<InsightMetric> metrics;
    private List<EvidenceTable> evidence;
    private List<InsightCta> ctas;
    private List<DataGap> dataGaps;       // non-empty iff status == PARTIAL
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
        r.status = InsightStatus.NO_DATA.name();
        r.metricsComplete = false;
        r.metrics = new ArrayList<>();
        r.evidence = new ArrayList<>();
        r.ctas = new ArrayList<>();
        r.dataGaps = Collections.singletonList(new DataGap("PROVIDER", "NOT_IMPLEMENTED", reason));
        r.caveats = new ArrayList<>();
        r.narrativeStatus = "UNAVAILABLE";
        return r;
    }
}
