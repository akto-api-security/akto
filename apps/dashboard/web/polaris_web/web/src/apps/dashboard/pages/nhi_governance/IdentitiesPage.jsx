import { useState, useMemo, useReducer, useEffect, useCallback } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, HorizontalStack, Modal, Text } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import { CellType } from "../../components/tables/rows/GithubRow";
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
const resourceName = { singular: "identity", plural: "identities" };

// ── Expiry status renderer ─────────────────────────────────────────────────────
const expiryComp = (s) => {
    if (!s) return null;
    if (s.startsWith("Expired"))
        return <Text variant="bodyMd" color="critical">{s}</Text>;
    if (s === "Rotation due today" || s.startsWith("Rotation Due in"))
        return <Text variant="bodyMd" color="warning" fontWeight="medium">{s}</Text>;
    return <Text variant="bodyMd">{s}</Text>;
};

const buildTableData = (rawRows, violationIndex = {}) =>
    rawRows
        .map((r) => {
            const v = violationIndex[r.identityName] || { violCrit: 0, violHigh: 0, violMed: 0 };
            return { ...r, violCrit: v.violCrit, violHigh: v.violHigh, violMed: v.violMed };
        })
        .sort((a, b) => {
            if (b.violCrit !== a.violCrit) return b.violCrit - a.violCrit;
            return (b.violCrit + b.violHigh + b.violMed) - (a.violCrit + a.violHigh + a.violMed);
        })
        .map((r) => ({
            ...r,
            id:             r.hexId,
            totalViolations: r.violCrit + r.violHigh + r.violMed,
            priorityScore:  r.violCrit * 1000 + (r.violCrit + r.violHigh + r.violMed),
            identityComp:  <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identityName} /><Text variant="bodyMd" fontWeight="medium">{r.identityName}</Text></HorizontalStack>,
            agentComp:     <HorizontalStack gap="2" blockAlign="center" wrap={false}><AgentIcon name={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
            typeComp:      <Badge>{r.type}</Badge>,
            violationsComp: <ViolationBubbles critical={r.violCrit} high={r.violHigh} medium={r.violMed} />,
            expiryComp:    expiryComp(r.expiryStatus),
        }));


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

