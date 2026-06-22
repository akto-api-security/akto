import {
    AppCell,
    CostCell,
    CountCell,
    DurationCell,
    IdCell,
    ModelChipCellRenderer,
    ModelsCell,
    SessionLinkCell,
    TimeCell,
    TitleCell,
    TokensCell,
    UserCell,
} from "./LLMCellRenderers";
import { formatDurationMs } from "./constants";

// AG Grid column definitions for the three LLM Observability tables. Kept in a .jsx
// file (separate from constants.js) because they reference React cell renderers.
// Status/violations columns are intentionally absent — no data source for them.

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

const titleCol = (headerName) => ({
    headerName,
    field: "_promptText",
    flex: 1,
    minWidth: 320,
    cellRenderer: TitleCell,
    cellStyle: FLEX_CELL,
    ...NO_FILTER,
});

const idCol = (headerName, field) => ({
    headerName,
    field,
    width: 180,
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

const costCol = {
    headerName: "Cost",
    field: "_cost",
    width: 90,
    cellRenderer: CostCell,
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

// Sessions table — one row per session (traces grouped on session id).
export const SESSION_COLUMN_DEFS = [
    titleCol("Session"),
    {
        headerName: "User",
        field: "userName",
        width: 150,
        cellRenderer: UserCell,
        cellStyle: FLEX_CELL,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    {
        headerName: "Model",
        field: "_models",
        width: 200,
        cellRenderer: ModelsCell,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    {
        headerName: "Application",
        field: "serviceId",
        width: 160,
        cellRenderer: AppCell,
        cellStyle: FLEX_CELL,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    countCol("Traces", "messageCount", 85),
    tokensCol,
    costCol,
    {
        // Session total duration can be hours — no alarming colour, plain text only.
        headerName: "Duration",
        field: "durationMs",
        width: 110,
        valueFormatter: p => formatDurationMs(p.value),
        cellStyle: { ...FLEX_CELL, fontSize: 12, color: "#202223" },
        ...NO_FILTER,
    },
    timeCol("latestTimestamp", "Last activity"),
    idCol("Session ID", "sessionIdentifier"),
];

// Traces table — one row per trace (spans grouped on trace id). `showSession` adds a
// clickable session-id column when the table isn't already scoped to a session.
export function getTraceColumnDefs({ showSession, onSessionClick } = {}) {
    return [
        timeCol("latestTimestamp"),
        titleCol("Trace"),
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
        tokensCol,
        durationCol,
        costCol,
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

// Argus traces table — no user/session/spans/traceId columns, short time format.
export const ARGUS_TRACE_COL_DEFS = [
    {
        headerName: "Time",
        field: "latestTimestamp",
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
    },
    titleCol("Trace"),
    {
        headerName: "Application",
        field: "serviceId",
        width: 170,
        cellRenderer: AppCell,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    // {
    //     headerName: "Model",
    //     field: "_model",
    //     width: 170,
    //     cellRenderer: ModelChipCellRenderer,
    //     cellStyle: FLEX_CELL,
    //     ...NO_FILTER,
    // },
    tokensCol,
    durationCol,
    costCol,
];

// Messages table — flat span-level rows.
export const MESSAGE_FLAT_COLUMN_DEFS = [
    titleCol("Message"),
    ...userServiceCols,
    {
        headerName: "Model",
        field: "_model",
        width: 170,
        cellRenderer: ModelChipCellRenderer,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    tokensCol,
    timeCol("timestamp"),
    idCol("Trace ID", "traceId"),
];
