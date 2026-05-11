// ── Static data shared across NHI Governance pages ────────────────────────────

// ── Atlas: 10 Critical Curated ────────────────────────────────────────────────
// Agents from MongoDB (ENDPOINT-tagged mcp-clients): cursor, vscode, windsurf,
// claude-cli, antigravity, claude-desktop. Owner from john.doe@bankone.com
export const CRITICAL_CURATED = [
    { identityName:"copilot-api-key",    agent:"Windsurf",      type:"API Key",      access:"Read/Write", violCrit:3, violHigh:0, violMed:2, lastUsed:"2h ago",  expiryStatus:"2d left",                owner:"John Doe" },
    { identityName:"slack-token",        agent:"Claude CLI",    type:"Bearer Token", access:"Write",      violCrit:1, violHigh:3, violMed:0, lastUsed:"45m ago", expiryStatus:"Rotation Due in 2 days", owner:"John Doe" },
    { identityName:"atlassian-api-key",  agent:"Cursor",        type:"API Key",      access:"Read/Write", violCrit:1, violHigh:3, violMed:2, lastUsed:"3h ago",  expiryStatus:"60d left",               owner:"John Doe" },
    { identityName:"github-api-key",     agent:"Claude CLI",    type:"API Key",      access:"Read/Write", violCrit:2, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"Rotation due today",     owner:"John Doe" },
    { identityName:"notion-api-key",     agent:"Cursor",        type:"API Key",      access:"Read",       violCrit:2, violHigh:4, violMed:1, lastUsed:"Never",   expiryStatus:"15d left",               owner:"John Doe" },
    { identityName:"filesystem-token",   agent:"Claude CLI",    type:"Bearer Token", access:"Admin",      violCrit:1, violHigh:0, violMed:1, lastUsed:"2d ago",  expiryStatus:"60d left",               owner:"John Doe" },
    { identityName:"razorpay-token",     agent:"Antigravity",   type:"Bearer Token", access:"Read",       violCrit:2, violHigh:5, violMed:0, lastUsed:"6h ago",  expiryStatus:"7d left",                owner:"John Doe" },
    { identityName:"docker-token",       agent:"Claude CLI",    type:"API Key",      access:"Admin",      violCrit:1, violHigh:2, violMed:1, lastUsed:"Never",   expiryStatus:"No expiry",              owner:"John Doe" },
    { identityName:"playwright-token",   agent:"Cursor",        type:"Bearer Token", access:"Write",      violCrit:3, violHigh:0, violMed:2, lastUsed:"5h ago",  expiryStatus:"Rotation due today",     owner:"John Doe" },
    { identityName:"kite-api-key",       agent:"Cursor",        type:"API Key",      access:"Read",       violCrit:1, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"Expired 1d ago",         owner:"John Doe" },
];

// ── Atlas: Non-critical curated ───────────────────────────────────────────────
export const NON_CRITICAL_CURATED = [
    { identityName:"postgres-token",     agent:"VS Code",       type:"Bearer Token", access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"3h ago",  expiryStatus:"15d left",  owner:"John Doe" },
    { identityName:"notion-mcp-token",   agent:"VS Code",       type:"Bearer Token", access:"Read",       violCrit:0, violHigh:1, violMed:1, lastUsed:"6h ago",  expiryStatus:"5d left",   owner:"John Doe" },
    { identityName:"jetbrains-token",    agent:"Claude Desktop",type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"1d left",   owner:"John Doe" },
    { identityName:"squareup-token",     agent:"Cursor",        type:"Bearer Token", access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"No expiry", owner:"John Doe" },
    { identityName:"alphavantage-key",   agent:"VS Code",       type:"API Key",      access:"Read",       violCrit:0, violHigh:3, violMed:1, lastUsed:"3h ago",  expiryStatus:"No expiry", owner:"John Doe" },
];

