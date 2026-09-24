package com.akto.service.posture;

import com.akto.service.insights.InsightResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One level of a posture panel's paginated drilldown flyout (see the AI Security Posture
 * CLAUDE.md's "paginated drilldown flyouts" section). Reuses InsightResult's Metric/Cta/Gap
 * directly rather than a parallel DTO family, per the PRD's explicit instruction — see
 * https://claude.ai/artifact/S2UEiM3CSG1UiiyCxZkvtN. Does NOT reuse InsightResult.Evidence:
 * Evidence is a fixed-cap "up to N of total" shape with no skip/offset concept, a different
 * contract than this drilldown's real skip/limit paging.
 *
 * Every row in {@link #rows} carries an "id" entry — the value that becomes the next `path`
 * segment when a row is clicked and {@link #drillable} is true for this level. Leaf-level
 * responses (no further level to open) set drillable=false and rows omit "id".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostureDrillResult {
    private String title;
    /** Root..current, INCLUDING this level — lets a reload from a deep-linked `path` render the
     *  full breadcrumb trail without the frontend re-fetching every ancestor level. */
    private List<BreadcrumbItem> breadcrumb = new ArrayList<>();
    private List<InsightResult.Metric> summary = new ArrayList<>();
    private List<ColumnDef> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private boolean drillable;
    private long total;
    private int skip;
    private int limit;
    /** The panel's existing destination (GUARDRAIL_VIOLATIONS, AGENTIC_ASSETS, ...), preserved as
     *  an escape hatch — this drilldown adds an in-context view, it doesn't remove the old link. */
    private List<InsightResult.Cta> ctas = new ArrayList<>();
    private List<InsightResult.Gap> dataGaps = new ArrayList<>();

    /** Same AI-narrative contract InsightResult uses (see InsightService's own narrative
     *  section) — OK | PENDING | UNAVAILABLE. PENDING means PostureDrillNarrativeService has
     *  kicked off a background generation job for this exact level; a re-fetch of the same
     *  drillId/path a few seconds later picks up the cached prose once it lands. */
    private String narrativeStatus = "UNAVAILABLE";
    private String narrativeMarkdown;
    private String narrativeConcern;
    private String narrativeImpact;
    private String narrativeRemediation;

    public void addDataGap(InsightResult.Gap gap) { dataGaps.add(gap); }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String path;   // the `path` value that navigates to this level, "" for the root
        private String label;
    }

    /** field is the key each row map uses; headerName is the display label — the shape AgGridTable
     *  (this drilldown's table component) already expects for a columnDef. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDef {
        private String field;
        private String headerName;
    }
}
