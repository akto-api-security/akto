import { useState, useMemo } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, Button, HorizontalStack, Modal, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";

const definedTableTabs = ["All", "Active", "Inactive", "Draft"];
const resourceName = { singular: "policy", plural: "policies" };

// ── Violation bubbles — colors from API Security discovery page ────────────────
function ViolationBubbles({ critical = 0, high = 0, medium = 0 }) {
    if (!critical && !high && !medium)
        return <Text variant="bodyMd" color="subdued">No Violations</Text>;
    const dot = (count, bg, fg) =>
        count > 0 ? (
            <span
                key={bg}
                style={{
                    background: bg, color: fg, borderRadius: "50%",
                    width: 22, height: 22, display: "inline-flex",
                    alignItems: "center", justifyContent: "center",
                    fontSize: 11, fontWeight: 600, flexShrink: 0,
                }}
            >{count}</span>
        ) : null;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {dot(critical, "#DF2909", "white")}
            {dot(high,     "#FED3D1", "#202223")}
            {dot(medium,   "#FFD79D", "#202223")}
        </HorizontalStack>
    );
}

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
                    <span><Badge>{`+${scope.extra}`}</Badge></span>
                </Tooltip>
            )}
            {scope.extra > 0 && !tooltipContent && <Badge>{`+${scope.extra}`}</Badge>}
        </HorizontalStack>
    );
}

// ── Status badge ───────────────────────────────────────────────────────────────
const STATUS_COLOR = { Active: "success", Inactive: "", Draft: "warning" };
const statusBadge = (s) => <Badge status={STATUS_COLOR[s] || ""}>{s}</Badge>;

// ── 10 policies (7 Active, 2 Inactive, 1 Draft) ────────────────────────────────
// violCrit + violHigh + violMed across active policies sums to 148
const RAW_POLICIES = [
    {
        policyName:    "No Admin Credentials for Agent Identities",
        violCrit: 3,  violHigh: 12, violMed: 6,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Ethan Carter",
        created:       "30d ago",
    },
    {
        policyName:    "Enforce Least Privilege on Credentials",
        violCrit: 6,  violHigh: 17, violMed: 0,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "5h ago",
        lastModified:  "Olivia Bennett",
        created:       "45d ago",
    },
    {
        policyName:    "Rotate API Keys Every 30 Days",
        violCrit: 0,  violHigh: 15, violMed: 0,
        scope:         { primary: "Cursor Prod", extra: 2 },
        agents:        ["Cursor Prod", "HR Assistant", "CI Bot"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Marcus Hale",
        created:       "60d ago",
    },
    {
        policyName:    "Detect Unusual Usage Patterns",
        violCrit: 0,  violHigh: 12, violMed: 6,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "3h ago",
        lastModified:  "Marcus Hale",
        created:       "20d ago",
    },
    {
        policyName:    "Restrict Access to Sensitive Resources",
        violCrit: 0,  violHigh: 12, violMed: 3,
        scope:         { primary: "Cursor Prod", extra: 2 },
        agents:        ["Cursor Prod", "Analytics Agent", "Data Sync"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Marcus Hale",
        created:       "50d ago",
    },
    {
        policyName:    "Disable Dormant Credentials (30+ days)",
        violCrit: 0,  violHigh: 0,  violMed: 8,
        scope:         { primary: "My Assistant", extra: 23 },
        agents:        ["My Assistant","Cursor Prod","HR Assistant","Code Reviewer","CI Bot","Finance Bot","Deploy Bot","Analytics Agent","Support Agent","Data Sync","Notify Bot","Log Monitor","Docs Assistant","Infra Agent","Marketing Bot","Test Runner","Audit Agent","Compliance Bot","Security Scanner","Backup Agent","Report Bot","API Gateway","Payment Proc","Auth Service"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Ethan Carter",
        created:       "40d ago",
    },
    {
        policyName:    "Prevent Cross-Service Credential Usage",
        violCrit: 0,  violHigh: 20, violMed: 0,
        scope:         { primary: "My Assistant", extra: 12 },
        agents:        ["My Assistant","Cursor Prod","HR Assistant","Code Reviewer","CI Bot","Finance Bot","Deploy Bot","Analytics Agent","Support Agent","Data Sync","Notify Bot","Log Monitor","Docs Assistant"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Ethan Carter",
        created:       "35d ago",
    },
    {
        policyName:    "Limit Automation Without Approval",
        violCrit: 0,  violHigh: 0,  violMed: 12,
        scope:         { primary: "Connector", extra: 3 },
        agents:        ["Connector", "HR Assistant", "Data Sync", "CI Bot"],
        status:        "Inactive",
        lastTriggered: "45m ago",
        lastModified:  "Marcus Hale",
        created:       "25d ago",
    },
    {
        policyName:    "Restrict Code Execution Permissions",
        violCrit: 0,  violHigh: 0,  violMed: 16,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Inactive",
        lastTriggered: "1h ago",
        lastModified:  "Olivia Bennett",
        created:       "15d ago",
    },
    {
        policyName:    "Enforce Scoped Access for OAuth Tokens",
        violCrit: 0,  violHigh: 0,  violMed: 0,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Draft",
        lastTriggered: "Never",
        lastModified:  "Olivia Bennett",
        created:       "1d ago",
    },
];

const tableData = RAW_POLICIES.map((r, i) => ({
    ...r,
    id:              i + 1,
    totalViolations: r.violCrit + r.violHigh + r.violMed,
    policyNameComp:  <Text variant="bodyMd" fontWeight="medium">{r.policyName}</Text>,
    violationsComp:  <ViolationBubbles critical={r.violCrit} high={r.violHigh} medium={r.violMed} />,
    scopeComp:       <ScopeCell scope={r.scope} agents={r.agents} />,
    statusComp:      statusBadge(r.status),
}));

// ── Computed summary ───────────────────────────────────────────────────────────
const TOTAL_P      = tableData.length;
const VIOLATIONS_T = tableData.reduce((s, r) => s + r.totalViolations, 0);

const summaryItems = [
    { title: "Total Policies",             data: TOTAL_P.toLocaleString()      },
    { title: "Total Violations Triggered", data: VIOLATIONS_T.toLocaleString() },
];

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

    const [selectedTab, setSelectedTab]         = useState(initialSelectedTab);
    const [selected, setSelected]               = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [showDeleteModal, setShowDeleteModal] = useState(false);

    const dataByTab = useMemo(() => ({
        all:      tableData,
        active:   tableData.filter((r) => r.status === "Active"),
        inactive: tableData.filter((r) => r.status === "Inactive"),
        draft:    tableData.filter((r) => r.status === "Draft"),
    }), []);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );

    return (
        <>
        <PageWithMultipleCards
            title={policiesPageTitle}
            isFirstPage
            primaryAction={<Button variant="primary" onClick={() => {}}>Create Policy</Button>}
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
                    promotedBulkActions={(selectedIds) => {
                        const selectedRows = dataByTab[selectedTab].filter((r) => selectedIds.includes(r.id) || selectedIds.includes(String(r.id)));
                        const hasActive = selectedRows.length === 0 || selectedRows.some((r) => r.status === "Active");
                        const hasDraft  = selectedRows.some((r) => r.status === "Draft");
                        return [
                            ...(hasActive ? [{ content: "Mark as inactive", onAction: () => {} }] : []),
                            ...(hasDraft  ? [{ content: "Mark as active",   onAction: () => {} }] : []),
                            { content: "Delete policy", destructive: true, onAction: () => setShowDeleteModal(true) },
                        ];
                    }}
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
