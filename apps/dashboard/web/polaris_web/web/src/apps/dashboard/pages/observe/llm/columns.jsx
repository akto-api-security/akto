import {
    AppCell,
    CountCell,
    DurationCell,
    GuardrailPolicyCell,
    GuardrailStatusCell,
    IdCell,
    ModelChipCellRenderer,
    ModelsCell,
    SessionLinkCell,
    TimeCell,
    PreviewCell,
    TokensCell,
    TopicCell,
    UnifiedIdCell,
    UserCell,
    unifiedRowId,
} from "./LLMCellRenderers";
import { formatDurationMs } from "./constants";

// AG Grid column definitions for the three LLM Observability tables. Kept in a .jsx
// file (separate from constants.js) because they reference React cell renderers.

const FLEX_CELL = { display: "flex", alignItems: "center" };

const userServiceCols = [
    {
        headerName: "User",
        field: "userName",
        width: 130,
        filterAllowed: true,
        filter: "agSetColumnFilter",
        sortable: true,
        cellStyle: FLEX_CELL,
    },
    {
        headerName: "Service",
        field: "serviceId",
        width: 170,
        filterAllowed: true,
        filter: "agSetColumnFilter",
        sortable: true,
        cellRenderer: AppCell,
        cellStyle: FLEX_CELL,
    },
];

const NO_FILTER = { filterAllowed: false, filter: false, sortable: false };

