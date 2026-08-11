import { useState, useEffect, useCallback } from "react";
import { ActionList, Box, Button, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import AgGridTable from "../../components/tables/AgGridTable";
import { IdentityIcon, transformViolationsPage } from "./nhiViolationsData";
import IdentityGraph from "./IdentityGraph";
import observeRequests from "../observe/api";
import func from "@/util/func";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";

// ── AG Grid column definitions (mirrors ViolationsPage.jsx's own colDefs) ──────────
function JsxCellRenderer({ value }) {
    return value ?? null;
}

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: false,
    cellStyle: { display: "flex", alignItems: "center" },
};

// AG Grid colId -> backend sortKey (NhiGovernanceViolationsAction.mapSortField)
const SORT_FIELD_MAP = {
    violationComp: "violationType",
    agentComp: "agent",
    severityComp: "severity",
    discovered: "detected",
};

const colDefs = [
    { field: "violationComp", headerName: "Violation", minWidth: 180, cellRenderer: JsxCellRenderer },
    { field: "identityComp", headerName: "Identity", minWidth: 160, sortable: false, cellRenderer: JsxCellRenderer },
    { field: "agentComp", headerName: "Agentic Asset", minWidth: 160, cellRenderer: JsxCellRenderer },
    { field: "severityComp", headerName: "Severity", minWidth: 110, sort: "desc", sortIndex: 0, cellRenderer: JsxCellRenderer },
    { field: "policyComp", headerName: "Policy", minWidth: 160, sortable: false, cellRenderer: JsxCellRenderer },
    { field: "discovered", headerName: "Discovered", minWidth: 120, cellRenderer: JsxCellRenderer },
];

