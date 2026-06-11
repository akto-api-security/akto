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
} from "./LLMCellRenderers";

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
        headerName: "Application",
        field: "serviceId",
        width: 190,
        cellRenderer: AppCell,
        cellStyle: FLEX_CELL,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    {
        headerName: "User",
        field: "userName",
        width: 130,
        cellStyle: FLEX_CELL,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    countCol("Traces", "messageCount", 80),
    {
        headerName: "Models",
        field: "_models",
        width: 180,
        cellRenderer: ModelsCell,
        cellStyle: FLEX_CELL,
        ...NO_FILTER,
    },
    tokensCol,
    durationCol,
    costCol,
    timeCol("latestTimestamp", "Last activity"),
    idCol("Session ID", "sessionIdentifier"),
];

// Traces table — one row per trace (spans grouped on trace id). `showSession` adds a
// clickable session-id column when the table isn't already scoped to a session.
export function getTraceColumnDefs({ showSession, onSessionClick } = {}) {
    return [
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
        timeCol("latestTimestamp"),
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
