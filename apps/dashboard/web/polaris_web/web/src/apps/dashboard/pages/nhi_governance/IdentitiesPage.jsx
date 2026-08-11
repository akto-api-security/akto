import { useState, useMemo, useReducer, useEffect, useCallback, useRef } from "react";
import { Badge, Box, HorizontalStack, Modal, Tabs, Text } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import AgGridTable from "../../components/tables/AgGridTable";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";
import { isEndpointSecurityCategory } from "../../../main/labelHelper";
import { formatRelativeTime } from "./nhiUtils";
import IdentityDetailsPanel from "./IdentityDetailsPanel";
import IdentityOverviewGraph from "./IdentityOverviewGraph";
import { IdentityIcon, AgentIcon, ViolationBubbles } from "./nhiViolationsData";
import observeRequests from "../observe/api";
import SpinnerCentered from "../../components/progress/SpinnerCentered";

const definedTableTabs = ["All", "Expired", "Disabled"];

// ── Expiry status renderer ─────────────────────────────────────────────────────
const expiryComp = (s) => {
    if (!s) return null;
    if (s.startsWith("Expired"))
        return <Text variant="bodyMd" color="critical">{s}</Text>;
    if (s === "Rotation due today" || s.startsWith("Rotation Due in"))
        return <Text variant="bodyMd" color="warning" fontWeight="medium">{s}</Text>;
    return <Text variant="bodyMd">{s}</Text>;
};

// Full-dataset row shaping — feeds IdentityOverviewGraph, tab counts, and summary cards, none of
// which render table cells, so no JSX construction needed here (unlike buildGridRow below).
const buildFullDatasetRows = (rawRows, violationIndex = {}) =>
    rawRows.map((r) => {
        const v = violationIndex[r.identityName] || { violCrit: 0, violHigh: 0, violMed: 0 };
        return {
            ...r,
            id: r.hexId,
            violCrit: v.violCrit, violHigh: v.violHigh, violMed: v.violMed,
            totalViolations: v.violCrit + v.violHigh + v.violMed,
        };
    });

// Per-page row shaping for the paginated AG Grid table — builds the JSX cell fields the column
// defs below read (identityComp/agentComp/typeComp/violationsComp/expiryComp).
function buildGridRow(apiIdentity, violationIndex) {
    const r = transformIdentityForUI(apiIdentity);
    const v = violationIndex[r.identityName] || { violCrit: 0, violHigh: 0, violMed: 0 };
    return {
        ...r,
        id: r.hexId,
        violCrit: v.violCrit, violHigh: v.violHigh, violMed: v.violMed,
        totalViolations: v.violCrit + v.violHigh + v.violMed,
        identityComp: <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identityName} /><Text variant="bodyMd" fontWeight="medium">{r.identityName}</Text></HorizontalStack>,
        agentComp: <HorizontalStack gap="2" blockAlign="center" wrap={false}><AgentIcon name={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
        typeComp: <Badge>{r.type}</Badge>,
        violationsComp: <ViolationBubbles critical={v.violCrit} high={v.violHigh} medium={v.violMed} />,
        expiryComp: expiryComp(r.expiryStatus),
    };
}

// Helper to format expiry status (expiryDate is epoch seconds)
const formatExpiryStatus = (expiryDate) => {
    if (!expiryDate) return "No expiry";
    const now = Math.floor(Date.now() / 1000); // Convert to seconds
    const diff = expiryDate - now; // Both in seconds
    const secondsInDay = 60 * 60 * 24;

    if (diff < 0) {
        const days = Math.floor(Math.abs(diff) / secondsInDay);
        return `Expired ${days}d ago`;
    }

    const days = Math.floor(diff / secondsInDay);
    if (days === 0) return "Rotation due today";
    if (days <= 2) return `Rotation Due in ${days}d`;
    return `${days}d left`;
};

// Helper to transform API identity to UI format
const transformIdentityForUI = (apiIdentity) => {
    return {
        hexId: apiIdentity.hexId,
        identityName: apiIdentity.identityName,
        agent: apiIdentity.agentName,
        type: apiIdentity.identityType,
        access: apiIdentity.accessLevel,
        owner: apiIdentity.owner?.name || "N/A",
        lastUsed: formatRelativeTime(apiIdentity.lastUsedAt),
        expiryStatus: formatExpiryStatus(apiIdentity.expiryDate),
        targetResource: apiIdentity.targetResource,
        status: apiIdentity.status,
        discoveredTimestamp: formatRelativeTime(apiIdentity.createdAt, "Unknown"),
    };
};

// ── Computed summary ───────────────────────────────────────────────────────────
const makeSummaryItems = (data) => {
    const total   = data.length;
    const expired = data.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")).length;
    const withV   = data.filter((r) => r.totalViolations > 0).length;
    return [
        { title: "Total Identities",          data: total.toLocaleString()   },
        { title: "Expired Identities",        data: expired.toLocaleString() },
        { title: "Identities with Violations",data: withV.toLocaleString()   },
    ];
};

// ── AG Grid column definitions ──────────────────────────────────────────────────
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

