import { useState, useMemo, useReducer } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, HorizontalStack, Modal, Text } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";
import { isEndpointSecurityCategory } from "../../../main/labelHelper";
import IdentityDetailsPanel from "./IdentityDetailsPanel";
import IdentityOverviewGraph from "./IdentityOverviewGraph";
import { violationsTableData, IdentityIcon, AgentIcon, ViolationBubbles } from "./nhiViolationsData";
import { CRITICAL_CURATED, NON_CRITICAL_CURATED, GENERATED } from "./nhiData";

const definedTableTabs = ["All", "Expired"];
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

// ── Violation counts derived from violations data (single source of truth) ────
const VIOL_INDEX = violationsTableData.reduce((acc, v) => {
    if (!acc[v.identity]) acc[v.identity] = { violCrit: 0, violHigh: 0, violMed: 0 };
    if (v.severity === "Critical")     acc[v.identity].violCrit++;
    else if (v.severity === "High")    acc[v.identity].violHigh++;
    else if (v.severity === "Medium")  acc[v.identity].violMed++;
    return acc;
}, {});

const ALL_RAW = [...CRITICAL_CURATED, ...NON_CRITICAL_CURATED, ...GENERATED]
    .map((r) => {
        const v = VIOL_INDEX[r.identityName] || { violCrit: 0, violHigh: 0, violMed: 0 };
        return { ...r, violCrit: v.violCrit, violHigh: v.violHigh, violMed: v.violMed };
    })
    .sort((a, b) => {
        if (b.violCrit !== a.violCrit) return b.violCrit - a.violCrit;
        return (b.violCrit + b.violHigh + b.violMed) - (a.violCrit + a.violHigh + a.violMed);
    });

const tableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:             i + 1,
    totalViolations: r.violCrit + r.violHigh + r.violMed,
    priorityScore:  r.violCrit * 1000 + (r.violCrit + r.violHigh + r.violMed),
    identityComp:  <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identityName} /><Text variant="bodyMd" fontWeight="medium">{r.identityName}</Text></HorizontalStack>,
    agentComp:     <HorizontalStack gap="2" blockAlign="center" wrap={false}><AgentIcon name={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
    typeComp:      <Badge>{r.type}</Badge>,
    violationsComp: <ViolationBubbles critical={r.violCrit} high={r.violHigh} medium={r.violMed} />,
    expiryComp:    expiryComp(r.expiryStatus),
}));

// ── Computed summary ───────────────────────────────────────────────────────────
const TOTAL_I    = tableData.length;
const EXPIRED_I  = tableData.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")).length;
const WITH_VIOL  = tableData.filter((r) => r.totalViolations > 0).length;

const summaryItems = [
    { title: "Total Identities",          data: TOTAL_I.toLocaleString()   },
    { title: "Expired Identities",        data: EXPIRED_I.toLocaleString() },
    { title: "Identities with Violations",data: WITH_VIOL.toLocaleString() },
];

// ── Headers ────────────────────────────────────────────────────────────────────
const headers = [
    { text: "Identity",      value: "identityComp",   title: "Identity"                           },
    { text: "Agentic Asset", value: "agentComp",      title: "Agentic Asset"                      },
    ...(isEndpointSecurityCategory() ? [{ text: "Owner", value: "owner", title: "Owner", type: CellType.TEXT }] : []),
    { text: "Type",          value: "typeComp",       title: "Type"                               },
    { text: "Access",        value: "access",         title: "Access",      type: CellType.TEXT   },
    { text: "Violations",    value: "violationsComp", title: "Violations"                         },
    { text: "Last Used",     value: "lastUsed",       title: "Last Used",   type: CellType.TEXT   },
    { text: "Expiry Status", value: "expiryComp",     title: "Expiry Status"                      },
];

const sortOptions = [
    { label: "Violations", value: "violations asc",  directionLabel: "Most first",   sortKey: "priorityScore", columnIndex: 4 },
    { label: "Violations", value: "violations desc", directionLabel: "Least first",  sortKey: "priorityScore", columnIndex: 4 },
];

// ── Page ───────────────────────────────────────────────────────────────────────
const pageTitle = (
    <TitleWithInfo
        titleText="Identities"
        tooltipContent="Non-human identities (API keys, tokens, service accounts) used by your AI agents."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/identities"
    />
);

export default function IdentitiesPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "all";

    const [selectedTab, setSelectedTab]         = useState(initialSelectedTab);
    const [selected, setSelected]               = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [showDeleteModal, setShowDeleteModal]     = useState(false);
    const [selectedRow, setSelectedRow]             = useState(null);
    const [showDetailsPanel, setShowDetailsPanel]   = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const dataByTab = useMemo(() => ({
        "all":     tableData,
        "expired": tableData.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")),
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

                <GithubSimpleTable
                    key="identities-table"
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
                    promotedBulkActions={() => [
                        { content: "Delete identity", destructive: true, onAction: () => setShowDeleteModal(true) },
                    ]}
                    onRowClick={(row) => { setSelectedRow(row); setShowDetailsPanel(true); }}
                    rowClickable={true}
                />,
            ]}
        />
        {selectedRow && (
            <IdentityDetailsPanel
                row={selectedRow}
                show={showDetailsPanel}
                setShow={setShowDetailsPanel}
            />
        )}
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Delete identity?"
            primaryAction={{
                content: "Delete identity",
                destructive: true,
                onAction: () => setShowDeleteModal(false),
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
