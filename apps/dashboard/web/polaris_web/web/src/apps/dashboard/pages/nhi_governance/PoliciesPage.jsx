import { useState, useMemo, useEffect } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, Box, Button, HorizontalStack, Modal, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { CancelMinor, ViewMinor } from "@shopify/polaris-icons";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import { ViolationBubbles } from "./nhiViolationsData";
import observeRequests from "../observe/api";
import CreateNhiPolicyModal from "./CreateNhiPolicyModal";
import SpinnerCentered from "../../components/progress/SpinnerCentered";
import { formatRelativeTime } from "./nhiUtils";

const definedTableTabs = ["All", "Active", "Inactive", "Draft"];
const resourceName = { singular: "policy", plural: "policies" };

// ── Scope cell with tooltip on "+N" ───────────────────────────────────────────
function ScopeCell({ scope, agents }) {
    if (!scope) return null;
    if (typeof scope === "string") return <Text variant="bodyMd">{scope}</Text>;

    const extraAgents = agents && agents.length > 1 ? agents.slice(1) : [];
    const tooltipContent = extraAgents.length > 0
        ? <VerticalStack gap="1">{extraAgents.map((a, i) => <Text key={a} variant="bodyMd" color="subdued">{`${i + 2}. ${a}`}</Text>)}</VerticalStack>
        : null;

    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <Text variant="bodyMd">{scope.primary}</Text>
            {scope.extra > 0 && tooltipContent && (
                <Tooltip content={tooltipContent} dismissOnMouseOut>
                    <Box><Badge>{`+${scope.extra}`}</Badge></Box>
                </Tooltip>
            )}
            {scope.extra > 0 && !tooltipContent && <Badge>{`+${scope.extra}`}</Badge>}
        </HorizontalStack>
    );
}

// ── Status badge ───────────────────────────────────────────────────────────────
const STATUS_COLOR = { Active: "success", Inactive: "", Draft: "warning" };
const statusBadge = (s) => <Badge status={STATUS_COLOR[s] || ""}>{s}</Badge>;

// ── Transform API policy to table format ───────────────────────────────────────
const STATUS_MAP = { ACTIVE: "Active", INACTIVE: "Inactive", DRAFT: "Draft" };

function transformApiPolicy(apiPolicy, idx, violCountByPolicy = {}) {
    const agents  = apiPolicy.scope?.agents  || [];
    const nhiIds  = apiPolicy.scope?.nhiIds  || [];
    const scope = agents.length === 0
        ? { primary: "All Agents" }
        : { primary: agents[0], extra: Math.max(0, agents.length - 1), extras: agents.slice(1) };

    const status = STATUS_MAP[apiPolicy.status] || apiPolicy.status || "Active";
    const policyId = apiPolicy.hexId;
    const vc = violCountByPolicy[policyId] || { total: 0, critical: 0, high: 0, medium: 0, low: 0 };

    return {
        ...apiPolicy,
        hexId: policyId,
        id: idx + 1,
        policyName: apiPolicy.policyName,
        agents,
        nhiIds,
        scope,
        status,
        totalViolations: vc.total,
        violCrit: vc.critical, violHigh: vc.high, violMed: vc.medium, violLow: vc.low,
        lastTriggered: apiPolicy.lastTriggeredAt ? formatRelativeTime(apiPolicy.lastTriggeredAt) : "Never",
        lastModified: apiPolicy.updatedAt ? formatRelativeTime(apiPolicy.updatedAt) : "—",
        created: apiPolicy.createdAt ? formatRelativeTime(apiPolicy.createdAt) : "—",
        policyNameComp: <Text variant="bodyMd" fontWeight="medium">{apiPolicy.policyName}</Text>,
        violationsComp: <ViolationBubbles critical={vc.critical} high={vc.high} medium={vc.medium} low={vc.low} />,
        scopeComp: <ScopeCell scope={scope} agents={agents} />,
        statusComp: statusBadge(status),
    };
}

// ── Headers ────────────────────────────────────────────────────────────────────
const headers = [
    { text: "Policy Name",    value: "policyNameComp", title: "Policy Name"                         },
    { text: "Violations",     value: "violationsComp", title: "Violations"                           },
    { text: "Scope",          value: "scopeComp",      title: "Scope"                               },
    { text: "Status",         value: "statusComp",     title: "Status"                              },
    { text: "Last Triggered", value: "lastTriggered",  title: "Last Triggered", type: CellType.TEXT },
    { text: "Last Modified",  value: "lastModified",   title: "Last Modified",  type: CellType.TEXT },
    { text: "Created",        value: "created",        title: "Created",        type: CellType.TEXT },
];

const sortOptions = [
    { label: "Violations", value: "violations asc",  directionLabel: "Most first",  sortKey: "totalViolations", columnIndex: 1 },
    { label: "Violations", value: "violations desc", directionLabel: "Least first", sortKey: "totalViolations", columnIndex: 1 },
];

// ── Page ───────────────────────────────────────────────────────────────────────
const policiesPageTitle = (
    <TitleWithInfo
        titleText="Policies"
        tooltipContent="Governance policies that define rules and constraints for non-human identity usage."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/policies"
    />
);

