import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, Text } from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import func from "@/util/func";

const resourceName = { singular: "policy", plural: "policies" };
const SEV_ORD = { Critical: 4, High: 3, Medium: 2, Low: 1 };

const sevBadge = (s) => (
    <div className={`badge-wrapper-${s.toUpperCase()}`}>
        <Badge status={func.getHexColorForSeverity(s.toUpperCase())}>{s}</Badge>
    </div>
);

const STATUS_COLOR = { Active: "success", Draft: "" };
const statusBadge = (s) => <Badge status={STATUS_COLOR[s] || ""}>{s}</Badge>;

// ── 24 policies, sorted Critical → High → Medium → Low ───────────────────────
const RAW_POLICIES = [
    // ── Critical (3) ──
    { severity:"Critical", policyName:"No Admin Keys for Agents",          type:"Access Control", scope:"All Agents",       violationType:"Over-Privileged Access",    status:"Active", violations:23, lastTriggered:"2h ago"  },
    { severity:"Critical", policyName:"Rotate API Keys every 30 days",     type:"Rotation",       scope:"API Keys",         violationType:"Orphaned Identity",         status:"Active", violations:11, lastTriggered:"5h ago"  },
    { severity:"Critical", policyName:"Detect LLM Usage Spikes",           type:"Usage",          scope:"LLM Identities",   violationType:"Suspicious Activity",        status:"Active", violations:18, lastTriggered:"1h ago"  },

    // ── High (5) ──
    { severity:"High",     policyName:"Disable Orphaned Tokens",           type:"Governance",     scope:"All Identities",   violationType:"No Rotation",               status:"Active", violations:9,  lastTriggered:"3h ago"  },
    { severity:"High",     policyName:"Enforce Least Privilege",           type:"Access Control", scope:"All Identities",   violationType:"Over-Privileged Access",    status:"Active", violations:15, lastTriggered:"2h ago"  },
    { severity:"High",     policyName:"Detect New IP Usage",               type:"Usage",          scope:"Service Tokens",   violationType:"Unauthorized Provisioning", status:"Active", violations:11, lastTriggered:"2h ago"  },
    { severity:"High",     policyName:"Block Cross-Environment Token Reuse",type:"Access Control", scope:"All Identities",  violationType:"Anomalous Usage",           status:"Active", violations:8,  lastTriggered:"4h ago"  },
    { severity:"High",     policyName:"Revoke Admin OAuth Tokens",         type:"Governance",     scope:"OAuth Tokens",     violationType:"Over-Privileged Access",    status:"Active", violations:6,  lastTriggered:"6h ago"  },

    // ── Medium (10) ──
    { severity:"Medium",   policyName:"Restrict Infra Provisioning",       type:"Access Control", scope:"Cloud Agents",     violationType:"Anomalous Usage",           status:"Active", violations:5,  lastTriggered:"1h ago"  },
    { severity:"Medium",   policyName:"Monitor Slack Abuse",               type:"Usage",          scope:"Slack Tokens",     violationType:"Abuse / Excessive Actions", status:"Active", violations:6,  lastTriggered:"45m ago" },
    { severity:"Medium",   policyName:"Expire Unused Tokens (30d)",        type:"Governance",     scope:"All Identities",   violationType:"Dormant Privilege",         status:"Active", violations:10, lastTriggered:"1h ago"  },
    { severity:"Medium",   policyName:"Limit CI/CD Token Permissions",     type:"Access Control", scope:"CI Bots",          violationType:"Suspicious Activity",        status:"Draft",  violations:0,  lastTriggered:"Never"   },
    { severity:"Medium",   policyName:"Flag Tokens Unused > 14 Days",      type:"Governance",     scope:"All Identities",   violationType:"Dormant Privilege",         status:"Active", violations:5,  lastTriggered:"2h ago"  },
    { severity:"Medium",   policyName:"Restrict MCP Token Scope",          type:"Access Control", scope:"MCP Tokens",       violationType:"Over-Privileged Access",    status:"Active", violations:4,  lastTriggered:"3h ago"  },
    { severity:"Medium",   policyName:"Alert on Bulk Messaging",           type:"Usage",          scope:"Slack Tokens",     violationType:"Abuse / Excessive Actions", status:"Draft",  violations:0,  lastTriggered:"Never"   },
    { severity:"Medium",   policyName:"Require Rotation Every 60 Days",    type:"Rotation",       scope:"Service Tokens",   violationType:"No Rotation",               status:"Active", violations:8,  lastTriggered:"1h ago"  },
    { severity:"Medium",   policyName:"Detect Off-Hours API Usage",        type:"Usage",          scope:"API Keys",         violationType:"Suspicious Activity",        status:"Active", violations:5,  lastTriggered:"5h ago"  },
    { severity:"Medium",   policyName:"Enforce Token Expiry < 90d",        type:"Governance",     scope:"All Identities",   violationType:"Dormant Privilege",         status:"Active", violations:3,  lastTriggered:"12h ago" },

    // ── Low (6) ──
    { severity:"Low",      policyName:"Read Token Scope Enforcement",      type:"Access Control", scope:"API Keys",         violationType:"Over-Privileged Access",    status:"Active", violations:1,  lastTriggered:"1d ago"  },
    { severity:"Low",      policyName:"Log All OAuth Refresh Events",      type:"Governance",     scope:"OAuth Tokens",     violationType:"Orphaned Identity",         status:"Active", violations:0,  lastTriggered:"2d ago"  },
    { severity:"Low",      policyName:"Alert on New Agent Registration",   type:"Usage",          scope:"All Agents",       violationType:"Suspicious Activity",        status:"Draft",  violations:0,  lastTriggered:"Never"   },
    { severity:"Low",      policyName:"Monitor Staging Token Usage",       type:"Usage",          scope:"Service Tokens",   violationType:"Anomalous Usage",           status:"Draft",  violations:0,  lastTriggered:"Never"   },
    { severity:"Low",      policyName:"Flag Test Tokens in Prod",          type:"Access Control", scope:"API Keys",         violationType:"Unauthorized Provisioning", status:"Draft",  violations:0,  lastTriggered:"Never"   },
    { severity:"Low",      policyName:"Review Long-Lived Dev Tokens",      type:"Governance",     scope:"All Identities",   violationType:"Dormant Privilege",         status:"Draft",  violations:0,  lastTriggered:"Never"   },
];

