import { useState, useMemo, useReducer, useEffect, useCallback, useRef } from "react";
import { Badge, Tabs } from "@shopify/polaris";
import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import AgGridTable from "../../components/tables/AgGridTable";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";
import { IdentityIcon, AgentIcon, sevBadge, PolicyCell, SEV_ORD } from "./nhiViolationsData";
import { getFirstIdentityName, getAllIdentityNames } from "./identityHelper";
import { formatRelativeTime } from "./nhiUtils";
import ViolationDetailsPanel from "./ViolationDetailsPanel";
import observeRequests from "../observe/api";
import SpinnerCentered from "../../components/progress/SpinnerCentered";
import JiraTicketCreationModal from "../../components/shared/JiraTicketCreationModal.jsx";
import issuesFunctions from "@/apps/dashboard/pages/issues/module";
import settingFunctions from "@/apps/dashboard/pages/settings/module";

const definedTableTabs = ["All", "Open", "Fixed"];

const SEVERITY_COLORS = {
    Critical: "#DF2909",
    High:     "#FED3D1",
    Medium:   "#FFD79D",
    Low:      "#E4E5E7",
};

function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <Box style={{ width:10, height:10, borderRadius:"50%", background:color, flexShrink:0 }} />
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

function DonutCard({ title, donutData }) {
    const legendItems = Object.entries(donutData).map(([label, { text, color }]) => ({ label, color, count: text }));
    return (
        <InfoCard title={title} component={
            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                <DonutChart data={donutData} title="" size={150} pieInnerSize="55%" />
                <ChartLegend items={legendItems} />
            </HorizontalStack>
        } />
    );
}

// ─── AG Grid column definitions ─────────────────────────────────────────────────
// Every cell just renders the pre-built React node computed for that field in
// mapViolationToRow — keeps a single source of truth for cell markup instead of
// re-deriving it from raw values here.
function JsxCellRenderer({ value }) {
    return value ?? null;
}

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: false,
    cellStyle: { display: "flex", alignItems: "center" },
};

const AUTO_SIZE_STRATEGY = { type: "fitCellContents" };

// AG Grid colId -> backend sortKey ("detected"|"severity"|"status"|"agent"|"violationType")
const SORT_FIELD_MAP = {
    violationComp: "violationType",
    agentComp: "agent",
    severityComp: "severity",
    discovered: "detected",
};

const colDefs = [
    { field: "violationComp", headerName: "Violation", minWidth: 200, cellRenderer: JsxCellRenderer },
    { field: "identityComp", headerName: "Identity", minWidth: 180, sortable: false, cellRenderer: JsxCellRenderer },
    { field: "agentComp", headerName: "Agentic Asset", minWidth: 180, cellRenderer: JsxCellRenderer },
    // Defaults to desc so the grid's initial (unsorted-by-user) request already asks the
    // backend for a true global severity sort — matches the page's historical default view.
    { field: "severityComp", headerName: "Severity", minWidth: 120, sort: "desc", cellRenderer: JsxCellRenderer },
    { field: "policyComp", headerName: "Policy", minWidth: 200, sortable: false, cellRenderer: JsxCellRenderer },
    { field: "discovered", headerName: "Discovered", minWidth: 140, cellRenderer: JsxCellRenderer },
];

// Builds one table row from a raw violation doc returned by the server. Mirrors
// nhiViolationsData.jsx's transformApiViolations() field-for-field, but — unlike that
// helper — does NOT re-sort the array by severity afterwards: transformApiViolations's
// baked-in client sort is fine for its other caller (IdentityDetailsPanel, which loads a
// small unpaginated set) but would silently scramble whatever order/page the server was
// asked for here (e.g. sort-by-"Discovered"), so this page keeps its own thin mapper.
function mapViolationToRow(v) {
    const violationHexId = v.hexId || v.id;
    const policyObj = v.policy && Array.isArray(v.policy)
        ? {
            primary: v.policy[0] || "N/A",
            extra: Math.max(0, v.policy.length - 1),
            extras: v.policy.slice(1) || [],
          }
        : v.policy;
    const firstIdentityName = getFirstIdentityName(v.identities);
    const allIdentityNames = getAllIdentityNames(v.identities);
    const extraCount = allIdentityNames.length - 1;
    return {
        ...v,
        id: violationHexId,
        violation: v.violationType,
        identity: firstIdentityName,
        identities: v.identities,
        discovered: formatRelativeTime(v.discoveredAt, "Unknown"),
        severityOrder: SEV_ORD[v.severity] || 0,
        policy: policyObj,
        violationComp: <Text variant="bodyMd" fontWeight="medium">{v.violationType}</Text>,
        identityComp: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <IdentityIcon name={firstIdentityName} />
                <Text variant="bodyMd">{firstIdentityName}</Text>
                {extraCount > 0 && (
                    <Badge>{`+${extraCount}`}</Badge>
                )}
            </HorizontalStack>
        ),
        agentComp: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <AgentIcon name={v.agentName} />
                <Text variant="bodyMd">{v.agentName}</Text>
            </HorizontalStack>
        ),
        severityComp: sevBadge(v.severity),
        policyComp: <PolicyCell policy={policyObj} />,
    };
}

