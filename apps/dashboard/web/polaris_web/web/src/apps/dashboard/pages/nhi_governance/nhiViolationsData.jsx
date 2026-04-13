import { Badge, Box, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { SettingsMajor } from "@shopify/polaris-icons";
import { CellType } from "../../components/tables/rows/GithubRow";
import func from "@/util/func";

// ── Identity icon ──────────────────────────────────────────────────────────────
const IDENTITY_DOMAIN_MAP = {
    aws: "aws.amazon.com", gcp: "cloud.google.com", azure: "azure.microsoft.com",
    github: "github.com", okta: "okta.com", twilio: "twilio.com",
    hubspot: "hubspot.com", salesforce: "salesforce.com", mongo: "mongodb.com",
    redis: "redis.com", elastic: "elastic.co", vault: "vaultproject.io",
    argo: "argoproj.io", terraform: "hashicorp.com", jenkins: "jenkins.io",
    splunk: "splunk.com", pagerduty: "pagerduty.com", opsgenie: "atlassian.com",
    linear: "linear.app", notion: "notion.so", zoom: "zoom.us", box: "box.com",
    dropbox: "dropbox.com", figma: "figma.com", cloudflare: "cloudflare.com",
    vercel: "vercel.com", netlify: "netlify.com", heroku: "heroku.com",
    fly: "fly.io", render: "render.com", datadog: "datadoghq.com",
    newrelic: "newrelic.com", grafana: "grafana.com", sentry: "sentry.io",
    launchdarkly: "launchdarkly.com", mixpanel: "mixpanel.com",
    amplitude: "amplitude.com", segment: "segment.io", intercom: "intercom.com",
    zendesk: "zendesk.com", stripe: "stripe.com", jira: "atlassian.com",
    slack: "slack.com", vscode: "code.visualstudio.com", entra: "microsoft.com",
    snowflake: "snowflake.com", docker: "docker.com", airbnb: "airbnb.com",
    playwright: "playwright.dev", huggingface: "huggingface.co", anthropic: "anthropic.com",
};
const INTERNAL_KEYWORDS = new Set(["internal", "connector", "filesystem"]);
export function IdentityIcon({ name }) {
    const parts = (name || "").toLowerCase().split(/[-_\d]+/).filter(p => p.length > 2);
    if (parts.some(p => INTERNAL_KEYWORDS.has(p)))
        return <Box style={{width:20,height:20,display:"flex",alignItems:"center",justifyContent:"center"}}><Icon source={SettingsMajor} color="subdued" /></Box>;
    const domain = parts.reduce((found, p) => found || IDENTITY_DOMAIN_MAP[p] || null, null);
    if (!domain) return null;
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
}

// ── Agent icon ─────────────────────────────────────────────────────────────────
const AGENT_SPECIFIC_DOMAIN = {
    "cursor prod":    "cursor.sh",
    "cursor":         "cursor.sh",
    "vs code":        "code.visualstudio.com",
    "claude cli":     "anthropic.com",
    "claude desktop": "anthropic.com",
    "windsurf":       "codeium.com",
};
const AI_ICON_POOL = ["claude.ai","openai.com","deepseek.com","x.ai","gemini.google.com","mistral.ai","perplexity.ai","cohere.com"];
export function AgentIcon({ name }) {
    const key = (name || "").toLowerCase().trim();
    const domain = AGENT_SPECIFIC_DOMAIN[key] || AI_ICON_POOL[key.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0) % AI_ICON_POOL.length];
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
}

// ── Violation severity badges ──────────────────────────────────────────────────
export function ViolationBubbles({ critical = 0, high = 0, medium = 0, low = 0 }) {
    if (!critical && !high && !medium && !low)
        return <Text variant="bodyMd" color="subdued">No violations</Text>;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {critical > 0 && <Box className="badge-wrapper-CRITICAL"><Badge size="small">{String(critical)}</Badge></Box>}
            {high     > 0 && <Box className="badge-wrapper-HIGH"><Badge size="small">{String(high)}</Badge></Box>}
            {medium   > 0 && <Box className="badge-wrapper-MEDIUM"><Badge size="small">{String(medium)}</Badge></Box>}
            {low      > 0 && <Box className="badge-wrapper-LOW"><Badge size="small">{String(low)}</Badge></Box>}
        </HorizontalStack>
    );
}

// ── Severity ───────────────────────────────────────────────────────────────────
export const SEV_ORD = { Critical: 4, High: 3, Medium: 2, Low: 1 };

export const sevBadge = (s) => (
    <Box className={`badge-wrapper-${s.toUpperCase()}`}>
        <Badge status={func.getHexColorForSeverity(s.toUpperCase())}>{s}</Badge>
    </Box>
);

// ── Policy name resolution (handles renames persisted in localStorage) ────────
// Forward: original violation-stored name → current display name
export function resolvePolicyName(name) {
    if (!name) return name;
    try {
        const map = JSON.parse(localStorage.getItem("nhi_policy_name_map") || "{}");
        return map[name] || name;
    } catch (_) { return name; }
}

// Reverse: current display name → original name stored in violation data
export function unresolvedPolicyName(displayName) {
    if (!displayName) return displayName;
    try {
        const map = JSON.parse(localStorage.getItem("nhi_policy_name_map") || "{}");
        const entry = Object.entries(map).find(([, v]) => v === displayName);
        return entry ? entry[0] : displayName;
    } catch (_) { return displayName; }
}

// ── Policy cell ────────────────────────────────────────────────────────────────
export function PolicyCell({ policy }) {
    if (!policy) return null;
    if (typeof policy === "string") return <Text variant="bodyMd">{resolvePolicyName(policy)}</Text>;
    const resolvedPrimary = resolvePolicyName(policy.primary);
    const resolvedExtras  = (policy.extras || []).map(resolvePolicyName);
    const tooltipContent  = resolvedExtras.length > 0
        ? <VerticalStack gap="1">{resolvedExtras.map((p, i) => <Text key={p} variant="bodyMd" color="subdued">{`${i + 2}. ${p}`}</Text>)}</VerticalStack>
        : null;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <Text variant="bodyMd">{resolvedPrimary}</Text>
            {policy.extra > 0 && tooltipContent && (
                <Tooltip content={tooltipContent} dismissOnMouseOut>
                    <Box><Badge>{`+${policy.extra}`}</Badge></Box>
                </Tooltip>
            )}
            {policy.extra > 0 && !tooltipContent && <Badge>{`+${policy.extra}`}</Badge>}
        </HorizontalStack>
    );
}