// ── Argus: Critical Curated ───────────────────────────────────────────────────
// Agents from MongoDB gen-ai tagged collections. No owner in Argus.
export const ARGUS_CRITICAL_CURATED = [
    // gen-ai:LLM
    { identityName:"openai-api-key",         agent:"OpenAI",         type:"API Key",      access:"Read/Write", violCrit:2, violHigh:4, violMed:1, lastUsed:"Never",   expiryStatus:"15d left"           },
    { identityName:"cohere-api-key",         agent:"Cohere",         type:"API Key",      access:"Admin",      violCrit:1, violHigh:2, violMed:1, lastUsed:"Never",   expiryStatus:"No expiry"          },
    { identityName:"perplexity-api-key",     agent:"Perplexity",     type:"API Key",      access:"Read/Write", violCrit:3, violHigh:0, violMed:2, lastUsed:"4h ago",  expiryStatus:"Rotation due today" },
    { identityName:"langchain-api-key",      agent:"LangChain",      type:"API Key",      access:"Read/Write", violCrit:1, violHigh:3, violMed:1, lastUsed:"30m ago", expiryStatus:"No expiry"          },
    // gen-ai:MCP Server
    { identityName:"k9s-mcp-token",          agent:"K9s Trade",      type:"Bearer Token", access:"Read/Write", violCrit:2, violHigh:3, violMed:1, lastUsed:"2h ago",  expiryStatus:"No expiry"          },
    { identityName:"vulnerable-mcp-token",   agent:"Vulnerable MCP", type:"API Key",      access:"Admin",      violCrit:3, violHigh:2, violMed:1, lastUsed:"1h ago",  expiryStatus:"Expired 3d ago"     },
    { identityName:"akplatform-mcp-token",   agent:"AK Platform",    type:"Bearer Token", access:"Read/Write", violCrit:1, violHigh:2, violMed:0, lastUsed:"3h ago",  expiryStatus:"5d left"            },
    // gen-ai:AI Agent (critical)
    { identityName:"replicate-api-key",      agent:"Replicate",      type:"API Key",      access:"Write",      violCrit:2, violHigh:5, violMed:0, lastUsed:"3h ago",  expiryStatus:"7d left"            },
    { identityName:"n8n-api-key",            agent:"N8N",            type:"API Key",      access:"Read/Write", violCrit:2, violHigh:1, violMed:1, lastUsed:"30m ago", expiryStatus:"No expiry"          },
    { identityName:"jasper-api-key",         agent:"Jasper",         type:"API Key",      access:"Read",       violCrit:1, violHigh:1, violMed:0, lastUsed:"8h ago",  expiryStatus:"Expired 2d ago"     },
];

// ── Argus: Non-critical curated ───────────────────────────────────────────────
export const ARGUS_NON_CRITICAL_CURATED = [
    // gen-ai:AI Agent (lower risk)
    { identityName:"luma-api-key",           agent:"Luma AI",        type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"2h ago",  expiryStatus:"10d left"           },
    { identityName:"chargebee-api-key",      agent:"Chargebee AI",   type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:1, lastUsed:"5h ago",  expiryStatus:"5d left"            },
    { identityName:"copy-ai-token",          agent:"Copy.AI",        type:"Bearer Token", access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"No expiry"          },
    { identityName:"babylon-api-key",        agent:"Babylon Health", type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"3h ago",  expiryStatus:"No expiry"          },
    { identityName:"jooksy-api-key",         agent:"Jooksy",         type:"API Key",      access:"Read",       violCrit:0, violHigh:2, violMed:1, lastUsed:"4h ago",  expiryStatus:"No expiry"          },
    { identityName:"anthropos-api-key",      agent:"Anthropos AI",   type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"6h ago",  expiryStatus:"No expiry"          },
    { identityName:"lttc-api-key",           agent:"LTTC AI",        type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:1, lastUsed:"5h ago",  expiryStatus:"30d left"           },
    { identityName:"agentai-token",          agent:"AgentAI",        type:"Bearer Token", access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"8h ago",  expiryStatus:"No expiry"          },
];