// Per-row shaping for the table's own server-paginated page — mirrors buildTableData's per-row
// map step above (same identityComp/agentComp/typeComp/violationsComp/expiryComp cell shapes),
// applied to one already-fetched page instead of the full account. violationIndex is the same
// small, cheap, account-wide map buildTableData already uses (fetchViolationCountsByIdentity),
// safe to merge in per-page without an extra network call.
function buildPageRow(apiIdentity, violationIndex) {
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

// ── Headers ────────────────────────────────────────────────────────────────────
const headers = [
    { text: "Identity",      value: "identityComp",       title: "Identity"                           },
    { text: "Agentic Asset", value: "agentComp",          title: "Agentic Asset"                      },
    ...(isEndpointSecurityCategory() ? [{ text: "Owner", value: "owner", title: "Owner", type: CellType.TEXT }] : []),
    { text: "Type",          value: "typeComp",           title: "Type"                               },
    { text: "Violations",    value: "violationsComp",     title: "Violations"                         },
    { text: "Expiry Status", value: "expiryComp",         title: "Expiry Status"                      },
    { text: "Discovered",    value: "discoveredTimestamp", title: "Discovered", type: CellType.TEXT   },
];

// Real backend-sortable fields only (NhiGovernanceIdentitiesAction.fetchAllNhiIdentities) — the old
// "sort by violations" option relied on a client-only computed priorityScore, which isn't something
// the paginated query can sort by without a $lookup into nhi_violations (identities don't carry a
// denormalized violation count). Default view (Discovered desc) is set on the Discovered column
// itself via GithubServerTable's usual initial-sort convention.
const sortOptions = [
    { label: "Discovered", value: "createdAt desc", directionLabel: "Newest first", sortKey: "createdAt", columnIndex: 6 },
    { label: "Discovered", value: "createdAt asc",  directionLabel: "Oldest first", sortKey: "createdAt", columnIndex: 6 },
    { label: "Identity",   value: "identityName asc",  directionLabel: "A-Z", sortKey: "identityName", columnIndex: 0 },
    { label: "Identity",   value: "identityName desc", directionLabel: "Z-A", sortKey: "identityName", columnIndex: 0 },
];

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

    // API fetching state
    const [rawIdentities, setRawIdentities] = useState([]);
    const [rawViolations, setRawViolations] = useState([]);
    const [loading, setLoading] = useState(true);

    // UI state
    const [selectedTab, setSelectedTab]         = useState(initialSelectedTab);
    const [selected, setSelected]               = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
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

    // Build table data with violation counts
    const tableData = useMemo(() => {
        return buildTableData(rawIdentities, violationIndex);
    }, [rawIdentities, violationIndex]);

    const dataByTab = useMemo(() => ({
        "all":      tableData,
        "expired":  tableData.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")),
        "disabled": tableData.filter((r) => r.status === "INACTIVE"),
    }), [tableData]);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );

    const summaryItems = makeSummaryItems(tableData);

    // ─── Server-side paginated fetch for the table itself ───────────────────────
    // The graph/summary cards/tab counts above keep using the full-account fetch (tableData) —
    // unchanged from before, and already the cheapest way to feed IdentityOverviewGraph, which
    // genuinely needs every identity to fan out each agent's full list correctly. Only the table's
    // own rows are server-paginated now, via fetchAllNhiIdentities.
    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const mongoSortOrder = sortOrder === -1 ? 1 : -1; // GithubServerTable: asc=-1/desc=1, inverted vs Mongo
        const status = selectedTab === "all" ? undefined : (selectedTab === "expired" ? "Expired" : "Disabled");
        const res = await observeRequests.fetchAllNhiIdentities(startTimestamp, endTimestamp, {
            skip, limit, sortKey: sortKey || "createdAt", sortOrder: mongoSortOrder, queryValue, status,
        });
        const rows = (res.identities || []).map((identity) => buildPageRow(identity, violationIndex));
        return { value: rows, total: res.total || 0 };
    }, [startTimestamp, endTimestamp, selectedTab, violationIndex]);

    const handleDeleteIdentities = async () => {
        try {
            setDeleting(true);
            await observeRequests.deleteNhiIdentities(selectedIdentityIds);
            func.setToast(true, false, `${selectedIdentityIds.length} identit${selectedIdentityIds.length > 1 ? "ies" : "y"} deleted successfully`);
            setShowDeleteModal(false);
            await fetchData();
        } catch (err) {
            func.setToast(true, true, "Failed to delete identities");
        } finally {
            setDeleting(false);
        }
    };

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
                    tableData={tableData}
                    onIdentityClick={(row) => { setSelectedRow(row); setShowDetailsPanel(true); }}
                />,

                <GithubServerTable
                    key={`identities-table-${selectedTab}-${startTimestamp}-${endTimestamp}`}
                    fetchData={fetchTableData}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    selectable={true}
                    mode={IndexFiltersMode.Default}
                    headings={headers}
                    useNewRow={true}
                    condensedHeight={true}
                    tableTabs={tableTabs}
                    onSelect={(i) => setSelected(i)}
                    selected={selected}
                    promotedBulkActions={(selectedResources) => [
                        {
                            content: "Delete identity",
                            destructive: true,
                            onAction: () => {
                                setSelectedIdentityIds(selectedResources);
                                setShowDeleteModal(true);
                            },
                        },
                    ]}
                    onRowClick={(row) => { setSelectedRow(row); setShowDetailsPanel(true); }}
                    rowClickable={true}
                    supportsNegationFilter={false}
                />,
            ]}
        />
        {selectedRow && (
            <IdentityDetailsPanel
                row={selectedRow}
                show={showDetailsPanel}
                setShow={setShowDetailsPanel}
                onUpdated={fetchData}
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
