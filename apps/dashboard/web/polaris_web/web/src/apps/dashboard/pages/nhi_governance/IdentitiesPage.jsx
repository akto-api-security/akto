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

// ── 10 Critical Curated (all others non-critical) ──────────────────────────────
const CRITICAL_CURATED = [
    { identityName:"aws-cursor-key",     agent:"Cursor",         type:"API Key",      access:"Admin",       violCrit:3, violHigh:0, violMed:0, lastUsed:"2h ago",  expiryStatus:"2d left",                owner:"Evelyn Carter"     },
    { identityName:"hr-slack-token",     agent:"Claude CLI",        type:"Bearer Token", access:"Write",      violCrit:1, violHigh:3, violMed:0, lastUsed:"45m ago", expiryStatus:"Rotation Due in 2 days", owner:"John Matthews"     },
    { identityName:"aws-env-sa",         agent:"Windsurf",       type:"Bearer Token", access:"Write",      violCrit:2, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"Rotation due today",     owner:"Adam Brooks"       },
    { identityName:"github-oauth-456",   agent:"VS Code",        type:"Bearer Token", access:"Read/Write", violCrit:1, violHigh:3, violMed:2, lastUsed:"3h ago",  expiryStatus:"60d left",               owner:"Sarah Williams"    },
    { identityName:"jira-token",         agent:"Claude Desktop",  type:"API Key",      access:"Read",       violCrit:2, violHigh:4, violMed:1, lastUsed:"Never",   expiryStatus:"15d left",               owner:"Noah Bennett"      },
    { identityName:"internal-api-token", agent:"Claude CLI",           type:"Bearer Token", access:"Read",       violCrit:1, violHigh:0, violMed:1, lastUsed:"2d ago",  expiryStatus:"60d left",               owner:"Theodore Collins"  },
    { identityName:"airbnb-api-key",      agent:"Antigravity",   type:"Bearer Token", access:"Read",       violCrit:2, violHigh:5, violMed:0, lastUsed:"6h ago",  expiryStatus:"7d left",                owner:"Michael Alvarez"   },
    { identityName:"vscode-oauth",       agent:"VS Code",     type:"API Key",      access:"Write",      violCrit:1, violHigh:2, violMed:1, lastUsed:"Never",   expiryStatus:"No expiry",              owner:"Nina Nolan"        },
    { identityName:"docker-registry-key",         agent:"Cursor",     type:"API Key",      access:"Admin",      violCrit:3, violHigh:0, violMed:2, lastUsed:"5h ago",  expiryStatus:"Rotation due today",     owner:"Lisa Wong"         },
    { identityName:"github-actions-key", agent:"VS Code",          type:"Bearer Token", access:"Write",      violCrit:1, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"Expired 1d ago",         owner:"Kevin O'Connor"    },
];

// ── Non-critical curated (high/medium/low violations) ──────────────────────────
const NON_CRITICAL_CURATED = [
    { identityName:"playwright-token",    agent:"Claude Desktop",     type:"Bearer Token", access:"Write",      violCrit:0, violHigh:1, violMed:0, lastUsed:"3h ago",  expiryStatus:"15d left",  owner:"Sarah Williams"  },
    { identityName:"filesystem-token",    agent:"Windsurf",     type:"API Key",      access:"Admin",      violCrit:0, violHigh:1, violMed:1, lastUsed:"6h ago",  expiryStatus:"5d left",   owner:"Adam Brooks"     },
    { identityName:"notion-token",       agent:"Claude CLI",  type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"1d left",   owner:"Noah Bennett"    },
    { identityName:"anthropic-api-key",      agent:"Claude CLI",   type:"Bearer Token", access:"Write",      violCrit:0, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"No expiry", owner:"Evelyn Carter"   },
    { identityName:"github-copilot-key",      agent:"Cursor",      type:"API Key",      access:"Read/Write", violCrit:0, violHigh:3, violMed:1, lastUsed:"3h ago",  expiryStatus:"No expiry", owner:"John Matthews"   },
];

// ── Generation pools ───────────────────────────────────────────────────────────
const AGENTS_POOL = [
    "Cursor","Claude CLI","VS Code","Claude Desktop","Windsurf",
    "Antigravity","Cursor","VS Code","Claude CLI","Claude Desktop",
    "Windsurf","Cursor","Claude CLI","VS Code","Antigravity",
    "Claude Desktop","Windsurf","Cursor","VS Code","Claude CLI",
    "Antigravity","Cursor","Claude Desktop","VS Code","Windsurf",
    "Claude CLI","Cursor","Antigravity","VS Code","Claude Desktop",
];
const IDENTITY_POOL = [
    "aws-prod-key","gcp-svc-account","azure-sp-token","github-actions-sa","okta-api-key",
    "twilio-auth-token","anthropic-api-key","salesforce-jwt","mongo-atlas-key","redis-cloud-sa",
    "elastic-api-key","vault-approle","argo-cd-token","terraform-sa","jenkins-cred",
    "splunk-hec-token","pagerduty-key","opsgenie-token","linear-api-key","notion-oauth",
    "zoom-jwt","box-oauth","dropbox-token","figma-pat","cloudflare-api-key",
    "vercel-token","netlify-token","heroku-api-key","fly-io-token","render-token",
    "datadog-agent-key","newrelic-license","grafana-cloud-key","sentry-auth","launchdarkly-sdk",
    "mixpanel-token","amplitude-key","segment-write-key","intercom-token","zendesk-jwt",
];
const OWNERS_POOL   = [
    "Evelyn Carter","John Matthews","Adam Brooks","Sarah Williams","Noah Bennett",
    "Theodore Collins","Michael Alvarez","Nina Nolan","Lisa Wong","Kevin O'Connor",
    "Grace Mitchell","Daniel Harper","Rachel Foster","James Sullivan","Claire Anderson",
];
const TYPES_POOL    = ["API Key", "Bearer Token", "API Key", "Bearer Token", "API Key"];
const ACCESS_POOL   = ["Admin", "Read", "Write", "Read/Write", "Read", "Write"];
const LAST_USED_P   = ["2m ago","15m ago","1h ago","3h ago","8h ago","1d ago","3d ago","7d ago","Never","30s ago","45m ago","2d ago","6h ago","12h ago"];
const EXPIRY_POOL   = [
    "30d left","15d left","7d left","5d left","60d left","45d left","90d left",
    "Rotation Due in 7 days","Rotation Due in 14 days","No expiry",
    "Expired 3d ago","Expired 7d ago","2d left","1d left",
];

const pick = (arr, i) => arr[i % arr.length];

// Generated: all non-critical (violCrit = 0), have high/medium/low violations
const GENERATED = Array.from({ length: 109 }, (_, i) => {
    const idx     = i + 16;
    const hasViol = i % 5 !== 4; // 80% have some violations

    return {
        identityName: `${pick(IDENTITY_POOL, idx)}-${idx}`,
        agent:        pick(AGENTS_POOL, idx + 2),
        type:         pick(TYPES_POOL, idx + 1),
        access:       pick(ACCESS_POOL, idx + 3),
        violCrit:     0, // All generated are non-critical
        violHigh:     !hasViol ? 0 : (1 + (idx % 4)),
        violMed:      hasViol ? (idx % 3) : 0,
        lastUsed:     pick(LAST_USED_P, idx + 4),
        expiryStatus: pick(EXPIRY_POOL, idx + 5),
        owner:        pick(OWNERS_POOL, idx + 6),
    };
});

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
    { text: "Agent",         value: "agentComp",      title: "Agent"                              },
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