// ── Generation pools ───────────────────────────────────────────────────────────
export const AGENTS_POOL = [
    "Cursor","Claude CLI","VS Code","Claude Desktop","Windsurf",
    "Antigravity","Cursor","VS Code","Claude CLI","Claude Desktop",
    "Windsurf","Cursor","Claude CLI","VS Code","Antigravity",
    "Claude Desktop","Windsurf","Cursor","VS Code","Claude CLI",
    "Antigravity","Cursor","Claude Desktop","VS Code","Windsurf",
    "Claude CLI","Cursor","Antigravity","VS Code","Claude Desktop",
];
export const IDENTITY_POOL = [
    "aws-prod-key","gcp-svc-account","azure-sp-token","github-actions-sa","okta-api-key",
    "twilio-auth-token","anthropic-api-key","salesforce-jwt","mongo-atlas-key","redis-cloud-sa",
    "elastic-api-key","vault-approle","argo-cd-token","terraform-sa","jenkins-cred",
    "splunk-hec-token","pagerduty-key","opsgenie-token","linear-api-key","notion-oauth",
    "zoom-jwt","box-oauth","dropbox-token","figma-pat","cloudflare-api-key",
    "vercel-token","netlify-token","heroku-api-key","fly-io-token","render-token",
    "datadog-agent-key","newrelic-license","grafana-cloud-key","sentry-auth","launchdarkly-sdk",
    "mixpanel-token","amplitude-key","segment-write-key","intercom-token","zendesk-jwt",
];
export const OWNERS_POOL   = [
    "Evelyn Carter","John Matthews","Adam Brooks","Sarah Williams","Noah Bennett",
    "Theodore Collins","Michael Alvarez","Nina Nolan","Lisa Wong","Kevin O'Connor",
    "Grace Mitchell","Daniel Harper","Rachel Foster","James Sullivan","Claire Anderson",
];
export const TYPES_POOL    = ["API Key", "Bearer Token", "API Key", "Bearer Token", "API Key"];
export const ACCESS_POOL   = ["Admin", "Read", "Write", "Read/Write", "Read", "Write"];
export const LAST_USED_P   = ["2m ago","15m ago","1h ago","3h ago","8h ago","1d ago","3d ago","7d ago","Never","30s ago","45m ago","2d ago","6h ago","12h ago"];
export const EXPIRY_POOL   = [
    "30d left","15d left","7d left","5d left","60d left","45d left","90d left",
    "Rotation Due in 7 days","Rotation Due in 14 days","No expiry",
    "Expired 3d ago","Expired 7d ago","2d left","1d left",
];

export const pick = (arr, i) => arr[i % arr.length];

