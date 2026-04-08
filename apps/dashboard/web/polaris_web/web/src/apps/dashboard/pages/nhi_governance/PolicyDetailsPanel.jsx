import { useRef, useState } from "react";
import { ActionList, Badge, Box, Button, Divider, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import { IndexFiltersMode } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import SampleData from "../../components/shared/SampleData";
import { AgentIcon, violationsTableData, violationsHeaders, violationsSortOptions, unresolvedPolicyName } from "./nhiViolationsData";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";
const STATUS_COLOR = { Active: "success", Inactive: "", Draft: "warning" };

// ── Per-policy YAML templates ─────────────────────────────────────────────────
const POLICY_YAML_MAP = {
    "No Admin Credentials for Agent Identities": `id: pol-001
name: No Admin Credentials for Agent Identities
description: >
  Ensures no agent identity is granted Admin-level IAM permissions.
  Agent keys must follow least privilege and only hold permissions
  required for their specific task.
severity: critical
status: active
type: access_control

scope:
  identity_type: agent
  environments:
    - prod
    - staging
    - dev
  agents: all  # or specify: [cursor, claude-cli]

condition:
  operator: AND
  rules:
    - field: identity.permission_level
      operator: equals
      value: admin

    - field: identity.iam_policies
      operator: contains_any
      value:
        - AdministratorAccess
        - PowerUserAccess

    # optional stricter check
    - field: identity.granted_actions
      operator: greater_than
      value: 20  # more than 20 IAM actions = flag it

trigger:
  on: real_time  # real_time | scheduled | on_change
  evaluate_every: null  # only for scheduled

actions:
  on_violation:
    - notify: security-team@acme.com
    - severity: critical`,

    "Enforce Least Privilege on Credentials": `id: pol-002
name: Enforce Least Privilege on Credentials
description: >
  Requires every credential issued to an agent to carry only the
  minimum permissions necessary for the specific task at hand.
severity: high
status: active
type: access_control

scope:
  identity_type: credential
  agents: all

condition:
  operator: OR
  rules:
    - field: credential.permission_scope
      operator: equals
      value: write_all

    - field: credential.unused_permissions_ratio
      operator: greater_than
      value: 0.5  # more than 50% permissions unused

    - field: credential.scope_count
      operator: greater_than
      value: 10

trigger:
  on: scheduled
  evaluate_every: 6h

actions:
  on_violation:
    - notify: security-team@acme.com
    - create_ticket: jira`,

    "Rotate API Keys Every 30 Days": `id: pol-003
name: Rotate API Keys Every 30 Days
description: >
  Mandates all API keys used by agents are rotated at least once
  every 30 days. Long-lived keys accumulate risk over time.
severity: high
status: active
type: credential_lifecycle

scope:
  identity_type: api_key
  agents:
    - cursor
    - claude-cli
    - vs-code

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
    - notify: devops@acme.com
    - auto_revoke: false  # set true to auto-rotate`,

    "Detect Unusual Usage Patterns": `id: pol-004
name: Detect Unusual Usage Patterns
description: >
  Monitors credentials for anomalous behavior including request
  volume spikes, access from unexpected networks, or calls to
  endpoints outside normal agent scope.
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
      value: 3.0  # 3x above baseline

    - field: credential.access_network
      operator: not_in
      value: trusted_networks

    - field: credential.endpoint_deviation_score
      operator: greater_than
      value: 0.8

trigger:
  on: real_time

actions:
  on_violation:
    - notify: security-team@acme.com
    - severity: high`,

    "Restrict Access to Sensitive Resources": `id: pol-005
name: Restrict Access to Sensitive Resources
description: >
  Prevents agents from accessing HR data stores, production
  databases, financial records, and customer PII unless
  explicitly approved for that specific workflow.
severity: critical
status: active
type: access_control

scope:
  identity_type: all
  agents:
    - cursor
    - vs-code
    - windsurf

condition:
  rules:
    - field: resource.classification
      operator: in
      value:
        - pii
        - financial
        - hr
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

    "Disable Dormant Credentials (30+ days)": `id: pol-006
name: Disable Dormant Credentials (30+ days)
description: >
  Automatically flags or disables any credential that has not
  been actively used in the past 30 days.
severity: medium
status: active
type: credential_lifecycle

scope:
  identity_type: all
  agents:
    - claude-desktop
    - cursor
    - claude-cli
    - vs-code
    - windsurf
    - antigravity

condition:
  rules:
    - field: credential.last_used_days
      operator: greater_than
      value: 30

trigger:
  on: scheduled
  evaluate_every: 24h

actions:
  on_violation:
    - disable_credential: true
    - notify: devops@acme.com`,

    "Prevent Cross-Service Credential Usage": `id: pol-007
name: Prevent Cross-Service Credential Usage
description: >
  Detects when a single credential is being used to access
  multiple unrelated services, breaking service isolation.
severity: high
status: active
type: access_control

scope:
  identity_type: credential
  agents:
    - claude-desktop
    - cursor
    - claude-cli
    - vs-code
    - windsurf
    - antigravity

condition:
  rules:
    - field: credential.distinct_service_count
      operator: greater_than
      value: 1

    - field: credential.services_accessed
      operator: cross_domain
      value: true

trigger:
  on: real_time

actions:
  on_violation:
    - notify: security-team@acme.com
    - severity: high`,

    "Limit Automation Without Approval": `id: pol-008
name: Limit Automation Without Approval
description: >
  Requires human-in-the-loop approval before an agent can
  execute bulk automated actions such as mass messaging,
  resource creation, or destructive workflows.
severity: medium
status: inactive
type: automation_control

scope:
  identity_type: all
  agents:
    - claude-cli
    - vs-code
    - windsurf

condition:
  operator: AND
  rules:
    - field: action.bulk_operation
      operator: equals
      value: true

    - field: action.approval_status
      operator: not_equals
      value: approved

    - field: action.item_count
      operator: greater_than
      value: 10

trigger:
  on: real_time

actions:
  on_violation:
    - block: true
    - request_approval: manager`,

    "Restrict Code Execution Permissions": `id: pol-009
name: Restrict Code Execution Permissions
description: >
  Limits permissions available to agents that execute arbitrary
  code via shell tools or code interpreters.
severity: medium
status: inactive
type: execution_control

scope:
  identity_type: all
  agents: all

condition:
  operator: OR
  rules:
    - field: execution.shell_access
      operator: equals
      value: unrestricted

    - field: execution.filesystem_write
      operator: outside
      value: allowed_paths

    - field: execution.network_egress
      operator: equals
      value: unrestricted

trigger:
  on: real_time

actions:
  on_violation:
    - block: true
    - notify: security-team@acme.com`,

    "Enforce Scoped Access for OAuth Tokens": `id: pol-010
name: Enforce Scoped Access for OAuth Tokens
description: >
  Validates all OAuth tokens issued to agents request only the
  specific scopes required for their declared purpose.
severity: low
status: draft
type: access_control

scope:
  identity_type: oauth_token
  agents: all

condition:
  rules:
    - field: token.requested_scopes
      operator: exceeds
      value: declared_minimum_scopes

    - field: token.broad_scope_flags
      operator: contains_any
      value:
        - read:all
        - write:all
        - admin

trigger:
  on: scheduled
  evaluate_every: 12h

actions:
  on_violation:
    - notify: security-team@acme.com
    - severity: low`,
};

// ── Exported textarea-based code editor (used in Create Policy modal) ─────────
export function CodeEditor({ value, onChange, minHeight = 300 }) {
    const lineNumRef = useRef(null);
    const lines = (value || "").split("\n");
    return (
        <div style={{
            display: "flex",
            fontFamily: "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace",
            fontSize: 13, border: "1px solid #E4E5E7", borderRadius: 8,
            overflow: "hidden", minHeight,
        }}>
            <div ref={lineNumRef} style={{
                background: "#F6F6F7", color: "#8C9196",
                padding: "12px 8px 12px 4px", textAlign: "right",
                userSelect: "none", overflowY: "hidden",
                lineHeight: "19.5px", minWidth: 36,
                borderRight: "1px solid #E4E5E7", flexShrink: 0,
            }}>
                {lines.map((_, i) => <div key={i}>{i + 1}</div>)}
            </div>
            <textarea
                value={value}
                onChange={onChange ? (e) => onChange(e.target.value) : undefined}
                readOnly={!onChange}
                onScroll={(e) => { if (lineNumRef.current) lineNumRef.current.scrollTop = e.target.scrollTop; }}
                style={{
                    flex: 1, padding: "12px 12px 12px 8px",
                    border: "none", outline: "none", resize: "none",
                    fontFamily: "inherit", fontSize: 13,
                    lineHeight: "19.5px", background: "white",
                    color: "#202223", overflowY: "auto",
                }}
                spellCheck={false}
            />
        </div>
    );
}

export default function PolicyDetailsPanel({ row, show, setShow, onSave }) {
    const [actionActive, setActionActive] = useState(false);
    const [isDirty, setIsDirty]           = useState(false);

    // Frozen at mount time via lazy initializer — never changes even if row.policyName
    // is updated after a save, so SampleData never gets a new data prop and won't reset
    // the editor or lose the cursor position.
    // row.yaml is the persisted YAML from localStorage (set on previous saves).
    const [initialYaml] = useState(
        () => row.yaml
            || POLICY_YAML_MAP[row.policyName]
            || `id: pol-xxx\nname: ${row.policyName}\nstatus: ${row.status.toLowerCase()}\n`
    );

    // yamlRef tracks live edits without triggering re-renders
    const yamlRef     = useRef(initialYaml);
    // savedYamlRef is the baseline for dirty detection — updates on every save
    const savedYamlRef = useRef(initialYaml);

    // Resolve fresh every render — never stale, picks up any rename immediately.
    // unresolvedPolicyName("New Name") → looks up nhi_policy_name_map → returns original key
    // that violations still reference.
    const originalPolicyName = unresolvedPolicyName(row.policyName);

    const policyViolations = violationsTableData.filter((v) => {
        const names = typeof v.policy === "object"
            ? [v.policy.primary, ...(v.policy.extras || [])]
            : [v.policy];
        return names.some((n) => n === originalPolicyName || n === row.policyName);
    });
    const violCrit = policyViolations.filter((v) => v.severity === "Critical").length;
    const violHigh = policyViolations.filter((v) => v.severity === "High").length;
    const violMed  = policyViolations.filter((v) => v.severity === "Medium").length;
    const violLow  = policyViolations.filter((v) => v.severity === "Low").length;

    const handleViolationClick = (violationRow) => {
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(violationRow));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
    };

    const handleSave = () => {
        const yaml = yamlRef.current;
        const nameMatch = yaml.match(/^name:\s*(.+)$/m);
        const newName = nameMatch ? nameMatch[1].trim() : row.policyName;
        savedYamlRef.current = yaml;   // update baseline so next edit compares against saved state
        setIsDirty(false);
        onSave && onSave({ policyName: newName, yaml });
    };

    const scopeLabel = row.agents && row.agents.length > 1
        ? `${row.agents[0]} +${row.agents.length - 1}`
        : row.agents && row.agents.length === 1
            ? row.agents[0]
            : "All Agents";

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <VerticalStack gap="1">
                <HorizontalStack align="space-between" blockAlign="center">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text as="span" variant="headingMd" fontWeight="semibold">{row.policyName}</Text>
                        <Badge status={STATUS_COLOR[row.status] || ""}>{row.status}</Badge>
                        {[
                            { count: violCrit, bg: "#DF2909", fg: "white"   },
                            { count: violHigh, bg: "#FED3D1", fg: "#202223" },
                            { count: violMed,  bg: "#FFD79D", fg: "#202223" },
                            { count: violLow,  bg: "#E4E5E7", fg: "#202223" },
                        ].map(({ count, bg, fg }) => count > 0 && (
                            <span key={bg} style={{
                                background: bg, color: fg,
                                borderRadius: "50%", width: 20, height: 20,
                                display: "inline-flex", alignItems: "center",
                                justifyContent: "center", fontSize: 11, fontWeight: 600, flexShrink: 0,
                            }}>{count}</span>
                        ))}
                    </HorizontalStack>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Button size="slim" disabled={!isDirty} onClick={handleSave}>Save</Button>
                        <Popover
                            active={actionActive}
                            activator={
                                <Button size="slim" disclosure onClick={() => setActionActive((v) => !v)}>
                                    Action
                                </Button>
                            }
                            onClose={() => setActionActive(false)}
                        >
                            <ActionList items={[
                                ...(row.status === "Active"
                                    ? [{ content: "Mark as Inactive", onAction: () => setActionActive(false) }]
                                    : [{ content: "Mark as Active",   onAction: () => setActionActive(false) }]
                                ),
                                { content: "Delete Policy", destructive: true, onAction: () => setActionActive(false) },
                            ]} />
                        </Popover>
                    </HorizontalStack>
                </HorizontalStack>
                <HorizontalStack gap="0">
                    <Text variant="bodySm" color="subdued">{scopeLabel}</Text>
                    <Text variant="bodySm" color="subdued">&nbsp;|&nbsp;</Text>
                    <Text variant="bodySm" color="subdued">Last Triggered {row.lastTriggered}</Text>
                    <Text variant="bodySm" color="subdued">&nbsp;|&nbsp;</Text>
                    <Text variant="bodySm" color="subdued">Last Modified by {row.lastModified}</Text>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );

    // ── Policy (YAML) tab — primary ───────────────────────────────────────────
    const policyTab = {
        id: "policy",
        content: "Policy",
        component: (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="2" paddingBlockEnd="4">
                <div style={{ height: "calc(100vh - 260px)", minHeight: 300 }}>
                    <SampleData
                        data={{ message: initialYaml }}
                        editorLanguage="custom_yaml"
                        readOnly={false}
                        getEditorData={(val) => {
                            yamlRef.current = val;
                            setIsDirty(val !== savedYamlRef.current);
                        }}
                        minHeight="calc(100vh - 260px)"
                    />
                </div>
            </Box>
        ),
    };

    // ── Overview tab ──────────────────────────────────────────────────────────
    const overviewTab = {
        id: "overview",
        content: "Overview",
        component: (
            <Box padding="4">
                <VerticalStack gap="5">
                    <HorizontalStack gap="8" blockAlign="start" wrap>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Status</Text>
                            <Badge status={STATUS_COLOR[row.status] || ""}>{row.status}</Badge>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Total Violations</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{policyViolations.length}</Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Last Triggered</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.lastTriggered}</Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Created</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.created}</Text>
                        </VerticalStack>
                    </HorizontalStack>
                    <Divider />
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Agents in scope</Text>
                        <VerticalStack gap="2">
                            {(row.agents || []).map((agent) => (
                                <HorizontalStack key={agent} gap="2" blockAlign="center">
                                    <AgentIcon name={agent} />
                                    <Text variant="bodyMd">{agent}</Text>
                                </HorizontalStack>
                            ))}
                        </VerticalStack>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Violations tab ────────────────────────────────────────────────────────
    const violationsTab = {
        id: "violations",
        content: `Violations${policyViolations.length > 0 ? ` ${policyViolations.length}` : ""}`,
        component: policyViolations.length > 0 ? (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="4">
                <GithubSimpleTable
                    data={policyViolations}
                    headers={violationsHeaders}
                    resourceName={{ singular: "violation", plural: "violations" }}
                    sortOptions={violationsSortOptions}
                    filters={[]}
                    selectable={false}
                    mode={IndexFiltersMode.Default}
                    headings={violationsHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    onRowClick={handleViolationClick}
                    rowClickable={true}
                />
            </Box>
        ) : (
            <Box padding="4">
                <Text variant="bodyMd" color="subdued">No violations triggered by this policy.</Text>
            </Box>
        ),
    };

    return (
        <FlyLayout
            title="Policy details"
            show={show}
            setShow={setShow}
            components={[
                <TitleComponent key="title" />,
                <LayoutWithTabs
                    key={row.id}
                    tabs={[policyTab, overviewTab, violationsTab]}
                    currTab={() => {}}
                    noLoading
                />,
            ]}
            showDivider
            newComp
        />
    );
}