// ── Raw data ───────────────────────────────────────────────────────────────────
const CURATED = [
    { severity:"Critical", violation:"Admin credential exposed to agent runtime",                    identity:"aws-cursor-key",     agent:"Cursor",     policy:{primary:"No Admin Credentials for Agent Identities"},           discovered:"2h ago",  status:"Open"  },
    { severity:"Critical", violation:"Credential exceeds intended permission scope",                 identity:"aws-cursor-key",     agent:"Cursor",     policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"1h ago",  status:"Open"  },
    { severity:"Critical", violation:"Unusual LLM access spike detected",                           identity:"aws-cursor-key",     agent:"Cursor",     policy:{primary:"Detect Unusual Usage Patterns"},           discovered:"15m ago", status:"Open"  },
    { severity:"Critical", violation:"Token used outside trusted network boundary",                 identity:"hr-slack-token",     agent:"Claude CLI",    policy:{primary:"Restrict Access to Sensitive Resources", extra:2, extras:["Rotate API Keys Every 30 Days","Detect Unusual Usage Patterns"]},  discovered:"6h ago",  status:"Open"  },
    { severity:"High",     violation:"Messaging token triggering bulk automated sends",             identity:"hr-slack-token",     agent:"Claude CLI",    policy:{primary:"Limit Automation Without Approval"},            discovered:"3h ago",  status:"Fixed" },
    { severity:"High",     violation:"Token scope broader than required for task",                  identity:"hr-slack-token",     agent:"Claude CLI",    policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"4h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Bulk API calls detected from single token",                   identity:"hr-slack-token",     agent:"Claude CLI",    policy:{primary:"Detect Unusual Usage Patterns"},                discovered:"5h ago",  status:"Open"  },
    { severity:"High",     violation:"Dormant credential retains write access",                     identity:"aws-env-sa",         agent:"Windsurf",   policy:{primary:"Disable Dormant Credentials (30+ days)", extra:2, extras:["Disable Dormant Credentials (30+ days)","Prevent Cross-Service Credential Usage"]}, discovered:"1h ago", status:"Fixed" },
    { severity:"High",     violation:"Provisioning access without approval controls",               identity:"aws-env-sa",         agent:"Windsurf",   policy:{primary:"Restrict Access to Sensitive Resources", extra:2, extras:["No Admin Credentials for Agent Identities","Enforce Least Privilege on Credentials"]},      discovered:"2h ago", status:"Open"  },
    { severity:"Medium",   violation:"Service account key expired 90+ days ago",                   identity:"aws-env-sa",         agent:"Windsurf",   policy:{primary:"Disable Dormant Credentials (30+ days)"},         discovered:"3h ago",  status:"Open"  },
    { severity:"High",     violation:"Repository token has admin-level permissions",                identity:"github-oauth-456",   agent:"VS Code",          policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"45m ago", status:"Open"  },
    { severity:"High",     violation:"Token accessing repositories beyond expiry date",             identity:"github-oauth-456",   agent:"VS Code",          policy:{primary:"Restrict Access to Sensitive Resources"},                discovered:"45m ago", status:"Fixed" },
    { severity:"Medium",   violation:"OAuth scope exceeds minimum required permissions",            identity:"github-oauth-456",   agent:"VS Code",          policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"1h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Access to restricted ticketing projects detected",            identity:"jira-token",         agent:"Claude Desktop",    policy:{primary:"Restrict Access to Sensitive Resources", extra:2, extras:["Disable Dormant Credentials (30+ days)","Enforce Least Privilege on Credentials"]},   discovered:"1h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Idle credential remains active without usage",                identity:"jira-token",         agent:"Claude Desktop",    policy:{primary:"Disable Dormant Credentials (30+ days)"},               discovered:"30m ago", status:"Open"  },
    { severity:"Critical", violation:"Service account with unrestricted API access",                identity:"internal-api-token", agent:"Claude CLI",       policy:{primary:"Restrict Access to Sensitive Resources"},        discovered:"2d ago",  status:"Open"  },
    { severity:"Medium",   violation:"MCP token missing expiry configuration",                      identity:"internal-api-token", agent:"Claude CLI",       policy:{primary:"Disable Dormant Credentials (30+ days)"},         discovered:"3d ago",  status:"Open"  },
    { severity:"Critical", violation:"Outbound token used without destination binding",             identity:"airbnb-api-key",      agent:"Antigravity",     policy:{primary:"Restrict Access to Sensitive Resources"},           discovered:"5h ago",  status:"Fixed" },
    { severity:"High",     violation:"Write permission granted without MFA binding",                identity:"airbnb-api-key",      agent:"Antigravity",     policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"6h ago",  status:"Open"  },
    { severity:"High",     violation:"Bulk API calls detected from single token",                   identity:"airbnb-api-key",      agent:"Antigravity",     policy:{primary:"Detect Unusual Usage Patterns"},                discovered:"7h ago",  status:"Open"  },
    { severity:"High",     violation:"Token scope broader than required for task",                  identity:"airbnb-api-key",      agent:"Antigravity",     policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"8h ago",  status:"Open"  },
    { severity:"High",     violation:"Identity inactive but retains write access",                  identity:"airbnb-api-key",      agent:"Antigravity",     policy:{primary:"Disable Dormant Credentials (30+ days)"},        discovered:"9h ago",  status:"Open"  },
    { severity:"High",     violation:"Agent sending external messages via shared token",            identity:"vscode-oauth",       agent:"VS Code",       policy:{primary:"Limit Automation Without Approval"},            discovered:"6h ago",  status:"Open"  },
    { severity:"High",     violation:"OAuth scope exceeds minimum required permissions",            identity:"vscode-oauth",       agent:"VS Code",       policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"7h ago",  status:"Open"  },
    { severity:"Medium",   violation:"MCP token missing expiry configuration",                      identity:"vscode-oauth",       agent:"VS Code",       policy:{primary:"Disable Dormant Credentials (30+ days)"},         discovered:"8h ago",  status:"Fixed" },
    { severity:"Critical", violation:"Admin credential exposed to agent runtime",                   identity:"docker-registry-key",         agent:"Cursor",     policy:{primary:"No Admin Credentials for Agent Identities"},           discovered:"5h ago",  status:"Open"  },
    { severity:"Critical", violation:"Excessive permissions granted at agent setup",                identity:"docker-registry-key",         agent:"Cursor",     policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"6h ago",  status:"Open"  },
    { severity:"Critical", violation:"Credential reused across staging and production",             identity:"docker-registry-key",         agent:"Cursor",     policy:{primary:"Prevent Cross-Service Credential Usage"},discovered:"7h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Token rotation overdue by 14 days",                          identity:"docker-registry-key",         agent:"Cursor",     policy:{primary:"Rotate API Keys Every 30 Days"},      discovered:"1d ago",  status:"Fixed" },
    { severity:"Medium",   violation:"Agent accessing prod resources from dev token",               identity:"docker-registry-key",         agent:"Cursor",     policy:{primary:"Prevent Cross-Service Credential Usage"},discovered:"2d ago",  status:"Fixed" },
    { severity:"High",     violation:"Production credential active in test environment",            identity:"github-actions-key", agent:"VS Code",          policy:{primary:"Prevent Cross-Service Credential Usage"},discovered:"12h ago", status:"Fixed" },
    { severity:"Medium",   violation:"Service account key expired 90+ days ago",                   identity:"github-actions-key", agent:"VS Code",          policy:{primary:"Disable Dormant Credentials (30+ days)"},         discovered:"1d ago",  status:"Open"  },
    { severity:"High",     violation:"Write credential exposed to read-only agent",                 identity:"playwright-token",    agent:"Claude Desktop",     policy:{primary:"Enforce Scoped Access for OAuth Tokens"},       discovered:"8h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Service account accessing non-authorized endpoint",           identity:"filesystem-token",    agent:"Windsurf",     policy:{primary:"Restrict Access to Sensitive Resources"},        discovered:"3h ago",  status:"Fixed" },
    { severity:"Medium",   violation:"MCP token missing expiry configuration",                      identity:"filesystem-token",    agent:"Windsurf",     policy:{primary:"Disable Dormant Credentials (30+ days)"},         discovered:"4h ago",  status:"Open"  },
    { severity:"High",     violation:"API token with administrative scope for read ops",            identity:"notion-token",       agent:"Claude CLI",  policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"2h ago",  status:"Fixed" },
    { severity:"Medium",   violation:"OAuth scope exceeds minimum required permissions",            identity:"huggingface-token",   agent:"Claude Desktop",       policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"4h ago",  status:"Fixed" },
    { severity:"High",     violation:"API token with administrative scope for read ops",            identity:"github-copilot-key",      agent:"Cursor",      policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"2h ago",  status:"Fixed" },
    { severity:"High",     violation:"Write credential exposed to read-only agent",                 identity:"github-copilot-key",      agent:"Cursor",      policy:{primary:"Enforce Scoped Access for OAuth Tokens"},       discovered:"3h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Token shared across multiple agent instances",                identity:"github-copilot-key",      agent:"Cursor",      policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"4h ago",  status:"Fixed" },
    { severity:"High",     violation:"Write credential exposed to read-only agent",                 identity:"anthropic-api-key",      agent:"Claude CLI",   policy:{primary:"Enforce Scoped Access for OAuth Tokens"},       discovered:"2h ago",  status:"Open"  },
    { severity:"High",     violation:"OAuth scope exceeds minimum required permissions",            identity:"playwright-token",    agent:"Claude Desktop",     policy:{primary:"Enforce Least Privilege on Credentials"},            discovered:"9h ago",  status:"Fixed" },
];

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
];
const VIOLATION_DESCS = [
    "Admin key used by LLM agent without restriction",
    "Credential not rotated in 60+ days",
    "OAuth token scoped to all repositories",
    "Service account provisioning without approval",
    "Token accessed from unrecognised IP address",
    "Write permission granted without MFA binding",
    "Bulk API calls detected from single token",
    "Identity inactive but retains write access",
    "Key used outside defined business hours",
    "Admin role assigned on non-production environment",
    "Token shared across multiple agent instances",
    "API key with org-wide admin privileges",
    "Credential reused across staging and production",
    "Service account key expired 90+ days ago",
    "MCP token missing expiry configuration",
    "Agent accessing secrets outside defined scope",
    "Long-lived session token not invalidated",
    "Excessive permissions granted at agent setup",
    "Token scope broader than required for task",
    "Service account not deprovisioned after offboarding",
    "API key associated with deleted user account",
    "Dormant token with active write permissions",
    "Service token with LLM inference rights",
    "Unauthorized infra provisioning detected",
    "Key rotation policy bypassed",
    "Token flagged for suspicious geographic usage",
    "Agent accessing prod resources from dev token",
    "OAuth token never refreshed in 45 days",
    "Unencrypted credential found in agent config",
    "Token used by multiple unrelated agents simultaneously",
];
const POLICIES_POOL = [
    "No Admin Credentials for Agent Identities","Enforce Least Privilege on Credentials","Detect Unusual Usage Patterns",
    "Restrict Access to Sensitive Resources","Disable Dormant Credentials (30+ days)","Restrict Access to Sensitive Resources",
    "Rotate API Keys Every 30 Days","Prevent Cross-Service Credential Usage",
    "Enforce Scoped Access for OAuth Tokens","Limit Automation Without Approval","Disable Dormant Credentials (30+ days)",
    "Restrict Access to Sensitive Resources","Restrict Access to Sensitive Resources","Detect Unusual Usage Patterns",
    "Detect Unusual Usage Patterns","Disable Dormant Credentials (30+ days)",
];
const TIME_AGO = [
    "10m ago","30m ago","1h ago","2h ago","4h ago","6h ago","12h ago",
    "1d ago","2d ago","3d ago","7d ago","20m ago","45m ago","8h ago","15m ago",
];
const GEN_SEVS     = ["Critical","Critical","High","High","High","Medium","Medium","Medium","Low","Low"];
const GEN_STATUSES = ["Open","Open","Open","Open","Open","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed"];