// AG Grid colId -> backend sortKey ("identityName"|"expiryDate"|"lastUsedAt"|"createdAt")
const SORT_FIELD_MAP = {
    identityComp: "identityName",
    expiryComp: "expiryDate",
    discoveredTimestamp: "createdAt",
};

function buildColDefs() {
    return [
        { field: "identityComp", headerName: "Identity", minWidth: 220, cellRenderer: JsxCellRenderer },
        { field: "agentComp", headerName: "Agentic Asset", minWidth: 180, sortable: false, cellRenderer: JsxCellRenderer },
        ...(isEndpointSecurityCategory() ? [{ field: "owner", headerName: "Owner", minWidth: 140, sortable: false }] : []),
        { field: "typeComp", headerName: "Type", minWidth: 120, sortable: false, cellRenderer: JsxCellRenderer },
        { field: "violationsComp", headerName: "Violations", minWidth: 160, sortable: false, cellRenderer: JsxCellRenderer },
        { field: "expiryComp", headerName: "Expiry Status", minWidth: 160, cellRenderer: JsxCellRenderer },
        // Defaults to desc so the grid's initial (unsorted-by-user) request already asks the
        // backend for "most recently discovered first" — see NhiGovernanceIdentitiesAction's
        // fetchAllNhiIdentities doc comment for why this replaces the old client-side
        // "most violations first" ranking (that would need a $lookup into nhi_violations).
        { field: "discoveredTimestamp", headerName: "Discovered", minWidth: 140, sort: "desc", sortIndex: 0 },
    ];
}

// ── Page ───────────────────────────────────────────────────────────────────────
const pageTitle = (
    <HorizontalStack gap="2" blockAlign="center">
        <TitleWithInfo
            titleText="Identities"
            tooltipContent="Non-human identities (API keys, tokens, service accounts) used by your AI agents."
            docsUrl="https://ai-security-docs.akto.io/nhi-governance/identities"
        />
        <Badge status="info">Beta</Badge>
    </HorizontalStack>
);

