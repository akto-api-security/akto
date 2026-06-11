import React, { useCallback, useEffect, useReducer, useState } from "react";
import { produce } from "immer";
import {
    Avatar,
    Badge,
    Box,
    Card,
    HorizontalGrid,
    HorizontalStack,
    Text,
    VerticalStack,
} from "@shopify/polaris";

import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import DonutChart from "@/apps/dashboard/components/shared/DonutChart";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";
import AgenticStatsCard from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import AgenticTopListCard from "@/apps/dashboard/pages/observe/agentic/AgenticTopListCard";
import { SeverityBadge } from "@/apps/dashboard/pages/observe/agentic/AgenticCellRenderers";
import AssetIcon from "@/apps/dashboard/pages/observe/agentic/AssetIcon";
import func from "@/util/func";
import values from "@/util/values";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";

import ViolationFlyout from "./ViolationFlyout";
import {
    OPEN_VIOLATIONS_SUMMARY,
    TOP_POLICIES,
    TOP_USERS,
    TOTAL_VIOLATIONS_SUMMARY,
    VIOLATIONS_BY_TYPE,
    VIOLATION_ROWS,
} from "./violationsData";

// ─── Type badge — colours a Polaris <Badge> via the .agentic-type-* classes ─────
// (style.css). Same technique TestRunResultFlyout uses for severity; no inline CSS.
const TYPE_CLASS_MAP = {
    Prompt: "agentic-type-PROMPT",
    Skill: "agentic-type-SKILL",
    Config: "agentic-type-CONFIG",
    "Tool Call": "agentic-type-TOOL",
    LLMs: "agentic-type-LLMS",
};

// ─── Cell renderers ─────────────────────────────────────────────────────────────
// AG Grid sandboxes its DOM, so cell renderers are the documented inline-style
// exception. Each is a small named component (no inline logic in cellRenderer:).

function OsIcon({ os, size = 16 }) {
    if (os === "mac")     return <img src="/public/os-mac.svg"     width={size} height={size} alt="macOS"   style={{ flexShrink: 0 }} />;
    if (os === "windows") return <img src="/public/os-windows.svg" width={size} height={size} alt="Windows" style={{ flexShrink: 0 }} />;
    return                       <img src="/public/os-linux.svg"   width={size} height={size} alt="Linux"   style={{ flexShrink: 0 }} />;
}

function DetectedCellRenderer({ value }) {
    if (value == null) return null;
    return <Text variant="bodySm">{func.epochToDateTime(value)}</Text>;
}

function ViolationCellRenderer({ value }) {
    if (!value) return null;
    return <Text variant="bodyMd" fontWeight="medium" truncate>{value}</Text>;
}

function TypeCellRenderer({ value }) {
    if (!value) return null;
    return (
        <Box as="span" className={TYPE_CLASS_MAP[value] || "agentic-type-DEFAULT"}>
            <Badge size="small">{value}</Badge>
        </Box>
    );
}

function AssetCellRenderer({ value, data }) {
    if (!value) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type={data?.assetType} assetTagValue={data?.assetDomain} size={20} />
            <Box minWidth="0" overflowX="hidden">
                <Text variant="bodySm" truncate>{value}</Text>
            </Box>
        </HorizontalStack>
    );
}

function SeverityCellRenderer({ value }) {
    if (!value) return null;
    return <SeverityBadge severity={value} />;
}

function UserCellRenderer({ value, data }) {
    if (!value) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <OsIcon os={data?.os} />
            <Text variant="bodySm" truncate>{value}</Text>
        </HorizontalStack>
    );
}

function ActionCellRenderer({ value }) {
    if (!value) return null;
    const status = value === "Blocked" ? "critical" : undefined;
    return <Badge size="small" status={status}>{value}</Badge>;
}

// ─── Column definitions ─────────────────────────────────────────────────────────

// Severity ordering for the default sort: Critical → High → Medium → Low.
const SEVERITY_RANK = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
const severityComparator = (a, b) => (SEVERITY_RANK[a] ?? 99) - (SEVERITY_RANK[b] ?? 99);

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: true,
    cellStyle: { display: "flex", alignItems: "center" },
};

// Columns hug their content via autoSizeStrategy (fitCellContents) on the grid —
// no flex stretching. minWidth is just a small floor so headers/filters stay usable.
const COL_DEFS = [
    {
        field: "detected",
        headerName: "Detected",
        minWidth: 150,
        filter: false,
        cellRenderer: DetectedCellRenderer,
    },
    {
        field: "type",
        headerName: "Type",
        minWidth: 100,
        filter: "agSetColumnFilter",
        cellRenderer: TypeCellRenderer,
    },
    {
        field: "violation",
        headerName: "Violation",
        minWidth: 200,
        cellRenderer: ViolationCellRenderer,
    },
    {
        field: "severity",
        headerName: "Severity",
        minWidth: 110,
        sort: "asc",
        comparator: severityComparator,
        filter: "agSetColumnFilter",
        cellRenderer: SeverityCellRenderer,
    },
    {
        field: "user",
        headerName: "User",
        minWidth: 140,
        cellRenderer: UserCellRenderer,
    },
    {
        field: "agenticAsset",
        headerName: "Agentic Asset",
        minWidth: 160,
        cellRenderer: AssetCellRenderer,
    },
    {
        field: "action",
        headerName: "Actions",
        minWidth: 110,
        filter: "agSetColumnFilter",
        cellRenderer: ActionCellRenderer,
    },
];