const pick = (arr, i) => arr[i % arr.length];

const GENERATED = Array.from({ length: 128 }, (_, i) => {
    const idx   = i + 21;
    const count = i % 7 === 0 ? 1 + (idx % 3) : 0;
    const extras  = count > 0 ? Array.from({ length: count }, (_, j) => pick(POLICIES_POOL, idx + j + 2)) : undefined;
    const extraObj = count > 0 ? { extra: count, extras } : {};
    return {
        severity:  GEN_SEVS[i % 10],
        violation: pick(VIOLATION_DESCS, idx + 4),
        identity:  `${pick(IDENTITY_POOL, idx)}-${idx}`,
        agent:     pick(AGENTS_POOL, idx + 2),
        policy:    { primary: pick(POLICIES_POOL, idx + 1), ...extraObj },
        discovered: pick(TIME_AGO, idx + 5),
        status:    GEN_STATUSES[i % 13],
    };
});

const ALL_RAW = [...CURATED, ...GENERATED].sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity]);

export const violationsTableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:            i + 1,
    severityOrder: SEV_ORD[r.severity],
    severityComp:  sevBadge(r.severity),
    violationComp: <Text variant="bodyMd" fontWeight="medium">{r.violation}</Text>,
    identityComp:  <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identity} /><Text variant="bodyMd">{r.identity}</Text></HorizontalStack>,
    agentComp:     <HorizontalStack gap="2" blockAlign="center" wrap={false}><AgentIcon name={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
    policyComp:    <PolicyCell policy={r.policy} />,
}));

export const violationsHeaders = [
    { text: "Violation",  value: "violationComp", title: "Violation"                          },
    { text: "Identity",   value: "identityComp",  title: "Identity"                           },
    { text: "Agent",      value: "agentComp",     title: "Agent"                              },
    { text: "Severity",   value: "severityComp",  title: "Severity"                           },
    { text: "Policy",     value: "policyComp",    title: "Policy"                             },
    { text: "Discovered", value: "discovered",    title: "Discovered", type: CellType.TEXT    },
];

export const violationsSortOptions = [
    { label: "Severity", value: "severity asc",  directionLabel: "Critical first", sortKey: "severityOrder", columnIndex: 3 },
    { label: "Severity", value: "severity desc", directionLabel: "Low first",      sortKey: "severityOrder", columnIndex: 3 },
];