export default function IdentitiesPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "all";

    // Full-account fetch — feeds the topology graph, tab counts, and summary cards, all of which
    // genuinely need every identity (see IdentityOverviewGraph, which fans out ALL of an agent's
    // identities). The table itself (below) fetches its own paginated page independently.
    const [rawIdentities, setRawIdentities] = useState([]);
    const [rawViolations, setRawViolations] = useState([]);
    const [loading, setLoading] = useState(true);

    // UI state
    const [selectedTab, setSelectedTab]         = useState(initialSelectedTab);
    const [bulkSelectedCount, setBulkSelectedCount] = useState(0);
    const [tableRefreshKey, setTableRefreshKey] = useState(0);
    const gridRef = useRef(null);
    const [showDeleteModal, setShowDeleteModal]     = useState(false);
    const [selectedIdentityIds, setSelectedIdentityIds] = useState([]);
    const [deleting, setDeleting]                   = useState(false);
    const [selectedRow, setSelectedRow]             = useState(null);
    const [showDetailsPanel, setShowDetailsPanel]   = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const startTimestamp = parseInt(currDateRange.period.since.getTime() / 1000);
    const endTimestamp = parseInt(currDateRange.period.until.getTime() / 1000);

    const fetchData = async () => {
        try {
            setLoading(true);

            const identitiesResponse = await observeRequests.fetchNhiIdentities(startTimestamp, endTimestamp);
            setRawIdentities(Array.isArray(identitiesResponse) ? identitiesResponse.map(transformIdentityForUI) : []);
            setLoading(false);

            try {
                const violationsResponse = await observeRequests.fetchViolationCountsByIdentity();
                setRawViolations(Array.isArray(violationsResponse) ? violationsResponse : []);
            } catch (violErr) {
                console.error("Error fetching violations for counts:", violErr);
                setRawViolations([]);
            }
        } catch (err) {
            console.error("Error fetching identities:", err);
            setRawIdentities([]);
        } finally {
            setLoading(false);
        }
    };

    // Fetch identities and violations from API
    useEffect(() => {
        fetchData();
    }, [startTimestamp, endTimestamp]);

    // Build violation index from the server-grouped counts (one row per identityName x severity,
    // not one row per violation document).
    const violationIndex = useMemo(() => {
        return rawViolations.reduce((acc, row) => {
            const identityName = row.identityName;
            if (!identityName) return acc;
            if (!acc[identityName]) acc[identityName] = { violCrit: 0, violHigh: 0, violMed: 0 };
            const count = row.count || 0;
            if (row.severity === "Critical")     acc[identityName].violCrit += count;
            else if (row.severity === "High")    acc[identityName].violHigh += count;
            else if (row.severity === "Medium")  acc[identityName].violMed += count;
            return acc;
        }, {});
    }, [rawViolations]);

    // Full dataset (unpaginated) — feeds the graph, tab counts, and summary cards only.
    const fullDatasetRows = useMemo(() => {
        return buildFullDatasetRows(rawIdentities, violationIndex);
    }, [rawIdentities, violationIndex]);

    const dataByTab = useMemo(() => ({
        "all":      fullDatasetRows,
        "expired":  fullDatasetRows.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")),
        "disabled": fullDatasetRows.filter((r) => r.status === "INACTIVE"),
    }), [fullDatasetRows]);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );
    const selectedTabIndex = Math.max(0, definedTableTabs.findIndex((t) => func.getKeyFromName(t) === selectedTab));

    const summaryItems = makeSummaryItems(fullDatasetRows);

    const triggerTableRefresh = useCallback(() => setTableRefreshKey((k) => k + 1), []);

    // IdentityOverviewGraph (react-flow) sits above the grid and settles its own layout
    // asynchronously after mount, which can leave the grid's row virtualization computed against
    // a stale viewport — rows land correctly in the DOM (confirmed via accessibility tree) but
    // don't visually paint until something forces a relayout. Nudge it once per grid (re)mount.
    useEffect(() => {
        const t = setTimeout(() => window.dispatchEvent(new Event("resize")), 300);
        return () => clearTimeout(t);
    }, [selectedTab, startTimestamp, endTimestamp, tableRefreshKey]);

    // ─── Server-side data fetch for AG Grid ─────────────────────────────────────
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const pageSize = limit || 50;
        const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "createdAt";
        // AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc — opposite of the backend's Mongo
        // convention (1 asc / -1 desc), matching ViolationsPage.jsx's own onServerFetch.
        const mongoSortOrder = sortOrder ? -sortOrder : -1;
        const status = selectedTab === "all" ? undefined : (selectedTab === "expired" ? "Expired" : "Disabled");

        return observeRequests.fetchAllNhiIdentities(startTimestamp, endTimestamp, {
            skip,
            limit: pageSize,
            sortKey: mappedSortKey,
            sortOrder: mongoSortOrder,
            queryValue: searchString || undefined,
            status,
        }).then((res) => ({
            value: (res.identities || []).map((identity) => buildGridRow(identity, violationIndex)),
            total: res.total || 0,
        }));
    }, [startTimestamp, endTimestamp, selectedTab, violationIndex]);

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

    const bulkActions = useMemo(() => [
        {
            label: "Delete identity",
            destructive: true,
            onAction: () => {
                setSelectedIdentityIds(getSelectedIds());
                setShowDeleteModal(true);
            },
        },
    ], [getSelectedIds]);

    const handleDeleteIdentities = async () => {
        try {
            setDeleting(true);
            await observeRequests.deleteNhiIdentities(selectedIdentityIds);
            func.setToast(true, false, `${selectedIdentityIds.length} identit${selectedIdentityIds.length > 1 ? "ies" : "y"} deleted successfully`);
            setShowDeleteModal(false);
            clearBulkSelection();
            triggerTableRefresh();
            await fetchData();
        } catch (err) {
            func.setToast(true, true, "Failed to delete identities");
        } finally {
            setDeleting(false);
        }
    };

    const handleRowClick = useCallback((e) => {
        if (e?.data) {
            setSelectedRow(e.data);
            setShowDetailsPanel(true);
        }
    }, []);

    const colDefs = useMemo(() => buildColDefs(), []);

    if (loading) {
        return <SpinnerCentered />;
    }

    return (
        <>
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <IdentityOverviewGraph
                    key="overview-graph"
                    tableData={fullDatasetRows}
                    onIdentityClick={(row) => { setSelectedRow(row); setShowDetailsPanel(true); }}
                />,

                <Box key="identities-table">
                    <Box paddingBlockEnd="3">
                        <Tabs
                            tabs={tableTabs}
                            selected={selectedTabIndex}
                            onSelect={(index) => tableTabs[index]?.onAction?.()}
                        />
                    </Box>
                    <AgGridTable
                        key={`nhi-identities-grid-${selectedTab}-${startTimestamp}-${endTimestamp}-${tableRefreshKey}`}
                        columnDefs={colDefs}
                        defaultColDef={DEFAULT_COL_DEF}
                        autoSizeStrategy={AUTO_SIZE_STRATEGY}
                        searchPlaceholder="Search identities"
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
                        filterStateUrl="nhi-identities-grid"
                        serverSideRowModel
                        getRowId={(params) => params.data.id}
                    />
                </Box>,
            ]}
        />
        {selectedRow && (
            <IdentityDetailsPanel
                row={selectedRow}
                show={showDetailsPanel}
                setShow={setShowDetailsPanel}
                onUpdated={() => { fetchData(); triggerTableRefresh(); }}
            />
        )}
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Delete identity?"
            primaryAction={{
                content: "Delete identity",
                destructive: true,
                loading: deleting,
                onAction: handleDeleteIdentities,
            }}
            secondaryActions={[{ content: "Cancel", onAction: () => setShowDeleteModal(false) }]}
        >
            <Modal.Section>
                <Text variant="bodyMd">
                    Are you sure you want to delete the selected identities? This action cannot be undone.
                </Text>
            </Modal.Section>
        </Modal>
        </>
    );
}
