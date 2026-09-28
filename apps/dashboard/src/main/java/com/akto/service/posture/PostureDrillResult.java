package com.akto.service.posture;

import com.akto.service.insights.InsightResult;
import com.mongodb.BasicDBObject;
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
    /** Worst real severity ("CRITICAL"/"HIGH"/"MEDIUM"/"LOW") among this level's own rows, when
     *  any carry one — see PostureService#worstSeverity. Null when no row has a severity field
     *  (most drills don't; never invented to fill the gap). Structural, not LLM-generated —
     *  always present immediately, independent of narrativeStatus. */
    private String severity;
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
    private BasicDBObject riskScoreBreakdown;

    /** Null (today's shape) renders the generic AgGridTable. "profile" renders an entity page
     *  (header + badge + facts + sections) instead — see PostureService's ProfileBuilder and the
     *  package CLAUDE.md's "risk score breakdown third level" section. Only ever set on the
     *  risk-score-breakdown drill's own third level (path has 2 segments), one per sub-score. */
    private String layout;
    /** Profile layout only — subtitle line under the title ("5 AI tools used · 7 flagged actions
     *  in this window"). */
    private String subtitle;
    /** Profile layout only — the risk/status pill next to the title. */
    private Badge badge;
    /** Profile layout only — an info banner above the stats (e.g. the "coaching, not conclusions"
     *  copy on a device profile). Null when there's nothing to say. */
    private String notice;
    /** Profile layout only — the key/value facts card. */
    private List<Fact> facts = new ArrayList<>();
    /** Profile layout only — one or more content sections (a timeline or a table) below the facts
     *  card. Each section's own rows are capped server-side (see PostureService.PROFILE_SECTION_CAP)
     *  — {@link Section#total} carries the real count for a "showing N of M" footer. */
    private List<Section> sections = new ArrayList<>();

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

    /** tone: "critical" | "warning" | "success" | "info" — matches Polaris Badge's own status
     *  vocabulary so the frontend can pass it straight through. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Badge {
        private String label;
        private String tone;
    }

    /** One row of the profile's key/value facts card. tone is optional (null = default text
     *  color) — used for things like an overdue-training date that should read as a warning. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fact {
        private String label;
        private String value;
        private String tone;
    }

    /** One profile content block. kind "timeline" renders {@link #rows} as a vertical activity
     *  feed (each row: timestamp/title/detail/severity/status); kind "table" renders them as a
     *  plain table using {@link #columns}. total is the real, uncapped count behind rows (which
     *  is capped at PostureService.PROFILE_SECTION_CAP), so the UI can show "Showing N of M". */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String id;
        private String title;
        private String subtitle;
        private String kind;
        private List<ColumnDef> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private long total;
    }
}