const tableData = RAW_POLICIES.map((r, i) => ({
    ...r,
    id:            i + 1,
    severityOrder: SEV_ORD[r.severity],
    severityComp:  sevBadge(r.severity),
    policyNameComp:<Text variant="bodyMd" fontWeight="medium">{r.policyName}</Text>,
    statusComp:    statusBadge(r.status),
}));

// ── Computed summary ──────────────────────────────────────────────────────────
const TOTAL_P    = tableData.length;
const ACTIVE_P   = tableData.filter((r) => r.status === "Active").length;
const VIOLATIONS_TRIGGERED = tableData.reduce((sum, r) => sum + r.violations, 0);

const summaryItems = [
    { title: "Total Policies",        data: TOTAL_P.toLocaleString()              },
    { title: "Active Policies",       data: ACTIVE_P.toLocaleString()             },
    { title: "Violations Triggered",  data: VIOLATIONS_TRIGGERED.toLocaleString() },
];

// ── Headers ───────────────────────────────────────────────────────────────────
const headers = [
    { text: "Severity",       value: "severityComp",   title: "Severity"                           },
    { text: "Policy Name",    value: "policyNameComp", title: "Policy Name"                        },
    { text: "Type",           value: "type",           title: "Type",         type: CellType.TEXT  },
    { text: "Scope",          value: "scope",          title: "Scope",        type: CellType.TEXT  },
    { text: "Violation Type", value: "violationType",  title: "Violation Type",type: CellType.TEXT },
    { text: "Status",         value: "statusComp",     title: "Status"                             },
    { text: "Violations",     value: "violations",     title: "Violations",   type: CellType.TEXT  },
    { text: "Last Triggered", value: "lastTriggered",  title: "Last Triggered",type: CellType.TEXT },
];

const sortOptions = [
    { label: "Severity",    value: "severity asc",    directionLabel: "Critical first", sortKey: "severityOrder", columnIndex: 0 },
    { label: "Severity",    value: "severity desc",   directionLabel: "Low first",      sortKey: "severityOrder", columnIndex: 0 },
    { label: "Violations",  value: "violations desc",  directionLabel: "Most",           sortKey: "violations",    columnIndex: 6 },
    { label: "Violations",  value: "violations asc",   directionLabel: "Least",          sortKey: "violations",    columnIndex: 6 },
];

// ── Page ──────────────────────────────────────────────────────────────────────
export default function PoliciesPage() {
    return (
        <PageWithMultipleCards
            title="Policies"
            isFirstPage
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <GithubSimpleTable
                    key="policies-table"
                    data={tableData}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    selectable={false}
                    mode={IndexFiltersMode.Default}
                    headings={headers}
                    useNewRow={true}
                    condensedHeight={true}
                />,
            ]}
        />
    );
}