function transformViolationsPage(apiViolations) {
    return (apiViolations || []).map(mapViolationToRow);
}

// ── Page ───────────────────────────────────────────────────────────────────────
const violationsPageTitle = (
    <HorizontalStack gap="2" blockAlign="center">
        <TitleWithInfo
            titleText="Violations"
            tooltipContent="Policy violations detected across all non-human identities used by your AI agents."
            docsUrl="https://ai-security-docs.akto.io/nhi-governance/violations"
        />
        <Badge status="info">Beta</Badge>
    </HorizontalStack>
);

export default function ViolationsPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "open";

    // Table state
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [bulkSelectedCount, setBulkSelectedCount] = useState(0);
    const [tableRefreshKey, setTableRefreshKey] = useState(0);
    const gridRef = useRef(null);

    // Stats (donut + line chart + tab counts) — fetched independently of the table's rows
    const [stats, setStats] = useState({});
    const [statsLoading, setStatsLoading] = useState(true);

    // UI state
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [showViolationPanel, setShowViolationPanel] = useState(false);

    // Bulk action state
    const [jiraModalActive, setJiraModalActive] = useState(false);
    const [bulkViolationIds, setBulkViolationIds] = useState([]);
    const [projId, setProjId] = useState("");
    const [issueType, setIssueType] = useState("");
    const [labelsText, setLabelsText] = useState("");
    const [jiraProjectMap, setJiraProjectMap] = useState({});

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const startTimestamp = parseInt(currDateRange.period.since.getTime() / 1000);
    const endTimestamp = parseInt(currDateRange.period.until.getTime() / 1000);

    const triggerTableRefresh = useCallback(() => setTableRefreshKey((k) => k + 1), []);

    const loadStats = useCallback(async () => {
        setStatsLoading(true);
        try {
            const result = await observeRequests.fetchNhiViolationsStats(startTimestamp, endTimestamp);
            setStats(result || {});
        } catch (err) {
            console.error("Error fetching violation stats:", err);
            setStats({});
        } finally {
            setStatsLoading(false);
        }
    }, [startTimestamp, endTimestamp]);

    useEffect(() => {
        loadStats();
    }, [loadStats]);

    // Refresh both the grid (re-fetches current page from the server) and the stats
    // (donut/chart/tab-counts) after any mutation — replaces the old fetchViolations()
    // which used to reload the single client-side array that fed everything.
    const refreshAll = useCallback(() => {
        triggerTableRefresh();
        loadStats();
    }, [triggerTableRefresh, loadStats]);

    useEffect(() => {
        const pending = sessionStorage.getItem("nhi_pending_violation");
        if (pending) {
            sessionStorage.removeItem("nhi_pending_violation");
            try {
                setSelectedViolation(JSON.parse(pending));
                setShowViolationPanel(true);
            } catch (_) {}
        }
    }, []);

    // Severity donut — server-computed, OPEN (non-Fixed) violations only, for the whole date range
    const severityDonutData = useMemo(() => {
        const counts = stats.bySeverityOpen || {};
        return {
            Critical: { text: counts.Critical || 0, color: SEVERITY_COLORS.Critical },
            High:     { text: counts.High || 0,     color: SEVERITY_COLORS.High },
            Medium:   { text: counts.Medium || 0,   color: SEVERITY_COLORS.Medium },
            Low:      { text: counts.Low || 0,      color: SEVERITY_COLORS.Low },
        };
    }, [stats]);

    // Violations-over-time line chart — server-computed day buckets across the whole date range
    const violationsOverTimeData = useMemo(() => {
        const byDay = stats.byDay || {};
        const entries = Object.entries(byDay);
        if (entries.length === 0) return [];
        const chartData = entries
            .sort(([dateA], [dateB]) => dateA.localeCompare(dateB))
            .map(([dateStr, count]) => [new Date(dateStr + "T00:00:00Z").getTime(), count]);
        return [{ data: chartData, color: "#EF4444", name: "Violations" }];
    }, [stats]);

    // Tab badge counts — derived from server-computed byStatus, not from a client array length
    const tabCountObj = useMemo(() => {
        const byStatus = stats.byStatus || {};
        const fixed = byStatus.Fixed || 0;
        const total = Object.values(byStatus).reduce((sum, n) => sum + (n || 0), 0);
        return { all: total, open: total - fixed, fixed };
    }, [stats]);

    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tabCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );
    const selectedTabIndex = Math.max(0, definedTableTabs.findIndex((t) => func.getKeyFromName(t) === selectedTab));

    // ─── Server-side data fetch for AG Grid ─────────────────────────────────────
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const pageSize = limit || 50;
        const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "severity";
        // AgGridTable/AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc (opposite of the
        // backend's Mongo convention: 1 asc / -1 desc) — same translation the guardrails
        // ViolationsPage.jsx onServerFetch uses.
        const mongoSortOrder = sortOrder ? -sortOrder : -1;
        const status = selectedTab === "all" ? undefined : (selectedTab === "fixed" ? "Fixed" : "Open");

        return observeRequests.fetchAllNhiViolations(startTimestamp, endTimestamp, {
            skip,
            limit: pageSize,
            sortKey: mappedSortKey,
            sortOrder: mongoSortOrder,
            queryValue: searchString || undefined,
            status,
        }).then((res) => ({
            value: transformViolationsPage(res.violations),
            total: res.total || 0,
        }));
    }, [startTimestamp, endTimestamp, selectedTab]);

    // ─── Bulk selection (SSRM tracks selection via node state, not getSelectedRows()) ────
    const getSelectedIds = useCallback(() => {
        const ids = [];
        gridRef.current?.api?.forEachNode((node) => {
            if (!node.stub && node.isSelected() && node.data?.id) ids.push(node.data.id);
        });
        return ids;
    }, []);

    const clearBulkSelection = useCallback(() => {
        gridRef.current?.api?.deselectAll();
        setBulkSelectedCount(0);
    }, []);

    const handleBulkMarkAsFixed = useCallback(async () => {
        const ids = getSelectedIds();
        if (!ids.length) return;
        try {
            await Promise.all(ids.map((id) => observeRequests.markViolationAsFixed(id)));
            func.setToast(true, false, `${ids.length} violation${ids.length > 1 ? "s" : ""} marked as fixed`);
            clearBulkSelection();
            refreshAll();
        } catch (err) {
            func.setToast(true, true, "Failed to mark violations as fixed");
        }
    }, [getSelectedIds, clearBulkSelection, refreshAll]);

    const handleOpenBulkJiraModal = useCallback(() => {
        const ids = getSelectedIds();
        setBulkViolationIds(ids);
        settingFunctions.fetchJiraIntegration().then((jiraIntegration) => {
            if (jiraIntegration.projectIdsMap !== null && Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                setJiraProjectMap(jiraIntegration.projectIdsMap);
                setProjId(Object.keys(jiraIntegration.projectIdsMap)[0]);
            } else {
                setProjId(jiraIntegration.projId);
                setIssueType(jiraIntegration.issueType);
            }
            setJiraModalActive(true);
        });
    }, [getSelectedIds]);

    const handleSaveBulkJira = async (issueId, labels) => {
        let jiraMetaData;
        try {
            jiraMetaData = issuesFunctions.prepareAdditionalIssueFieldsJiraMetaData(projId, issueType);
            if (labels !== undefined && labels && labels.trim()) {
                jiraMetaData.labels = labels.trim();
            }
        } catch (error) {
            return;
        }

        setJiraModalActive(false);
        try {
            await Promise.all(bulkViolationIds.map((id) =>
                observeRequests.createJiraTicketFromViolation(id, window.location.origin, projId, issueType, jiraMetaData)
            ));
            func.setToast(true, false, `Jira ticket${bulkViolationIds.length > 1 ? "s" : ""} created successfully`);
            clearBulkSelection();
            refreshAll();
        } catch (err) {
            func.setToast(true, true, "Failed to create Jira ticket");
        }
    };

    const bulkActions = useMemo(() => [
        { label: "Mark as fixed", onAction: handleBulkMarkAsFixed },
        { label: "Open Jira ticket", onAction: handleOpenBulkJiraModal },
    ], [handleBulkMarkAsFixed, handleOpenBulkJiraModal]);

    const handleRowClick = useCallback((e) => {
        if (e?.data) {
            setSelectedViolation(e.data);
            setShowViolationPanel(true);
        }
    }, []);

    return (
        <>
        <PageWithMultipleCards
            title={violationsPageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                <Box key="charts" style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "16px" }}>
                    <InfoCard
                        title="Violations over time"
                        component={
                            statsLoading ? (
                                <Box style={{ height: 220, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                    <SpinnerCentered />
                                </Box>
                            ) : violationsOverTimeData.length > 0 ? (
                                <LineChart
                                    data={violationsOverTimeData}
                                    type="line"
                                    height={220}
                                    text={true}
                                    showGridLines={true}
                                    exportingDisabled={true}
                                    defaultChartOptions={{
                                        xAxis: {
                                            type: "datetime",
                                            dateTimeLabelFormats: { day: "%a" },
                                            title: { text: null },
                                            visible: true,
                                            gridLineWidth: 0,
                                        },
                                        yAxis: {
                                            title: { text: "Violations" },
                                            gridLineWidth: 1,
                                            min: 0,
                                        },
                                        legend: { enabled: true },
                                    }}
                                />
                            ) : (
                                <Box style={{ height: 220, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                    <Text variant="bodyMd" color="subdued">No violations in the selected time range</Text>
                                </Box>
                            )
                        }
                    />
                    <DonutCard title="Violations by severity" donutData={severityDonutData} />
                </Box>,

                <Box key="violations-table">
                    <Box paddingBlockEnd="3">
                        <Tabs
                            tabs={tableTabs}
                            selected={selectedTabIndex}
                            onSelect={(index) => tableTabs[index]?.onAction?.()}
                        />
                    </Box>
                    <AgGridTable
                        key={`nhi-violations-grid-${selectedTab}-${startTimestamp}-${endTimestamp}-${tableRefreshKey}`}
                        columnDefs={colDefs}
                        defaultColDef={DEFAULT_COL_DEF}
                        autoSizeStrategy={AUTO_SIZE_STRATEGY}
                        searchPlaceholder="Search violations"
                        onRowClicked={handleRowClick}
                        suppressRowClickSelection
                        getRowStyle={() => ({ cursor: "pointer" })}
                        gridRef={gridRef}
                        rowSelection={{
                            mode: "multiRow",
                            checkboxes: true,
                            headerCheckbox: true,
                            enableClickSelection: false,
                        }}
                        onSelectionChanged={(e) => {
                            let count = 0;
                            e.api.forEachNode((node) => { if (!node.stub && node.isSelected()) count++; });
                            setBulkSelectedCount(count);
                        }}
                        bulkActionCount={bulkSelectedCount}
                        bulkActions={bulkActions}
                        onClearBulk={clearBulkSelection}
                        paginationPageSize={50}
                        paginationPageSizeSelector={[20, 50, 100]}
                        height={500}
                        domLayout="normal"
                        onServerFetch={onServerFetch}
                        filterStateUrl="nhi-violations-grid"
                        serverSideRowModel
                        getRowId={(params) => params.data.id}
                    />
                </Box>,
            ]}
        />
        {selectedViolation && (
            <ViolationDetailsPanel
                row={selectedViolation}
                show={showViolationPanel}
                setShow={setShowViolationPanel}
                onUpdated={refreshAll}
            />
        )}
        <JiraTicketCreationModal
            activator={<div />}
            modalActive={jiraModalActive}
            setModalActive={setJiraModalActive}
            handleSaveAction={handleSaveBulkJira}
            jiraProjectMaps={jiraProjectMap}
            setProjId={setProjId}
            setIssueType={setIssueType}
            projId={projId}
            issueType={issueType}
            labelsText={labelsText}
            setLabelsText={setLabelsText}
        />
        </>
    );
}