const AUTO_SIZE_STRATEGY = { type: "fitCellContents" };

// ─── Leaderboard rows → AgenticTopListCard format ───────────────────────────────

function buildTopListRows(items) {
    return items.map((item) => ({
        id: item.id,
        name: item.name,
        type: item.type,
        renderValue: () => (
            <HorizontalStack gap="3" blockAlign="center" align="end" wrap={false}>
                <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>
                <SmoothAreaChart tickPositions={item.sparkline} color="#EF4444" height={28} width={90} enableHover />
            </HorizontalStack>
        ),
    }));
}

// ─── Dashboard summary section ──────────────────────────────────────────────────

function ViolationsDashboard({ severityFilter, onSeverityFilter }) {
    return (
        <VerticalStack gap="4">
            <HorizontalGrid columns={2} gap="4">
                <AgenticStatsCard
                    title="Total Violations"
                    total={TOTAL_VIOLATIONS_SUMMARY.total}
                    delta={TOTAL_VIOLATIONS_SUMMARY.delta}
                    deltaColor="subdued"
                    sparklineCounts={TOTAL_VIOLATIONS_SUMMARY.sparkline}
                    sparklineColor="#EF4444"
                    breakdown={TOTAL_VIOLATIONS_SUMMARY.breakdown}
                    onFilterClick={onSeverityFilter}
                    activeFilter={severityFilter}
                />
                <AgenticStatsCard
                    title="Open Violations"
                    total={OPEN_VIOLATIONS_SUMMARY.total}
                    delta={OPEN_VIOLATIONS_SUMMARY.delta}
                    deltaColor="subdued"
                    sparklineCounts={OPEN_VIOLATIONS_SUMMARY.sparkline}
                    sparklineColor="#EF4444"
                    breakdown={OPEN_VIOLATIONS_SUMMARY.breakdown}
                />
            </HorizontalGrid>

            <HorizontalGrid columns={3} gap="4">
                <AgenticTopListCard
                    title="Violations by Top Users"
                    columns={[{ label: "User" }, { label: "Violations" }]}
                    rows={buildTopListRows(TOP_USERS)}
                    renderIcon={() => <Avatar customer size="extraSmall" />}
                />
                <AgenticTopListCard
                    title="Top Policies Triggered"
                    columns={[{ label: "Policy" }, { label: "Count" }]}
                    rows={buildTopListRows(TOP_POLICIES)}
                    renderIcon={() => null}
                />
                <Card padding="0">
                    <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                        <Text variant="headingSm">Violations by Type</Text>
                    </Box>
                    <Box padding="4">
                        <HorizontalStack gap="4" blockAlign="center" align="center" wrap={false}>
                            <DonutChart data={VIOLATIONS_BY_TYPE} size={160} pieInnerSize="65%" />
                            <VerticalStack gap="2">
                                {Object.entries(VIOLATIONS_BY_TYPE).map(([label, seg]) => (
                                    <HorizontalStack key={label} gap="2" blockAlign="center" wrap={false}>
                                        <Box className="agentic-dot" style={{ "--dot-color": seg.color }} />
                                        <Text variant="bodySm" color="subdued">{label}</Text>
                                    </HorizontalStack>
                                ))}
                            </VerticalStack>
                        </HorizontalStack>
                    </Box>
                </Card>
            </HorizontalGrid>
        </VerticalStack>
    );
}

// ─── Page ───────────────────────────────────────────────────────────────────────

function Violations() {
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [severityFilter, setSeverityFilter] = useState(new Set());

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[4],
    );

    useEffect(() => {
        setLoading(true);
        try {
            setRows(VIOLATION_ROWS);
        } finally {
            setLoading(false);
        }
    }, []);

    const handleSeverityFilter = useCallback((key) => {
        setSeverityFilter((prev) => {
            const next = new Set(prev);
            next.has(key) ? next.delete(key) : next.add(key);
            return next;
        });
    }, []);

    const filteredRows = severityFilter.size > 0
        ? rows.filter((r) => severityFilter.has(r.severity))
        : rows;

    const handleRowClick = (e) => {
        if (e?.data) setSelectedViolation(e.data);
    };

    const tableComponent = (
        <div key="table" style={{ display: "flex", flexDirection: "column", minHeight: 480 }}>
            <AgGridTable
                rowData={filteredRows}
                columnDefs={COL_DEFS}
                defaultColDef={DEFAULT_COL_DEF}
                autoSizeStrategy={AUTO_SIZE_STRATEGY}
                searchPlaceholder="Search violations"
                onRowClicked={handleRowClick}
                suppressRowClickSelection
                getRowStyle={() => ({ cursor: "pointer" })}
                pagination
                paginationPageSize={20}
                paginationPageSizeSelector={[20, 50, 100]}
                domLayout="autoHeight"
            />
        </div>
    );

    const components = loading
        ? [<SpinnerCentered key="loading" />]
        : [
            <ViolationsDashboard key="dashboard" severityFilter={severityFilter} onSeverityFilter={handleSeverityFilter} />,
            tableComponent,
            <ViolationFlyout
                key="flyout"
                violation={selectedViolation}
                show={selectedViolation !== null}
                onClose={() => setSelectedViolation(null)}
            />,
        ];

    const dateFilter = (
        <DateRangeFilter
            initialDispatch={currDateRange}
            dispatch={(dateObj) =>
                dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
            }
        />
    );

    return <PageWithMultipleCards title="Violations" isFirstPage secondaryActions={dateFilter} components={components} />;
}

export default Violations;