export default function IdentityDetailsPanel({ row, show, setShow, onUpdated }) {
    const [actionActive, setActionActive] = useState(false);
    const [total, setTotal] = useState(0);
    const [severityCounts, setSeverityCounts] = useState({});
    const [disabling, setDisabling] = useState(false);

    // Cheap stats-only fetch (limit: 1) — populates the title bubbles/overview description
    // immediately on panel open, before the user ever switches to the Violations tab (whose own
    // AgGridTable, below, only mounts once that tab is actually selected). total/severityCounts
    // are whole-identity aggregates computed server-side regardless of page size, so a limit-1
    // call is just as accurate as fetching everything — see fetchViolationsByIdentity.
    useEffect(() => {
        const fetchStats = async () => {
            try {
                const { total: totalCount, stats } = await observeRequests.fetchViolationsByIdentity(
                    row.hexId, { limit: 1 }
                );
                setTotal(totalCount || 0);
                setSeverityCounts(stats?.bySeverityOpen || {});
            } catch (err) {
                console.error("Error fetching violation stats:", err);
                setTotal(0);
                setSeverityCounts({});
            }
        };

        if (show && row?.hexId) {
            fetchStats();
        }
    }, [show, row?.hexId]);

    const violCrit = severityCounts.Critical || 0;
    const violHigh = severityCounts.High || 0;
    const violMed  = severityCounts.Medium || 0;
    const totalViolations = total;

    // ─── Server-side data fetch for the Violations tab's AG Grid ────────────────
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const pageSize = limit || 20;
        const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "detected";
        // AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc — opposite of the backend's Mongo
        // convention, matching ViolationsPage.jsx's own onServerFetch.
        const mongoSortOrder = sortOrder ? -sortOrder : -1;

        return observeRequests.fetchViolationsByIdentity(row.hexId, {
            skip,
            limit: pageSize,
            sortKey: mappedSortKey,
            sortOrder: mongoSortOrder,
            queryValue: searchString || undefined,
        }).then((res) => {
            setTotal(res.total || 0);
            setSeverityCounts(res.stats?.bySeverityOpen || {});
            return { value: transformViolationsPage(res.violations), total: res.total || 0 };
        });
    }, [row?.hexId]);

    const handleViolationClick = useCallback((e) => {
        if (!e?.data) return;
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(e.data));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
    }, [setShow]);

    const handleDisableIdentity = async () => {
        try {
            setDisabling(true);

            await observeRequests.disableNhiIdentity(row.hexId);

            func.setToast(true, false, "Identity disabled successfully");
            setActionActive(false);
            setShow(false);
            await onUpdated?.();
        } catch (err) {
            func.setToast(true, true, "Failed to disable identity");
        } finally {
            setDisabling(false);
        }
    };

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="start">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center" align="start">
                        <IdentityIcon name={row.identityName} />
                        <Text variant="headingMd" fontWeight="semibold">{row.identityName}</Text>
                        {[
                            { count: violCrit, bg: "#DF2909", fg: "white"   },
                            { count: violHigh, bg: "#FED3D1", fg: "#202223" },
                            { count: violMed,  bg: "#FFD79D", fg: "#202223" },
                        ].map(({ count, bg, fg }) => count > 0 && (
                            <Box key={bg} style={{
                                background: bg, color: fg,
                                borderRadius: "50%", width: 20, height: 20,
                                display: "flex", alignItems: "center",
                                justifyContent: "center", fontSize: 11, fontWeight: 600, flexShrink: 0,
                            }}>{count}</Box>
                        ))}
                    </HorizontalStack>
                    <HorizontalStack gap="2">
                        <Text variant="bodySm" color="subdued">{row.type}</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">{row.access} Access</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">Last Used {row.lastUsed}</Text>
                    </HorizontalStack>
                </VerticalStack>
                <Popover
                    active={actionActive}
                    activator={
                        <Button size="slim" disclosure onClick={() => setActionActive((v) => !v)}>
                            Action
                        </Button>
                    }
                    onClose={() => setActionActive(false)}
                >
                    <ActionList items={[{ content: "Disable identity", destructive: true, onAction: handleDisableIdentity }]} />
                </Popover>
            </HorizontalStack>
        </Box>
    );

    // ── Overview tab ──────────────────────────────────────────────────────────
    const overviewTab = {
        id: "overview",
        content: "Overview",
        component: (
            <Box padding="4">
                <VerticalStack gap="4">
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Graph</Text>
                        <IdentityGraph row={row} />
                    </VerticalStack>
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Description</Text>
                        <Text variant="bodyMd">
                            {(() => {
                                const access = row.access ? `${row.access.toLowerCase()}-level access ` : "";
                                const via = row.type ? `via ${row.type}` : "";
                                const suffix = totalViolations > 0
                                    ? `It currently has ${totalViolations} security violation${totalViolations > 1 ? "s" : ""} that increase the risk of misuse or unauthorized access.`
                                    : "No active security violations detected.";
                                return `This identity is actively used by ${row.agent || "an unknown agent"} with ${access}${via}. ${suffix}`.replace(/\s+/g, " ").trim();
                            })()}
                        </Text>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Violations tab ────────────────────────────────────────────────────────
    const violationsTab = {
        id: "violations",
        content: `Violations ${total > 0 ? total : ""}`.trim(),
        component: total > 0 ? (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="4">
                <AgGridTable
                    key={`identity-violations-grid-${row.hexId}`}
                    columnDefs={colDefs}
                    defaultColDef={DEFAULT_COL_DEF}
                    searchPlaceholder="Search violations"
                    onRowClicked={handleViolationClick}
                    getRowStyle={() => ({ cursor: "pointer" })}
                    paginationPageSize={20}
                    paginationPageSizeSelector={[20, 50, 100]}
                    height={400}
                    domLayout="normal"
                    onServerFetch={onServerFetch}
                    filterStateUrl="identity-violations-grid"
                    serverSideRowModel
                    getRowId={(params) => params.data.id}
                />
            </Box>
        ) : (
            <Box padding="4">
                <Text variant="bodyMd" color="subdued">No violations found for this identity.</Text>
            </Box>
        ),
    };

    return (
        <FlyLayout
            title="Identity details"
            show={show}
            setShow={setShow}
            components={[
                <TitleComponent key="title" />,
                <LayoutWithTabs
                    key={row.identityName}
                    tabs={[overviewTab, violationsTab]}
                    currTab={() => {}}
                    noLoading
                />,
            ]}
            showDivider
            newComp
        />
    );
}