// ── Per-violation detail content ───────────────────────────────────────────────
const VIOLATION_DETAIL_MAP = {
    "Admin credential exposed to agent runtime": {
        description: "An admin-level credential is directly accessible within the agent's runtime environment. This grants the agent unrestricted access to perform privileged operations across connected services without any additional authorization controls, significantly increasing the risk of accidental or malicious misuse.",
        affectedResources: "S3, EC2, Bedrock, IAM",
        whyTriggered: "Policy 'No Admin Credentials for Agent Identities' requires all agent credentials to operate with scoped, least-privilege permissions. Admin credentials exposed in runtime environments create a direct path for privilege escalation and cannot be audited at the operation level.",
        blastRadius: [
            "Full administrative control over connected cloud resources",
            "Ability to create or modify IAM roles  -  privilege escalation risk",
            "Access to sensitive data across all storage services",
            "Unrestricted AI model usage  -  potential cost abuse",
            "Lateral movement risk across connected services",
        ],
        remediationSteps: [
            "Immediately revoke the admin credential from the agent runtime configuration",
            "Create a scoped IAM role with only the minimum permissions required for the agent's specific task",
            "Rotate the credential and audit all access events from the past 30 days",
            "Update the agent configuration to reference the new scoped role",
            "Enable access logging to monitor future usage patterns",
        ],
        timeline: [
            { time: "Now",     event: "Violation remains active \u2014 admin credential still accessible in runtime" },
            { time: "2h ago",  event: "Violation detected by policy engine during scheduled scan" },
            { time: "2h ago",  event: "Alert triggered for identity owner" },
            { time: "1d ago",  event: "Credential was last rotated" },
            { time: "30d ago", event: "Identity created and assigned to agent" },
        ],
    },
    "Credential exceeds intended permission scope": {
        description: "The credential assigned to this agent has permissions that go beyond what is required for its designated tasks. Overly broad permissions increase the attack surface and risk of unintended data access or service modifications.",
        affectedResources: "IAM Policies, Resource Endpoints",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy requires each credential to have only the minimum permissions necessary. This identity's permission set includes access to resources and operations outside its documented use case.",
        blastRadius: [
            "Access to resources outside the agent's functional scope",
            "Risk of accidental data modification or deletion",
            "Elevated impact if the credential is compromised",
            "Difficulty in auditing actual vs. intended access",
        ],
        remediationSteps: [
            "Review the full permission set attached to this credential",
            "Identify and remove permissions not required for the agent's current tasks",
            "Apply a scoped permission boundary to prevent future over-provisioning",
            "Document the intended permission set for this agent role",
            "Schedule a quarterly permission review for all agent identities",
        ],
        timeline: [
            { time: "1h ago",  event: "Violation detected  -  permissions exceed documented scope" },
            { time: "1h ago",  event: "Policy engine flagged excess permissions" },
            { time: "7d ago",  event: "Permissions last modified" },
            { time: "45d ago", event: "Identity onboarded with initial permission set" },
        ],
    },
    "Unusual LLM access spike detected": {
        description: "A significant spike in LLM API calls was detected from this identity, deviating from its established baseline usage patterns. This may indicate prompt injection, runaway agent loops, or unauthorized use of the credential by an external actor.",
        affectedResources: "Bedrock, LLM Gateway, Model APIs",
        whyTriggered: "The 'Detect Unusual Usage Patterns' policy monitors for access volume anomalies. This identity's call rate exceeded the configured threshold by more than 3x within a 1-hour window.",
        blastRadius: [
            "Significant AI compute cost overrun",
            "Potential data exfiltration via LLM prompts",
            "Risk of sensitive context leaking to external models",
            "Service degradation for other agents sharing the same quota",
        ],
        remediationSteps: [
            "Pause the agent and review recent LLM request payloads for anomalies",
            "Check for prompt injection patterns in recent inputs",
            "Apply rate limiting on the credential's LLM access",
            "Review the agent's execution logs for loop or retry conditions",
            "Reset the credential if external compromise is suspected",
        ],
        timeline: [
            { time: "Now",     event: "Spike ongoing  -  access rate remains elevated" },
            { time: "30m ago", event: "Alert triggered  -  3x baseline exceeded" },
            { time: "1h ago",  event: "Anomaly first detected by usage monitor" },
            { time: "2d ago",  event: "Last baseline recalculation" },
        ],
    },
    "Token used outside trusted network boundary": {
        description: "This token was used from an IP address or network range outside the defined trusted boundaries. Access from untrusted networks significantly increases the risk of credential interception or misuse by an external actor.",
        affectedResources: "Slack API, Network Gateway",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy enforces that tokens are only used from within approved IP ranges or VPN-connected networks. An access event was logged from an address outside these boundaries.",
        blastRadius: [
            "Potential token use by an unauthorized external party",
            "Risk of data exfiltration through the token's API access",
            "Inability to attribute actions to a known internal actor",
            "Compliance violation if regulated data was accessed",
        ],
        remediationSteps: [
            "Immediately revoke the token and issue a replacement",
            "Review access logs for all API calls made from the untrusted source",
            "Investigate whether the access was from a legitimate remote worker or a threat",
            "Enforce IP allowlisting on the token's service account",
            "Enable geo-anomaly detection for future access attempts",
        ],
        timeline: [
            { time: "6h ago", event: "Access event detected from untrusted IP range" },
            { time: "6h ago", event: "Violation flagged by network boundary policy" },
            { time: "6h ago", event: "Token owner notified" },
            { time: "2d ago", event: "Token last rotated" },
        ],
    },
    "Messaging token triggering bulk automated sends": {
        description: "This messaging token has been used to send an unusually high volume of messages in a short time window. Bulk automated sends via agent tokens can be used for spam, data exfiltration, or social engineering at scale.",
        affectedResources: "Slack API, Messaging Channels",
        whyTriggered: "The 'Limit Automation Without Approval' policy detects tokens that exceed normal message-send thresholds. This token's send rate exceeded the allowed limit, suggesting automated bulk messaging behavior.",
        blastRadius: [
            "Mass distribution of potentially harmful or misleading content",
            "Risk of workspace members being targeted through legitimate channels",
            "API rate limit exhaustion affecting other integrations",
            "Reputational damage if messages reach external recipients",
        ],
        remediationSteps: [
            "Suspend the token and halt all outbound messaging immediately",
            "Review the message content and recipients from the bulk send event",
            "Audit the agent's code for unbounded send loops",
            "Apply per-minute and per-hour send rate limits to the token",
            "Notify affected workspace members if necessary",
        ],
        timeline: [
            { time: "3h ago", event: "Bulk send threshold exceeded  -  500+ messages in 10 minutes" },
            { time: "3h ago", event: "Violation detected and token flagged" },
            { time: "4h ago", event: "Agent session initiated" },
            { time: "5d ago", event: "Token issued to agent" },
        ],
    },
    "Token scope broader than required for task": {
        description: "This token has been granted access scopes that exceed what is necessary for the agent's defined function. Unused but active scopes expand the token's potential for misuse without providing any functional benefit.",
        affectedResources: "API Scopes, Resource Endpoints",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy requires each token to include only the scopes directly used by its associated agent. Analysis shows this token holds scopes for operations the agent has never performed.",
        blastRadius: [
            "Exposure of resources and operations outside the agent's role",
            "Higher impact if the token is exfiltrated",
            "Difficulty tracking what the token can and does access",
        ],
        remediationSteps: [
            "Audit the token's active scopes against its actual API usage",
            "Reissue the token with only the minimum required scopes",
            "Update the agent configuration to use the reduced-scope token",
            "Document approved scopes for each agent role",
        ],
        timeline: [
            { time: "4h ago",  event: "Scope mismatch detected during privilege audit" },
            { time: "4h ago",  event: "Violation flagged by least-privilege policy" },
            { time: "14d ago", event: "Token scopes last modified" },
            { time: "30d ago", event: "Token created with initial scope set" },
        ],
    },
    "Bulk API calls detected from single token": {
        description: "An unusually high number of API calls has been made using a single token in a short time window. This pattern may indicate a runaway agent loop, data harvesting, or unauthorized use of the token.",
        affectedResources: "API Gateway, Rate Limiter",
        whyTriggered: "Rate-monitoring policies detect abnormal API call volumes. This token's call rate far exceeded its historical baseline within the monitoring window, suggesting automated or compromised usage.",
        blastRadius: [
            "Risk of data harvesting through rapid sequential API reads",
            "API quota exhaustion impacting other services and agents",
            "Increased attack surface if the spike originates from a compromised token",
        ],
        remediationSteps: [
            "Temporarily suspend the token and investigate the call source",
            "Review the agent's recent execution logs for loop or retry conditions",
            "Implement server-side rate limiting on the token",
            "Rotate the token if external compromise is suspected",
        ],
        timeline: [
            { time: "5h ago", event: "Anomalous call volume detected  -  10x baseline" },
            { time: "5h ago", event: "Rate monitoring policy triggered violation" },
            { time: "6h ago", event: "Agent session started" },
        ],
    },
    "Dormant credential retains write access": {
        description: "This credential has not been used for an extended period but still holds write-level permissions. Dormant credentials with active write access are a common vector for lateral movement and unauthorized changes.",
        affectedResources: "Cloud Storage, Compute Resources",
        whyTriggered: "The 'Disable Dormant Credentials (30+ days)' policy flags credentials inactive for 30+ days that still retain write or higher permissions. This credential has exceeded the inactivity threshold.",
        blastRadius: [
            "Undetected unauthorized writes to storage or configuration",
            "Potential use as a persistent backdoor after initial compromise",
            "Audit trail gaps due to dormant but active credential",
        ],
        remediationSteps: [
            "Revoke write permissions on the dormant credential immediately",
            "If the credential is no longer needed, delete it entirely",
            "Enable inactivity-based automatic deactivation for all credentials",
            "Audit recent access logs to confirm no unauthorized use occurred",
        ],
        timeline: [
            { time: "1h ago",  event: "Dormancy threshold exceeded  -  flagged by policy engine" },
            { time: "31d ago", event: "Last recorded usage of this credential" },
            { time: "90d ago", event: "Credential created with write access" },
        ],
    },
    "Provisioning access without approval controls": {
        description: "This identity has been used to provision infrastructure resources without going through the required approval workflow. Uncontrolled provisioning bypasses change management, cost controls, and security review processes.",
        affectedResources: "Infrastructure, Terraform State, Cloud Accounts",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy requires all infrastructure changes via agent identities to be pre-approved through the change management system. This identity triggered provisioning actions with no corresponding approval record.",
        blastRadius: [
            "Unauthorized infrastructure changes that are difficult to roll back",
            "Potential security misconfiguration of new resources",
            "Unbudgeted cost exposure from untracked resource creation",
            "Compliance violation for environments subject to change control",
        ],
        remediationSteps: [
            "Immediately audit the resources provisioned by this identity",
            "Halt further provisioning until approval controls are established",
            "Implement policy-as-code enforcement for this identity",
            "Require approval gates in the agent's pipeline for infra changes",
            "Tag all agent-provisioned resources with identity metadata for tracking",
        ],
        timeline: [
            { time: "2h ago", event: "Unapproved provisioning action detected" },
            { time: "2h ago", event: "Violation flagged by provisioning policy" },
            { time: "3h ago", event: "Agent initiated infrastructure workflow" },
            { time: "5d ago", event: "Identity granted provisioning permissions" },
        ],
    },
    "Service account key expired 90+ days ago": {
        description: "The service account key associated with this identity has exceeded its maximum allowed age of 90 days. Expired keys that remain active represent a long-lived credential risk and indicate a gap in the key rotation process.",
        affectedResources: "Service Account, Cloud APIs",
        whyTriggered: "The 'Disable Dormant Credentials (30+ days)' policy requires all service account keys to be rotated within 90 days of issuance. This key has not been rotated within the required window.",
        blastRadius: [
            "Long-lived credentials are higher-value targets for attackers",
            "Extended window of unauthorized access if key was exfiltrated",
            "Compliance violations for SOC 2, ISO 27001, or similar frameworks",
        ],
        remediationSteps: [
            "Rotate the service account key immediately",
            "Update all services and agents using the old key with the new one",
            "Enable automatic key expiry and rotation alerts",
            "Audit usage of the expired key for any anomalies",
        ],
        timeline: [
            { time: "Now",      event: "Key remains active and expired" },
            { time: "1d ago",   event: "Second expiry alert sent  -  no action taken" },
            { time: "30d ago",  event: "First expiry alert sent  -  no action taken" },
            { time: "90d ago",  event: "Key rotation deadline reached" },
            { time: "180d ago", event: "Key originally created" },
        ],
    },
    "Repository token has admin-level permissions": {
        description: "The GitHub token assigned to this agent has admin-level permissions on the repository, including the ability to modify settings, manage webhooks, and delete branches. Code review and automation operations require only scoped read access.",
        affectedResources: "GitHub Repositories, Branch Protection Rules",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy requires repository tokens to be scoped to the minimum permissions needed. Admin tokens grant full repository control, which is unnecessary for automated workflows.",
        blastRadius: [
            "Ability to modify or disable branch protection rules",
            "Risk of unintended or malicious force-pushes to main branches",
            "Ability to add or remove collaborators and manage webhooks",
            "Deletion risk for branches, tags, or repository content",
        ],
        remediationSteps: [
            "Replace the admin token with a fine-grained token scoped to the required permissions only",
            "Revoke the existing admin token immediately",
            "Audit the agent's recent repository actions for unauthorized changes",
            "Apply repository-level protection to prevent admin-scope tokens for bots",
        ],
        timeline: [
            { time: "45m ago", event: "Admin-scope token detected by privilege scanner" },
            { time: "45m ago", event: "Violation flagged  -  least-privilege policy triggered" },
            { time: "7d ago",  event: "Token created with admin scope during agent setup" },
        ],
    },
    "Token accessing repositories beyond expiry date": {
        description: "This token is continuing to access repositories despite its configured expiry date having passed. Expired tokens that remain functional indicate a failure in the token lifecycle management process.",
        affectedResources: "GitHub Repositories, Code Assets",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy enforces token expiry dates. This token's expiry date has passed but it remains active and in use, suggesting that expiry was not enforced at the service level.",
        blastRadius: [
            "Unauthorized repository access beyond the intended time window",
            "Breach of time-bound access agreements or contractor offboarding",
            "Audit trail gaps for post-expiry access events",
        ],
        remediationSteps: [
            "Revoke the token immediately",
            "Audit all repository access events that occurred after the expiry date",
            "Enforce server-side token expiry validation",
            "Reissue a new token with a fresh expiry date if access is still required",
        ],
        timeline: [
            { time: "45m ago", event: "Post-expiry access event detected" },
            { time: "45m ago", event: "Violation flagged by token lifecycle policy" },
            { time: "1d ago",  event: "Token expiry date reached  -  expiry not enforced" },
            { time: "30d ago", event: "Token issued" },
        ],
    },
    "OAuth scope exceeds minimum required permissions": {
        description: "This OAuth token has been granted scopes beyond what the agent's current tasks require. Unused but active OAuth scopes expose unnecessary API capabilities and increase the potential blast radius of a token compromise.",
        affectedResources: "OAuth-protected APIs, User Data",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy requires OAuth tokens to include only the scopes actively used by the agent. Periodic scope audits identified permissions that have never been exercised.",
        blastRadius: [
            "Access to API endpoints and user data outside the agent's purpose",
            "Higher-value target for attackers if token is exfiltrated",
            "Potential access to sensitive user profile or account data",
        ],
        remediationSteps: [
            "Review the token's scopes against the agent's actual API call history",
            "Reissue the OAuth token with only the used scopes",
            "Update the agent's OAuth consent flow to request minimal scopes",
            "Schedule regular scope reviews as agent functionality evolves",
        ],
        timeline: [
            { time: "1h ago",  event: "Scope audit flagged unused permissions" },
            { time: "1h ago",  event: "Least-privilege policy violation created" },
            { time: "14d ago", event: "Last scope review performed" },
            { time: "45d ago", event: "Token originally issued" },
        ],
    },
    "Access to restricted ticketing projects detected": {
        description: "This identity accessed Jira projects marked as restricted, which require explicit authorization. Restricted projects may contain sensitive HR, legal, or security information not intended for automated agent access.",
        affectedResources: "Jira Projects, Ticket Data",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy monitors for agent access to projects flagged as sensitive or restricted. This identity accessed one or more restricted projects without an approved exception on record.",
        blastRadius: [
            "Exposure of sensitive ticket data to an automated agent",
            "Risk of confidential information leaking via LLM context windows",
            "Compliance violation if HR, legal, or security projects were accessed",
        ],
        remediationSteps: [
            "Immediately revoke the identity's access to restricted Jira projects",
            "Review the tickets accessed for sensitive information exposure",
            "Reissue the token with an explicit project scope allowlist",
            "Submit an access exception request if restricted access is legitimately needed",
        ],
        timeline: [
            { time: "1h ago", event: "Access to restricted project detected" },
            { time: "1h ago", event: "Policy alert raised for restricted project access" },
            { time: "2h ago", event: "Agent session started" },
        ],
    },
    "Idle credential remains active without usage": {
        description: "This credential has been active but unused for an extended period. Idle credentials represent unnecessary attack surface and may indicate an orphaned or forgotten identity that is no longer actively managed.",
        affectedResources: "API Endpoints, Data Resources",
        whyTriggered: "The 'Disable Dormant Credentials (30+ days)' policy flags credentials with no recorded usage activity over the defined idle threshold. This identity has exceeded the allowed inactivity period.",
        blastRadius: [
            "Unmonitored access path that could be exploited without detection",
            "Orphaned credentials are often excluded from regular security reviews",
            "Risk of use by former employees or contractors whose access was not revoked",
        ],
        remediationSteps: [
            "Confirm with the identity owner whether the credential is still needed",
            "If no longer needed, deactivate and delete the credential",
            "If still needed, re-evaluate and update the agent's task scope",
            "Enable automatic deactivation after a defined period of inactivity",
        ],
        timeline: [
            { time: "30m ago", event: "Idle threshold exceeded  -  violation created" },
            { time: "30d ago", event: "Last recorded usage of this credential" },
            { time: "60d ago", event: "Credential created and assigned" },
        ],
    },
    "Service account with unrestricted API access": {
        description: "This service account has been granted unrestricted access across the full API surface, allowing it to call any endpoint without scope or resource constraints. Unrestricted API access is a high-risk configuration for automated service accounts.",
        affectedResources: "All API Endpoints, Internal Services",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy and broader access governance require service accounts to be scoped to specific APIs. This account's permissions were found to have no API scope restrictions configured.",
        blastRadius: [
            "Access to all internal service endpoints, including admin APIs",
            "Ability to read, write, or delete data across all services",
            "Full infrastructure control if infra APIs are included",
            "Critical single point of failure if the account is compromised",
        ],
        remediationSteps: [
            "Immediately restrict the service account to the specific APIs it uses",
            "Audit all API calls made by this account in the past 90 days",
            "Apply an API allowlist or scope policy to the account",
            "Enable enhanced logging for all service account API activity",
            "Review the provisioning process to prevent unrestricted accounts from being created",
        ],
        timeline: [
            { time: "2d ago",  event: "Unrestricted access scope detected during audit" },
            { time: "2d ago",  event: "Critical violation created  -  no API scope constraints" },
            { time: "14d ago", event: "Service account created with default permissions" },
        ],
    },
    "MCP token missing expiry configuration": {
        description: "This MCP (Model Context Protocol) token has been issued without an expiry date or TTL configuration. Tokens without expiry never automatically invalidate, creating a permanent credential that remains valid indefinitely unless manually revoked.",
        affectedResources: "MCP Server, Agent Context Sessions",
        whyTriggered: "The 'Disable Dormant Credentials (30+ days)' policy requires all tokens, including MCP tokens, to have an explicit expiry date configured. This token was issued without a TTL and will not expire on its own.",
        blastRadius: [
            "Permanent credential risk  -  token never expires automatically",
            "Extended window of unauthorized access if the token is leaked",
            "Difficulty detecting stale or abandoned agent sessions",
        ],
        remediationSteps: [
            "Reissue the MCP token with an explicit TTL (recommended: 30 days)",
            "Revoke the non-expiring token immediately",
            "Update the token issuance process to require expiry configuration",
            "Implement token expiry monitoring to catch future misconfigurations",
        ],
        timeline: [
            { time: "3d ago", event: "Token expiry audit flagged missing TTL" },
            { time: "3d ago", event: "Violation created  -  no expiry configuration found" },
            { time: "7d ago", event: "MCP token issued without expiry" },
        ],
    },
    "Outbound token used without destination binding": {
        description: "This token is making outbound API calls to destinations not in its approved allowlist. Tokens without destination binding can be used to exfiltrate data or interact with unauthorized external services.",
        affectedResources: "External APIs, Outbound Connections",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy requires outbound tokens to have explicit destination binding. This token was detected making calls to endpoints outside its configured allowlist.",
        blastRadius: [
            "Data exfiltration risk via unauthorized external API calls",
            "Risk of the agent being used as a proxy to attack external services",
            "Compliance violation if restricted data was sent to unapproved destinations",
        ],
        remediationSteps: [
            "Revoke the token and investigate the unauthorized outbound calls",
            "Review the content of recent API calls to external destinations",
            "Reissue the token with strict destination binding enforced",
            "Implement egress filtering at the network level for agent services",
        ],
        timeline: [
            { time: "5h ago", event: "Outbound call to unauthorized endpoint detected" },
            { time: "5h ago", event: "Destination policy violation created" },
            { time: "6h ago", event: "Agent session initiated" },
        ],
    },
    "Write permission granted without MFA binding": {
        description: "This identity has write-level permissions but was provisioned without requiring MFA (multi-factor authentication) verification. Write operations performed without MFA provide no guarantee that the action was initiated by an authorized and verified principal.",
        affectedResources: "Data Stores, Configuration Services",
        whyTriggered: "Policy requires all write-level credentials to be bound to an MFA-verified session or an approved automated workflow with equivalent verification controls. This identity's write access has no such binding.",
        blastRadius: [
            "Unauthorized data modification if credential is used by an unverified actor",
            "Configuration changes made without human verification",
            "Elevated risk for sensitive or production environments",
        ],
        remediationSteps: [
            "Add MFA binding to the identity's authentication flow",
            "For automated workflows, implement equivalent approval gate controls",
            "Audit recent write operations performed by this identity",
            "Restrict write access until MFA binding is confirmed",
        ],
        timeline: [
            { time: "6h ago",  event: "Write-without-MFA pattern detected" },
            { time: "6h ago",  event: "Policy violation created for identity" },
            { time: "10d ago", event: "Identity granted write permissions" },
        ],
    },
    "Identity inactive but retains write access": {
        description: "This identity has not performed any authenticated actions for a significant period, yet it retains write-level access permissions. Inactive identities with active write access represent a dormant but dangerous attack surface.",
        affectedResources: "Data Stores, Configuration Services",
        whyTriggered: "The 'Disable Dormant Credentials (30+ days)' policy flags identities that have been inactive beyond the threshold period while retaining write or higher access levels. This identity has exceeded the inactivity window.",
        blastRadius: [
            "Dormant write access that could be activated without detection",
            "Risk of use by former team members or offboarded agents",
            "Often excluded from active monitoring due to perceived inactivity",
        ],
        remediationSteps: [
            "Revoke write access from this identity immediately",
            "Determine whether the identity is still needed",
            "If not needed, fully deactivate the identity",
            "Enable automatic write-access revocation after a defined inactivity period",
        ],
        timeline: [
            { time: "9h ago",  event: "Inactivity + write-access pattern flagged" },
            { time: "35d ago", event: "Last recorded activity by this identity" },
            { time: "90d ago", event: "Write access granted during initial setup" },
        ],
    },
    "Agent sending external messages via shared token": {
        description: "An agent is using a shared token to send messages to external parties. Shared tokens make it impossible to attribute individual message sends to a specific agent or user, increasing the risk of impersonation or data leakage.",
        affectedResources: "Messaging APIs, External Contacts",
        whyTriggered: "The 'Limit Automation Without Approval' policy detects when a single token is used by multiple agents or is sending messages to external recipients. This token was found doing both simultaneously.",
        blastRadius: [
            "Risk of external parties receiving messages impersonating the organization",
            "Inability to attribute or audit specific outbound message events",
            "Potential data exfiltration if message payloads contain sensitive content",
        ],
        remediationSteps: [
            "Immediately revoke the shared token",
            "Issue individual, purpose-specific tokens to each agent that needs messaging access",
            "Review all external messages sent via this token in the past 30 days",
            "Enable message content logging for agent-originated external sends",
        ],
        timeline: [
            { time: "6h ago", event: "External message sent via shared token detected" },
            { time: "6h ago", event: "Messaging policy violation created" },
            { time: "7h ago", event: "Agent session started using shared token" },
        ],
    },
    "Excessive permissions granted at agent setup": {
        description: "During the initial setup of this agent, it was provisioned with a broad permission set that exceeds its documented operational requirements. Over-provisioned setup permissions are a common security debt that rarely gets cleaned up.",
        affectedResources: "IAM Roles, Service APIs",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy audits all agent identities against their documented scope. This agent's permissions were found to exceed its registered operational requirements by a significant margin.",
        blastRadius: [
            "Wide access surface across all connected services",
            "Elevated impact if the agent is compromised or misbehaves",
            "Difficulty enforcing separation of duties across agent roles",
        ],
        remediationSteps: [
            "Audit the agent's actual API usage against its provisioned permissions",
            "Remove all permissions not actively used in the past 30 days",
            "Establish a documented minimum permission set for this agent role",
            "Implement periodic permission drift detection",
        ],
        timeline: [
            { time: "6h ago",  event: "Permission audit detected over-provisioned setup" },
            { time: "6h ago",  event: "Least-privilege violation created" },
            { time: "30d ago", event: "Agent setup completed with broad permissions" },
        ],
    },
    "Credential reused across staging and production": {
        description: "The same credential is being used in both staging and production environments. Cross-environment credential reuse breaks environment isolation and creates a path for staging-environment activity to impact production systems.",
        affectedResources: "Production Services, Staging Environment",
        whyTriggered: "The 'Prevent Cross-Service Credential Usage' policy requires distinct credentials for each deployment environment. This credential was found active in both staging and production simultaneously.",
        blastRadius: [
            "Staging compromise could directly enable production access",
            "Production data may be inadvertently accessed from staging workflows",
            "Invalidating the credential to stop staging access also disrupts production",
            "Audit logs cannot distinguish staging from production actions",
        ],
        remediationSteps: [
            "Issue separate credentials for staging and production immediately",
            "Revoke the shared credential after verifying both environments use new credentials",
            "Enforce environment tagging and separation at the credential issuance level",
            "Audit recent staging activity for any unintended production data access",
        ],
        timeline: [
            { time: "7h ago",  event: "Same credential detected in both environments" },
            { time: "7h ago",  event: "Cross-environment policy violation created" },
            { time: "14d ago", event: "Credential initially issued for staging use" },
            { time: "13d ago", event: "Credential reused in production deployment" },
        ],
    },
    "Token rotation overdue by 14 days": {
        description: "This token's rotation schedule required it to be replaced 14 days ago, but no rotation has occurred. Overdue rotation means the token has been active for longer than the security policy allows, increasing exposure risk.",
        affectedResources: "Service APIs, Data Endpoints",
        whyTriggered: "The 'Rotate API Keys Every 30 Days' policy sets a hard rotation deadline. This token's rotation date was missed and no extension was approved, triggering a policy violation.",
        blastRadius: [
            "Extended credential exposure window beyond policy limits",
            "Long-lived tokens are higher-value targets for attackers",
            "Non-compliance with rotation-based security frameworks",
        ],
        remediationSteps: [
            "Rotate the token immediately",
            "Update all services using the old token",
            "Investigate why the rotation deadline was missed",
            "Set automated rotation reminders or auto-rotation for future cycles",
        ],
        timeline: [
            { time: "1d ago",  event: "Rotation overdue alert triggered" },
            { time: "14d ago", event: "Rotation deadline passed  -  no action taken" },
            { time: "30d ago", event: "Token originally issued" },
        ],
    },
    "Agent accessing prod resources from dev token": {
        description: "A development environment token is being used to access production resources. Dev tokens typically have fewer security controls and logging requirements, making their use in production a significant security and audit risk.",
        affectedResources: "Production Databases, Production APIs",
        whyTriggered: "The 'Prevent Cross-Service Credential Usage' policy detects when tokens issued for development environments are used to access resources tagged as production. This token was issued as a dev token but has active production access.",
        blastRadius: [
            "Production data accessible through a less-secured dev credential",
            "Actions in production attributed to dev environment  -  audit confusion",
            "Dev tokens often have weaker rotation and monitoring requirements",
            "Potential for production data to be returned to the dev environment",
        ],
        remediationSteps: [
            "Immediately revoke the dev token's access to production resources",
            "Issue a proper production-scoped token for legitimate production access",
            "Audit all production actions performed via the dev token",
            "Add environment-based access control checks at the resource level",
        ],
        timeline: [
            { time: "2d ago", event: "Dev token accessing production resource detected" },
            { time: "2d ago", event: "Cross-environment policy violation created" },
            { time: "3d ago", event: "Dev token issued for development environment" },
        ],
    },
    "Production credential active in test environment": {
        description: "A production credential has been deployed in the test environment. Production credentials in test environments create a risk of test activity affecting production systems and expose production secrets in a less-controlled environment.",
        affectedResources: "Production APIs, Production Data",
        whyTriggered: "The 'Prevent Cross-Service Credential Usage' policy requires strict separation between production and test credentials. This credential is tagged as production but was detected in active use in a test environment.",
        blastRadius: [
            "Test workflows could inadvertently modify production data",
            "Production credentials exposed in test environment logs or configs",
            "Less strict access controls in test environment applied to production access",
        ],
        remediationSteps: [
            "Remove the production credential from the test environment immediately",
            "Create a dedicated test credential with access to mock or test data only",
            "Audit test runs that used the production credential for unintended side effects",
            "Implement credential environment tagging and deployment gate checks",
        ],
        timeline: [
            { time: "12h ago", event: "Production credential detected in test environment" },
            { time: "12h ago", event: "Cross-environment policy violation created" },
            { time: "15h ago", event: "Test environment deployment triggered" },
        ],
    },
    "Write credential exposed to read-only agent": {
        description: "A credential with write permissions has been assigned to an agent designated as read-only. This is a clear over-provisioning violation  -  the agent has capabilities far beyond its intended scope.",
        affectedResources: "Data Stores, APIs",
        whyTriggered: "The 'Enforce Scoped Access for OAuth Tokens' policy validates that read-only agents are assigned credentials scoped to read operations only. This agent's assigned credential was found to include write and delete permissions.",
        blastRadius: [
            "Agent can perform unintended writes or deletions",
            "Data integrity risk if the agent behaves unexpectedly",
            "Higher impact if the agent is compromised or manipulated via prompt injection",
        ],
        remediationSteps: [
            "Replace the write credential with a read-only scoped credential immediately",
            "Audit the agent's recent activity for any unintended write operations",
            "Review and update the agent's credential assignment in configuration",
            "Enforce credential scope validation at agent deployment time",
        ],
        timeline: [
            { time: "8h ago", event: "Write scope detected on read-only agent credential" },
            { time: "8h ago", event: "Scope enforcement policy violation created" },
            { time: "3d ago", event: "Credential assigned during agent configuration" },
        ],
    },
    "Service account accessing non-authorized endpoint": {
        description: "This service account made an API call to an endpoint it is not authorized to access. Either the account's permission set is too broad, or the agent is attempting to access resources outside its defined scope.",
        affectedResources: "Unauthorized API Endpoints, Internal Services",
        whyTriggered: "The 'Restrict Access to Sensitive Resources' policy monitors service account access against an approved endpoint list. An access event was logged for an endpoint not on this account's approved allowlist.",
        blastRadius: [
            "Potential access to sensitive internal endpoints",
            "Risk of data exfiltration from non-authorized services",
            "Indicator of misconfigured or misbehaving agent",
        ],
        remediationSteps: [
            "Block the service account's access to the unauthorized endpoint",
            "Investigate why the agent called the unauthorized endpoint",
            "Update the account's permission set to explicitly restrict unauthorized endpoints",
            "Review the agent's logic for unintended endpoint discovery or traversal",
        ],
        timeline: [
            { time: "3h ago", event: "Access to non-authorized endpoint logged" },
            { time: "3h ago", event: "Endpoint access policy violation created" },
            { time: "4h ago", event: "Agent session initiated" },
        ],
    },
    "API token with administrative scope for read ops": {
        description: "An API token with admin-level scope is being used for operations that only require read access. The admin scope is unnecessary for the task and represents significant over-provisioning.",
        affectedResources: "Admin APIs, Configuration Endpoints",
        whyTriggered: "The 'Enforce Least Privilege on Credentials' policy detected that this token's admin scope is not required for any of its recent operations. All recent API calls were read operations satisfiable by a read-only token.",
        blastRadius: [
            "Admin token can perform writes, deletes, and configuration changes",
            "Higher-value target for attackers than a read-only token",
            "Risk of accidental destructive operations",
        ],
        remediationSteps: [
            "Replace the admin token with a read-only token immediately",
            "Revoke the admin token after confirming the read-only token is working",
            "Audit the token's usage history for any admin-scope operations",
            "Update the token issuance process to enforce purpose-based scoping",
        ],
        timeline: [
            { time: "2h ago", event: "Admin scope detected on read-only workflow" },
            { time: "2h ago", event: "Least-privilege violation created" },
            { time: "5d ago", event: "Token issued with admin scope" },
        ],
    },
};

const DEFAULT_DETAIL = {
    description: "A policy violation has been detected for this identity. Review the violation details and take appropriate remediation steps to resolve the issue.",
    affectedResources: "API Endpoints, Data Resources",
    whyTriggered: "This violation was triggered because the identity's configuration or usage pattern conflicts with an applicable governance policy. Review the policy definition for specific requirements.",
    blastRadius: [
        "Potential unauthorized access to protected resources",
        "Risk of credential misuse or exfiltration",
        "Compliance policy violation",
    ],
    remediationSteps: [
        "Review the identity's current permissions and access scope",
        "Rotate or revoke the affected credential if compromise is suspected",
        "Update the identity configuration to comply with the triggering policy",
        "Monitor access logs for suspicious activity",
    ],
    timeline: [
        { time: "Now",      event: "Violation remains active" },
        { time: "Recently", event: "Violation detected by policy engine" },
    ],
};

export const getViolationDetail = (violationStr) => VIOLATION_DETAIL_MAP[violationStr] || DEFAULT_DETAIL;
