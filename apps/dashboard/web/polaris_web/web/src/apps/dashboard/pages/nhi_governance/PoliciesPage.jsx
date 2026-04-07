import { useState, useMemo, useEffect, useRef } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, Box, Button, HorizontalStack, Modal, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import SampleData from "../../components/shared/SampleData";
import DropdownSearch from "../../components/shared/DropdownSearch";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import PolicyDetailsPanel from "./PolicyDetailsPanel";
import { violationsTableData, unresolvedPolicyName } from "./nhiViolationsData";

const definedTableTabs = ["All", "Active", "Inactive", "Draft"];
const resourceName = { singular: "policy", plural: "policies" };

// ── Violation bubbles — colors from API Security discovery page ────────────────
function ViolationBubbles({ critical = 0, high = 0, medium = 0, low = 0 }) {
    if (!critical && !high && !medium && !low)
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
            {dot(low,      "#E4E5E7", "#202223")}
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
const INITIAL_POLICIES = [
    {
        policyName:    "No Admin Credentials for Agent Identities",
        violCrit: 3,  violHigh: 12, violMed: 5,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Ethan Carter",
        created:       "30d ago",
    },
    {
        policyName:    "Enforce Least Privilege on Credentials",
        violCrit: 8,  violHigh: 22, violMed: 0,
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
        scope:         { primary: "Cursor", extra: 2 },
        agents:        ["Cursor", "Claude CLI", "VS Code"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Marcus Hale",
        created:       "60d ago",
    },
    {
        policyName:    "Detect Unusual Usage Patterns",
        violCrit: 0,  violHigh: 14, violMed: 7,
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "3h ago",
        lastModified:  "Marcus Hale",
        created:       "20d ago",
    },
    {
        policyName:    "Restrict Access to Sensitive Resources",
        violCrit: 0,  violHigh: 12, violMed: 4,
        scope:         { primary: "Cursor", extra: 2 },
        agents:        ["Cursor", "VS Code", "Windsurf"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Marcus Hale",
        created:       "50d ago",
    },
    {
        policyName:    "Disable Dormant Credentials (30+ days)",
        violCrit: 0,  violHigh: 0,  violMed: 10,
        scope:         { primary: "Claude Desktop", extra: 5 },
        agents:        ["Claude Desktop","Cursor","Claude CLI","VS Code","Windsurf","Antigravity"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Ethan Carter",
        created:       "40d ago",
    },
    {
        policyName:    "Prevent Cross-Service Credential Usage",
        violCrit: 0,  violHigh: 22, violMed: 0,
        scope:         { primary: "Claude Desktop", extra: 5 },
        agents:        ["Claude Desktop","Cursor","Claude CLI","VS Code","Windsurf","Antigravity"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Ethan Carter",
        created:       "35d ago",
    },
    {
        policyName:    "Limit Automation Without Approval",
        violCrit: 0,  violHigh: 0,  violMed: 14,
        scope:         { primary: "Claude CLI", extra: 2 },
        agents:        ["Claude CLI", "VS Code", "Windsurf"],
        status:        "Inactive",
        lastTriggered: "45m ago",
        lastModified:  "Marcus Hale",
        created:       "25d ago",
    },
    {
        policyName:    "Restrict Code Execution Permissions",
        violCrit: 0,  violHigh: 0,  violMed: 18,
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

// ── Last-triggered derived from violations ─────────────────────────────────────
function parseMins(discovered) {
    if (!discovered || discovered === "Never") return Infinity;
    if (discovered === "Now") return 0;
    const m = discovered.match(/^(\d+)(m|h|d)\s+ago$/);
    if (!m) return Infinity;
    const n = parseInt(m[1]);
    return m[2] === "m" ? n : m[2] === "h" ? n * 60 : n * 1440;
}

function getPolicyViolations(policyName) {
    const originalName = unresolvedPolicyName(policyName);
    return violationsTableData.filter(
        (v) => (typeof v.policy === "object" ? v.policy.primary : v.policy) === originalName
    );
}

function getLastTriggered(viols) {
    if (!viols.length) return "Never";
    return viols.reduce((best, v) => parseMins(v.discovered) < parseMins(best.discovered) ? v : best).discovered;
}

function buildTableData(rawList) {
    return rawList.map((r, i) => {
        const viols    = getPolicyViolations(r.policyName);
        const violCrit = viols.filter((v) => v.severity === "Critical").length;
        const violHigh = viols.filter((v) => v.severity === "High").length;
        const violMed  = viols.filter((v) => v.severity === "Medium").length;
        const violLow  = viols.filter((v) => v.severity === "Low").length;
        return {
        ...r,
        id:              i + 1,
        violCrit, violHigh, violMed, violLow,
        totalViolations: violCrit + violHigh + violMed + violLow,
        lastTriggered:   getLastTriggered(viols),
        policyNameComp:  <Text variant="bodyMd" fontWeight="medium">{r.policyName}</Text>,
        violationsComp:  <ViolationBubbles critical={violCrit} high={violHigh} medium={violMed} low={violLow} />,
        scopeComp:       <ScopeCell scope={r.scope} agents={r.agents} />,
        statusComp:      statusBadge(r.status),
        };
    });
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

const AGENT_OPTIONS = [
    { label: "Cursor",         value: "Cursor" },
    { label: "Claude CLI",     value: "Claude CLI" },
    { label: "Claude Desktop", value: "Claude Desktop" },
    { label: "VS Code",        value: "VS Code" },
    { label: "Windsurf",       value: "Windsurf" },
    { label: "Antigravity",    value: "Antigravity" },
];

const TEMPLATE_OPTIONS = [
    { label: "Select Template",      value: "" },
    { label: "Credential Security",  value: "credential_security" },
    { label: "Access Control",       value: "access_control" },
    { label: "Usage Monitoring",     value: "usage_monitoring" },
    { label: "Automation Controls",  value: "automation_controls" },
    { label: "Lifecycle Management", value: "lifecycle_management" },
];

const TEMPLATE_YAML = {
    credential_security: `id: pol-new
name: <policy name>
description: >
  Detects credentials that exceed their intended permission scope
  or present elevated risk across agent workflows.
severity: high
status: active
type: access_control

scope:
  identity_type: credential
  agents: all

condition:
  operator: OR
  rules:
    - field: credential.permission_level
      operator: equals
      value: admin

    - field: credential.unused_permissions_ratio
      operator: greater_than
      value: 0.5

trigger:
  on: real_time

actions:
  on_violation:
    - notify: security-team@acme.com`,

    access_control: `id: pol-new
name: <policy name>
description: >
  Restricts agent access to classified or sensitive resources
  unless explicitly approved for the workflow.
severity: critical
status: active
type: access_control

scope:
  identity_type: all
  agents: all

condition:
  rules:
    - field: resource.classification
      operator: in
      value:
        - pii
        - financial
        - production_db

    - field: access.approval_status
      operator: not_equals
      value: approved

trigger:
  on: real_time

actions:
  on_violation:
    - block: true
    - notify: compliance@acme.com`,

    usage_monitoring: `id: pol-new
name: <policy name>
description: >
  Monitors credentials for anomalous usage patterns including
  request spikes, off-hours access, and unusual endpoints.
severity: high
status: active
type: anomaly_detection

scope:
  identity_type: all
  agents: all

condition:
  operator: OR
  rules:
    - field: credential.request_rate_multiplier
      operator: greater_than
      value: 3.0

    - field: credential.access_network
      operator: not_in
      value: trusted_networks

trigger:
  on: real_time

actions:
  on_violation:
    - notify: security-team@acme.com`,

    automation_controls: `id: pol-new
name: <policy name>
description: >
  Requires human approval before agents execute bulk automated
  actions or destructive operations.
severity: medium
status: active
type: automation_control

scope:
  identity_type: all
  agents: all

condition:
  operator: AND
  rules:
    - field: action.bulk_operation
      operator: equals
      value: true

    - field: action.approval_status
      operator: not_equals
      value: approved

trigger:
  on: real_time

actions:
  on_violation:
    - block: true
    - request_approval: manager`,

    lifecycle_management: `id: pol-new
name: <policy name>
description: >
  Enforces credential lifecycle rules such as rotation schedules
  and dormancy detection across all agent identities.
severity: medium
status: active
type: credential_lifecycle

scope:
  identity_type: credential
  agents: all

condition:
  rules:
    - field: credential.last_rotated_days
      operator: greater_than
      value: 30

trigger:
  on: scheduled
  evaluate_every: 24h

actions:
  on_violation:
    - notify: devops@acme.com`,
};

const BLANK_YAML = `id: pol-new\nname: <policy name>\ndescription: >\n  Write your policy description here.\nseverity: medium\nstatus: draft\ntype: access_control\n\nscope:\n  identity_type: all\n  agents: all\n\ncondition:\n  rules:\n    - field: \n      operator: \n      value: \n\ntrigger:\n  on: real_time\n\nactions:\n  on_violation:\n    - notify: security-team@acme.com`;

const MAX_NAME = 64;

function CreatePolicyModal({ open, onClose, onCreatePolicy }) {
    const [name, setName]                     = useState("");
    const [selectedAgents, setSelectedAgents] = useState([]);
    const [yamlKey, setYamlKey]               = useState(0);
    const yamlRef                             = useRef(BLANK_YAML);

    const handleClose = () => {
        setName(""); setSelectedAgents([]); yamlRef.current = BLANK_YAML; setYamlKey(0);
        onClose();
    };

    const buildAndCreate = (status) => {
        const policyName = name.trim() || "Untitled Policy";
        const allSelected = selectedAgents.length === 0 || selectedAgents.length === AGENT_OPTIONS.length;
        const agents = allSelected ? ["All Agents"] : selectedAgents;
        const scope  = allSelected
            ? { primary: "All Agents" }
            : { primary: agents[0], ...(agents.length > 1 ? { extra: agents.length - 1 } : {}) };
        onCreatePolicy({
            policyName, agents, scope, status, yaml: yamlRef.current,
            violCrit: 0, violHigh: 0, violMed: 0,
            lastModified: "Fenil Shah",
            created: "Just now",
        });
        handleClose();
    };

    return (
        <Modal
            open={open}
            onClose={handleClose}
            title="Create Policy"
            large
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <HorizontalStack gap="4" blockAlign="end" wrap={false}>
                        <div style={{ flex: "1 1 0", minWidth: 0 }}>
                            <TextField
                                label="Name"
                                value={name}
                                onChange={setName}
                                maxLength={MAX_NAME}
                                showCharacterCount
                                autoComplete="off"
                                placeholder="e.g. Enforce Least Privilege on Credentials"
                            />
                        </div>
                        <div style={{ flex: "0 0 220px" }}>
                            <VerticalStack gap="1">
                                <Text variant="bodySm" fontWeight="medium">Select Agents</Text>
                                <DropdownSearch
                                    id="create-policy-agents"
                                    optionsList={AGENT_OPTIONS}
                                    setSelected={setSelectedAgents}
                                    preSelected={selectedAgents}
                                    allowMultiple
                                    placeholder="All Selected"
                                    itemName="agent"
                                />
                            </VerticalStack>
                        </div>
                    </HorizontalStack>
                    <div style={{ height: 420 }}>
                        <SampleData
                            key={yamlKey}
                            data={{ message: yamlRef.current }}
                            editorLanguage="custom_yaml"
                            readOnly={false}
                            getEditorData={(val) => { yamlRef.current = val; }}
                            minHeight="420px"
                        />
                    </div>
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Button onClick={() => buildAndCreate("Draft")}>Create Draft</Button>
                        <HorizontalStack gap="2">
                            <Button onClick={handleClose}>Cancel</Button>
                            <Button primary onClick={() => buildAndCreate("Active")}>Create Policy</Button>
                        </HorizontalStack>
                    </HorizontalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}

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

    const [rawPolicies, setRawPolicies]           = useState(() => {
        try {
            const stored = localStorage.getItem("nhi_policies_v1");
            if (stored) return JSON.parse(stored);
        } catch (_) {}
        return INITIAL_POLICIES;
    });
    const [selectedTab, setSelectedTab]           = useState(initialSelectedTab);
    const [selected, setSelected]                 = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [showDeleteModal, setShowDeleteModal]   = useState(false);
    const [showCreateModal, setShowCreateModal]   = useState(false);
    const [selectedPolicy, setSelectedPolicy]     = useState(null);
    const [showPolicyPanel, setShowPolicyPanel]   = useState(false);

    useEffect(() => {
        try { localStorage.setItem("nhi_policies_v1", JSON.stringify(rawPolicies)); } catch (_) {}
    }, [rawPolicies]);

    const tableData = useMemo(() => buildTableData(rawPolicies), [rawPolicies]);

    const summaryItems = useMemo(() => [
        { title: "Total Policies",             data: tableData.length.toLocaleString() },
        { title: "Total Violations Triggered", data: tableData.reduce((s, r) => s + r.totalViolations, 0).toLocaleString() },
    ], [tableData]);

    const handleCreatePolicy = (newPolicy) => {
        setRawPolicies((prev) => [...prev, newPolicy]);
    };

    const handlePolicySave = ({ policyName, yaml }) => {
        const lastModified = "Fenil Shah";
        const oldName = selectedPolicy.policyName;

        // Persist old→new name remap so violations table/panel can resolve it at render time
        if (oldName !== policyName) {
            try {
                const map = JSON.parse(localStorage.getItem("nhi_policy_name_map") || "{}");
                // If oldName is itself a renamed version, find the original key
                const originalKey = Object.entries(map).find(([, v]) => v === oldName)?.[0] ?? oldName;
                if (originalKey !== policyName) {
                    map[originalKey] = policyName;
                } else {
                    delete map[originalKey]; // renamed back to original — clear the entry
                }
                localStorage.setItem("nhi_policy_name_map", JSON.stringify(map));
            } catch (_) {}
        }

        setRawPolicies((prev) =>
            prev.map((p) =>
                p.policyName === oldName
                    ? { ...p, policyName, yaml, lastModified }
                    : p
            )
        );
        setSelectedPolicy((prev) => ({ ...prev, policyName, yaml, lastModified }));
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

    return (
        <>
        <PageWithMultipleCards
            title={policiesPageTitle}
            isFirstPage
            primaryAction={{ content: "Create Policy", onAction: () => setShowCreateModal(true) }}
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
                    onRowClick={(r) => { setSelectedPolicy(r); setShowPolicyPanel(true); }}
                    rowClickable={true}
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
        <CreatePolicyModal open={showCreateModal} onClose={() => setShowCreateModal(false)} onCreatePolicy={handleCreatePolicy} />
        {selectedPolicy && (
            <PolicyDetailsPanel
                key={selectedPolicy.id}
                row={selectedPolicy}
                show={showPolicyPanel}
                setShow={setShowPolicyPanel}
                onSave={handlePolicySave}
            />
        )}
        </>
    );
}