const topicCol = (headerName, isTopic = true) => ({
    headerName,
    field: "topicHierarchy",
    width: 240,
    cellRenderer: TopicCell,
    cellRendererParams: { isTopic },
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

// Prompt preview for admins, falling back to the row's id for everyone else: `idField`
// names the field, or pass `idOf` in cellRendererParams when the id depends on the row.
const previewCol = (headerName, idField) => ({
    headerName,
    field: "_promptText",
    flex: 1,
    minWidth: 340,
    cellRenderer: PreviewCell,
    cellRendererParams: { idField },
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

// Rows are identified by their id — prompt content is never rendered in a list.
const leadIdCol = (headerName, field) => ({
    headerName,
    field,
    flex: 1,
    minWidth: 300,
    cellRenderer: IdCell,
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

const idCol = (headerName, field, width = 180) => ({
    headerName,
    field,
    width,
    minWidth: width,
    cellRenderer: IdCell,
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

const tokensCol = {
    headerName: "Tokens in / out",
    field: "_tokens",
    width: 150,
    cellRenderer: TokensCell,
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
};

const durationCol = {
    headerName: "Duration",
    field: "durationMs",
    width: 110,
    cellRenderer: DurationCell,
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
};


const timeCol = (field, headerName = "Time") => ({
    headerName,
    field,
    width: 150,
    cellRenderer: TimeCell,
    sort: "desc",
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

const countCol = (headerName, field, width = 90) => ({
    headerName,
    field,
    width,
    cellRenderer: CountCell,
    cellStyle: { ...FLEX_CELL, justifyContent: "flex-end" },
    ...NO_FILTER,
});

// Set-filter + sort, for the paged tables whose backend can filter on the column.
const SET_FILTER = { filter: "agSetColumnFilter", filterAllowed: true, sortable: true };

const userCol = (width, extra = NO_FILTER) => ({
    headerName: "User",
    field: "userName",
    width,
    cellRenderer: UserCell,
    cellStyle: FLEX_CELL,
    ...extra,
});

const appCol = (width, extra = NO_FILTER) => ({
    headerName: "Application",
    field: "serviceId",
    width,
    cellRenderer: AppCell,
    cellStyle: FLEX_CELL,
    ...extra,
});

const guardrailCol = (extra = NO_FILTER) => ({
    headerName: "Guardrail",
    field: "hasActiveGuardrail",
    width: 140,
    cellRenderer: GuardrailStatusCell,
    cellStyle: FLEX_CELL,
    ...extra,
});

const policyCol = (width, extra = NO_FILTER) => ({
    headerName: "Policy Triggered",
    field: "guardrailPolicies",
    width,
    cellRenderer: GuardrailPolicyCell,
    cellStyle: FLEX_CELL,
    ...extra,
});

// A session's total duration can be hours — no alarming colour, plain text only.
const sessionDurationCol = {
    headerName: "Duration",
    field: "durationMs",
    width: 110,
    valueFormatter: p => formatDurationMs(p.value),
    cellStyle: { ...FLEX_CELL, fontSize: 12, color: "#202223" },
    ...NO_FILTER,
};

// Absolute short date-time ("11/23/26, 4:28 PM"), for tables that mix rows of different ages.
const shortTimeCol = (field, headerName = "Time") => ({
    headerName,
    field,
    width: 160,
    sort: "desc",
    cellStyle: FLEX_CELL,
    valueFormatter: p => {
        const ts = p.value;
        if (!ts) return "-";
        const ms = ts > 1e10 ? ts : ts * 1000;
        return new Date(ms).toLocaleString("en-US", {
            month: "numeric", day: "numeric", year: "2-digit",
            hour: "numeric", minute: "2-digit", hour12: true,
        });
    },
    ...NO_FILTER,
});

// Sessions table — one row per session (traces grouped on session id).
export const SESSION_COLUMN_DEFS = [
    leadIdCol("Session", "sessionIdentifier"),
    userCol(150, SET_FILTER),
    {
        headerName: "Model",
        field: "_models",
        width: 200,
        cellRenderer: ModelsCell,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    appCol(160, SET_FILTER),
    countCol("Traces", "messageCount", 85),
    guardrailCol({
        ...SET_FILTER,
        filterParams: { valueFormatter: p => p.value === "true" ? "Guardrail hit" : "Clean" },
    }),
    policyCol(220, { ...SET_FILTER, sortable: false }),
    topicCol("Topics Queried"),
    tokensCol,
    sessionDurationCol,
    timeCol("latestTimestamp", "Last activity"),
];

// Traces table — one row per trace (spans grouped on trace id). `showSession` adds a
// clickable session-id column when the table isn't already scoped to a session.
export function getTraceColumnDefs({ showSession, onSessionClick } = {}) {
    return [
        timeCol("latestTimestamp"),
        previewCol("Trace", "traceId"),
        {
            headerName: "Application",
            field: "serviceId",
            width: 170,
            cellRenderer: AppCell,
            cellStyle: FLEX_CELL,
            ...NO_FILTER,
        },
        {
            headerName: "Model",
            field: "_model",
            width: 170,
            cellRenderer: ModelChipCellRenderer,
            cellStyle: FLEX_CELL,
            ...NO_FILTER,
        },
        {
            headerName: "User",
            field: "userName",
            width: 120,
            cellStyle: FLEX_CELL,
            ...NO_FILTER,
        },
        countCol("Spans", "spanCount", 80),
        {
            headerName: "Guardrail",
            field: "hasActiveGuardrail",
            width: 140,
            cellRenderer: GuardrailStatusCell,
            cellStyle: FLEX_CELL,
            filter: "agSetColumnFilter",
        },
        topicCol("Topics Queried"),
        topicCol("SubTopic queried", false),
        tokensCol,
        durationCol,
        ...(showSession ? [{
            headerName: "Session",
            field: "sessionIdentifier",
            width: 160,
            cellRenderer: SessionLinkCell,
            cellRendererParams: { onSessionClick },
            cellStyle: FLEX_CELL,
            ...NO_FILTER,
        }] : []),
        idCol("Trace ID", "traceId"),
    ];
}

// Argus Spans tab — one row per record, sessioned or not: the Argus traces table as it was
// before session grouping, with its lead column named for what each row is.
export const ARGUS_SPAN_COL_DEFS = [
    shortTimeCol("latestTimestamp"),
    // Each row is one span. Non-admins see its id instead of the prompt; records from before
    // span ids were assigned at ingest have none, so they fall back to their trace id.
    { ...previewCol("Span"), cellRendererParams: { idOf: d => d.spanId || d.traceId } },
    appCol(170),
    topicCol("Topics Queried"),
    topicCol("SubTopic queried", false),
    tokensCol,
    durationCol,
    idCol("Session ID", "sessionIdentifier", 300),
];

// Argus unified table — one row per conversation, plus one row per invocation that never
// carried a session. The two halves partition the data server-side, so nothing is listed
// twice. Sorting is deliberately absent: the rows come from two merged streams and time is
// the only key both are ordered by, so a click-to-sort on any other column would silently
// reorder one half against the other.
export const ARGUS_UNIFIED_COL_DEFS = [
    {
        headerName: "Session / Span",
        field: "sessionIdentifier",
        width: 320,
        minWidth: 260,
        cellRenderer: UnifiedIdCell,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    // The opening prompt of a conversation, or the only prompt of a lone invocation - the
    // column people actually scan. Locked rows fall back to whichever id the row has.
    { ...previewCol("Prompt"), minWidth: 300, cellRendererParams: { idOf: unifiedRowId } },
    shortTimeCol("latestTimestamp"),
    appCol(170),
    userCol(160),
    countCol("Traces", "messageCount", 85),
    guardrailCol(),
    policyCol(200),
    topicCol("Topics Queried"),
    topicCol("SubTopic queried", false),
    tokensCol,
    sessionDurationCol,
];

// Messages table — flat span-level rows.
export const MESSAGE_FLAT_COLUMN_DEFS = [
    leadIdCol("Trace", "traceId"),
    ...userServiceCols,
    {
        headerName: "Model",
        field: "_model",
        width: 170,
        cellRenderer: ModelChipCellRenderer,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    topicCol("Topics Queried"),
    topicCol("SubTopic queried", false),
    tokensCol,
    timeCol("timestamp"),
];