// Generated: all non-critical (violCrit = 0), have high/medium/low violations
export const GENERATED = Array.from({ length: 109 }, (_, i) => {
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

// ── 10 policies (7 Active, 2 Inactive, 1 Draft) ────────────────────────────────
export const INITIAL_POLICIES = [
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

// ── Per-policy YAML templates ─────────────────────────────────────────────────
export const POLICY_YAML_MAP = {
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

export const TEMPLATE_YAML = {
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

export const BLANK_YAML = `id: pol-new\nname: <policy name>\ndescription: >\n  Write your policy description here.\nseverity: medium\nstatus: draft\ntype: access_control\n\nscope:\n  identity_type: all\n  agents: all\n\ncondition:\n  rules:\n    - field: \n      operator: \n      value: \n\ntrigger:\n  on: real_time\n\nactions:\n  on_violation:\n    - notify: security-team@acme.com`;

export const AGENT_OPTIONS = [
    { label: "Cursor",         value: "Cursor" },
    { label: "Claude CLI",     value: "Claude CLI" },
    { label: "Claude Desktop", value: "Claude Desktop" },
    { label: "VS Code",        value: "VS Code" },
    { label: "Windsurf",       value: "Windsurf" },
    { label: "Antigravity",    value: "Antigravity" },
];

export const ARGUS_AGENT_OPTIONS = [
    // LLM
    { label: "OpenAI",         value: "OpenAI" },
    { label: "Cohere",         value: "Cohere" },
    { label: "Perplexity",     value: "Perplexity" },
    { label: "LangChain",      value: "LangChain" },
    // MCP Server
    { label: "K9s Trade",      value: "K9s Trade" },
    { label: "Vulnerable MCP", value: "Vulnerable MCP" },
    { label: "AK Platform",    value: "AK Platform" },
    // AI Agent
    { label: "Replicate",      value: "Replicate" },
    { label: "N8N",            value: "N8N" },
    { label: "Jasper",         value: "Jasper" },
    { label: "Luma AI",        value: "Luma AI" },
    { label: "Chargebee AI",   value: "Chargebee AI" },
    { label: "Copy.AI",        value: "Copy.AI" },
    { label: "Babylon Health", value: "Babylon Health" },
    { label: "Jooksy",         value: "Jooksy" },
    { label: "Anthropos AI",   value: "Anthropos AI" },
    { label: "LTTC AI",        value: "LTTC AI" },
    { label: "AgentAI",        value: "AgentAI" },
];

// ── Argus: 10 policies with Argus agent scopes ─────────────────────────────────
export const ARGUS_INITIAL_POLICIES = [
    {
        policyName:    "No Admin Credentials for Agent Identities",
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Ethan Carter",
        created:       "30d ago",
    },
    {
        policyName:    "Enforce Least Privilege on Credentials",
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "3h ago",
        lastModified:  "Olivia Bennett",
        created:       "45d ago",
    },
    {
        policyName:    "Rotate API Keys Every 30 Days",
        scope:         { primary: "OpenAI", extra: 2 },
        agents:        ["OpenAI", "Perplexity", "LangChain"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Marcus Hale",
        created:       "60d ago",
    },
    {
        policyName:    "Detect Unusual Usage Patterns",
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Active",
        lastTriggered: "1h ago",
        lastModified:  "Marcus Hale",
        created:       "20d ago",
    },
    {
        policyName:    "Restrict Access to Sensitive Resources",
        scope:         { primary: "OpenAI", extra: 2 },
        agents:        ["OpenAI", "Replicate", "N8N"],
        status:        "Active",
        lastTriggered: "4h ago",
        lastModified:  "Marcus Hale",
        created:       "50d ago",
    },
    {
        policyName:    "Disable Dormant Credentials (30+ days)",
        scope:         { primary: "OpenAI", extra: 5 },
        agents:        ["OpenAI","Cohere","Perplexity","LangChain","Replicate","N8N"],
        status:        "Active",
        lastTriggered: "6h ago",
        lastModified:  "Ethan Carter",
        created:       "40d ago",
    },
    {
        policyName:    "Prevent Cross-Service Credential Usage",
        scope:         { primary: "Replicate", extra: 5 },
        agents:        ["Replicate","N8N","Jasper","LangChain","K9s Trade","Vulnerable MCP"],
        status:        "Active",
        lastTriggered: "2h ago",
        lastModified:  "Ethan Carter",
        created:       "35d ago",
    },
    {
        policyName:    "Limit Automation Without Approval",
        scope:         { primary: "K9s Trade", extra: 2 },
        agents:        ["K9s Trade", "Vulnerable MCP", "AK Platform"],
        status:        "Inactive",
        lastTriggered: "Never",
        lastModified:  "Marcus Hale",
        created:       "25d ago",
    },
    {
        policyName:    "Restrict Code Execution Permissions",
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Inactive",
        lastTriggered: "Never",
        lastModified:  "Olivia Bennett",
        created:       "15d ago",
    },
    {
        policyName:    "Enforce Scoped Access for OAuth Tokens",
        scope:         { primary: "All Agents" },
        agents:        ["All Agents"],
        status:        "Draft",
        lastTriggered: "Never",
        lastModified:  "Olivia Bennett",
        created:       "1d ago",
    },
];

// ── Chart data ─────────────────────────────────────────────────────────────────
// Atlas: higher counts (curated + 128 generated)
export const atlasViolationsOverTimeData = [{
    data: [
        [Date.UTC(2026, 3,  1), 178],
        [Date.UTC(2026, 3,  2), 182],
        [Date.UTC(2026, 3,  3), 176],
        [Date.UTC(2026, 3,  4), 171],
        [Date.UTC(2026, 3,  5), 174],
        [Date.UTC(2026, 3,  6), 172],
        [Date.UTC(2026, 3,  7), 169],
    ],
    color: "#EF4444",
    name: "Violations",
}];
// Argus: 54 curated violations only
export const argusViolationsOverTimeData = [{
    data: [
        [Date.UTC(2026, 3,  1), 61],
        [Date.UTC(2026, 3,  2), 58],
        [Date.UTC(2026, 3,  3), 63],
        [Date.UTC(2026, 3,  4), 57],
        [Date.UTC(2026, 3,  5), 60],
        [Date.UTC(2026, 3,  6), 56],
        [Date.UTC(2026, 3,  7), 54],
    ],
    color: "#EF4444",
    name: "Violations",
}];
// Keep backward-compat name for any remaining imports
export const violationsOverTimeData = atlasViolationsOverTimeData;