export default function PoliciesPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "all";

    // API state
    const [rawPolicies, setRawPolicies]   = useState([]);
    const [rawViolations, setRawViolations] = useState([]);
    const [loading, setLoading]           = useState(true);

    // UI state
    const [selectedTab, setSelectedTab]   = useState(initialSelectedTab);
    const [selected, setSelected]         = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [showDeleteModal, setShowDeleteModal]   = useState(false);
    const [showCreateModal, setShowCreateModal]   = useState(false);
    const [isEditMode, setIsEditMode]             = useState(false);
    const [editingPolicy, setEditingPolicy]       = useState(null);

    const fetchPolicies = async () => {
        try {
            setLoading(true);
            const [policiesResp, violationsResp] = await Promise.all([
                observeRequests.fetchNhiPolicies(),
                observeRequests.fetchAllNhiViolations(),
            ]);
            setRawPolicies(Array.isArray(policiesResp) ? policiesResp : []);
            setRawViolations(Array.isArray(violationsResp) ? violationsResp : []);
        } catch (err) {
            console.error("Error fetching NHI policies:", err);
            setRawPolicies([]);
            setRawViolations([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchPolicies(); }, []);

    const violCountByPolicy = useMemo(() => {
        const map = {};
        rawViolations.forEach((v) => {
            (v.policyIds || []).forEach((pId) => {
                if (!map[pId]) map[pId] = { total: 0, critical: 0, high: 0, medium: 0, low: 0 };
                map[pId].total++;
                const sev = (v.severity || "").toLowerCase();
                if (sev === "critical") map[pId].critical++;
                else if (sev === "high") map[pId].high++;
                else if (sev === "medium") map[pId].medium++;
                else map[pId].low++;
            });
        });
        return map;
    }, [rawViolations]);

    const tableData = useMemo(
        () => rawPolicies.map((p, i) => transformApiPolicy(p, i, violCountByPolicy)),
        [rawPolicies, violCountByPolicy]
    );

    // Cross-page navigation: open edit modal for a specific policy
    useEffect(() => {
        if (loading || tableData.length === 0) return;

        // From ViolationDetailsPanel "Update Policy"
        const editName = sessionStorage.getItem("nhi_policy_edit_name");
        if (editName) {
            sessionStorage.removeItem("nhi_policy_edit_name");
            const match = tableData.find((r) => r.policyName === editName);
            if (match) { openEditModal(match); }
            return;
        }

        // Legacy: clicking policy name link in violation panel (view mode)
        const pending = sessionStorage.getItem("nhi_pending_policy");
        if (pending) {
            sessionStorage.removeItem("nhi_pending_policy");
            const match = tableData.find((r) => r.policyName === pending);
            if (match) { openEditModal(match); }
        }
    }, [loading, tableData]);

    const openEditModal = (policy) => {
        setEditingPolicy(policy);
        setIsEditMode(true);
        setShowCreateModal(true);
    };

    const openCreateModal = () => {
        setEditingPolicy(null);
        setIsEditMode(false);
        setShowCreateModal(true);
    };

    const handleSavePolicy = async (payload, policyId) => {
        try {
            await observeRequests.saveNhiPolicy(payload, policyId);
            await fetchPolicies();
        } catch (err) {
            console.error("Error saving NHI policy:", err);
        }
    };

    const handleDisablePolicy = async (row) => {
        const policyId = row.hexId || row._id?.$oid;
        if (!policyId) return;
        try {
            await observeRequests.saveNhiPolicy({ status: "INACTIVE" }, policyId);
            await fetchPolicies();
        } catch (err) {
            console.error("Error disabling NHI policy:", err);
        }
    };

    const getActionsList = (item) => {
        const isActive = item.status === "Active";
        return [{
            title: "Actions",
            items: [
                {
                    content: isActive
                        ? <span style={{ color: "#D72C0D" }}>Disable policy</span>
                        : <span style={{ color: "#008060" }}>Enable policy</span>,
                    icon: CancelMinor,
                    onAction: () => handleDisablePolicy(item),
                    destructive: isActive,
                },
                {
                    content: "View details",
                    icon: ViewMinor,
                    onAction: () => openEditModal(item),
                },
            ],
        }];
    };

    const dataByTab = useMemo(() => ({
        all:      tableData,
        active:   tableData.filter((r) => r.status === "Active"),
        inactive: tableData.filter((r) => r.status === "Inactive"),
        draft:    tableData.filter((r) => r.status === "Draft"),
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

    const summaryItems = useMemo(() => [
        { title: "Total Policies",             data: tableData.length.toLocaleString() },
        { title: "Total Violations Triggered", data: tableData.reduce((s, r) => s + r.totalViolations, 0).toLocaleString() },
    ], [tableData]);

    if (loading) return <SpinnerCentered />;

    if (showCreateModal) {
        const closeModal = () => { setShowCreateModal(false); setEditingPolicy(null); setIsEditMode(false); };
        return (
            <CreateNhiPolicyModal
                onClose={closeModal}
                onSave={handleSavePolicy}
                onDisable={isEditMode && editingPolicy ? async () => { await handleDisablePolicy(editingPolicy); closeModal(); } : undefined}
                editingPolicy={editingPolicy}
                isEditMode={isEditMode}
            />
        );
    }

    return (
        <>
        <PageWithMultipleCards
            title={policiesPageTitle}
            isFirstPage
            primaryAction={{ content: "Create Policy", onAction: openCreateModal }}
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <GithubSimpleTable
                    key="policies-table"
                    data={dataByTab[selectedTab]}
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
                    onRowClick={(r) => openEditModal(r)}
                    rowClickable={true}
                    getActions={getActionsList}
                    promotedBulkActions={() => [
                        { content: "Delete policy", destructive: true, onAction: () => setShowDeleteModal(true) },
                    ]}
                />,
            ]}
        />
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Delete policy?"
            primaryAction={{
                content: "Delete policy",
                destructive: true,
                onAction: () => setShowDeleteModal(false),
            }}
            secondaryActions={[{ content: "Cancel", onAction: () => setShowDeleteModal(false) }]}
        >
            <Modal.Section>
                <Text variant="bodyMd">
                    Are you sure you want to delete the selected policies? This action cannot be undone.
                </Text>
            </Modal.Section>
        </Modal>
        </>
    );
}
