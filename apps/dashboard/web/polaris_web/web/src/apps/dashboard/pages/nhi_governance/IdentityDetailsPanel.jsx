import { useState } from "react";
import { ActionList, Badge, Box, Button, Divider, HorizontalStack, Icon, Popover, Text, VerticalStack } from "@shopify/polaris";

import { SettingsMajor } from "@shopify/polaris-icons";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import func from "@/util/func";

// ── Icon helpers (mirrored from IdentitiesPage) ────────────────────────────────
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
    snowflake: "snowflake.com",
};
const INTERNAL_KEYWORDS = new Set(["internal", "connector"]);
function IdentityIcon({ name }) {
    const parts = (name || "").toLowerCase().split(/[-_\d]+/).filter(p => p.length > 2);
    if (parts.some(p => INTERNAL_KEYWORDS.has(p)))
        return <div style={{ width: 20, height: 20, display: "flex", alignItems: "center", justifyContent: "center" }}><Icon source={SettingsMajor} color="subdued" /></div>;
    const domain = parts.reduce((found, p) => found || IDENTITY_DOMAIN_MAP[p] || null, null);
    if (!domain) return null;
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{ borderRadius: 3, flexShrink: 0 }} alt="" />;
}

const AGENT_SPECIFIC_DOMAIN = { "cursor prod": "cursor.sh", "cursor": "cursor.sh", "entra bot": "microsoft.com" };
const AI_ICON_POOL = ["claude.ai", "openai.com", "deepseek.com", "azure.microsoft.com", "gemini.google.com", "mistral.ai", "perplexity.ai", "cohere.com"];
function AgentIcon({ name }) {
    const key = (name || "").toLowerCase().trim();
    const domain = AGENT_SPECIFIC_DOMAIN[key] || AI_ICON_POOL[key.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0) % AI_ICON_POOL.length];
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{ borderRadius: 3, flexShrink: 0 }} alt="" />;
}

// ── Per-identity violation details ─────────────────────────────────────────────
const PANEL_VIOLATIONS = {
    "aws-cursor-key": [
        { violation: "Admin credential exposed to agent runtime",        agent: "Cursor Prod",    severity: "Critical" },
        { violation: "Credential exceeds intended permission scope",     agent: "Cursor Prod",    severity: "Critical" },
        { violation: "Unusual LLM access spike detected",               agent: "Cursor Prod",    severity: "Critical" },
    ],
    "hr-slack-token": [
        { violation: "Token used outside trusted network boundary",      agent: "HR Assistant",   severity: "Critical" },
        { violation: "Messaging token triggering bulk automated sends",  agent: "HR Assistant",   severity: "High"     },
    ],
    "aws-env-sa": [
        { violation: "Dormant credential retains write access",          agent: "New Env Agent",  severity: "High"     },
        { violation: "Provisioning access without approval controls",    agent: "New Env Agent",  severity: "High"     },
    ],
    "github-oauth-456": [
        { violation: "Repository token has admin-level permissions",     agent: "Code Reviewer",  severity: "High"     },
        { violation: "Token accessing repositories beyond expiry date",  agent: "Code Reviewer",  severity: "High"     },
    ],
    "jira-token": [
        { violation: "Access to restricted ticketing projects detected", agent: "My Assistant",   severity: "Medium"   },
        { violation: "Idle credential remains active without usage",     agent: "My Assistant",   severity: "Medium"   },
    ],
    "internal-api-token": [
        { violation: "Service account with unrestricted API access",     agent: "Connector",      severity: "Critical" },
    ],
    "entra-service": [
        { violation: "Outbound token used without destination binding",  agent: "Entra Bot",      severity: "Critical" },
    ],
    "vscode-oauth": [
        { violation: "Agent sending external messages via shared token", agent: "Agent Studio",   severity: "High"     },
    ],
    "stripe-key": [
        { violation: "Token rotation overdue by 14 days",               agent: "Finance Bot",    severity: "Low"      },
    ],
    "github-actions-key": [
        { violation: "Production credential active in test environment", agent: "CI Bot",         severity: "High"     },
    ],
    "zendesk-api-key": [
        { violation: "Write credential exposed to read-only agent",      agent: "Support Bot",    severity: "High"     },
    ],
    "gcp-service-key": [
        { violation: "Service account accessing non-authorized endpoint", agent: "Infra Agent",   severity: "Medium"   },
    ],
    "notion-token": [
        { violation: "API token with administrative scope for read ops", agent: "Docs Assistant", severity: "Medium"   },
    ],
};

function getTopSeverity(row) {
    if (row.violCrit > 0) return { label: "Critical", status: "critical"  };
    if (row.violHigh > 0) return { label: "High",     status: "warning"   };
    if (row.violMed  > 0) return { label: "Medium",   status: "attention" };
    return null;
}

export default function IdentityDetailsPanel({ row, show, setShow }) {
    const [actionActive, setActionActive] = useState(false);

    const severity        = getTopSeverity(row);
    const totalViolations = row.violCrit + row.violHigh + row.violMed;
    const violations      = PANEL_VIOLATIONS[row.identityName] || [];

    const SEV_STYLE = {
        Critical: { background: "#DF2909", color: "white"    },
        High:     { background: "#FED3D1", color: "#202223"  },
        Medium:   { background: "#FFD79D", color: "#202223"  },
        Low:      { background: "#E4E5E7", color: "#202223"  },
    };

    // ── TitleComponent (first component in the scrollable list) ───────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="start">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center" align="start">
                        <IdentityIcon name={row.identityName} />
                        <Text variant="headingMd" fontWeight="semibold">{row.identityName}</Text>
                        {severity && (
                            <span style={{
                                background: SEV_STYLE[severity.label]?.background,
                                color: SEV_STYLE[severity.label]?.color,
                                borderRadius: 20, padding: "2px 10px",
                                fontSize: 12, fontWeight: 600,
                            }}>{severity.label}</span>
                        )}
                    </HorizontalStack>
                    <HorizontalStack gap="2">
                        <Text variant="bodySm" color="subdued">{row.type}</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">{row.access} Access</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">Last Used {row.lastUsed}</Text>
                    </HorizontalStack>
                </VerticalStack>
                <Popover
                    active={actionActive}
                    activator={
                        <Button size="slim" disclosure onClick={() => setActionActive((v) => !v)}>
                            Action
                        </Button>
                    }
                    onClose={() => setActionActive(false)}
                >
                    <ActionList items={[{ content: "Disable identity", destructive: true, onAction: () => setActionActive(false) }]} />
                </Popover>
            </HorizontalStack>
        </Box>
    );

    // ── Overview tab ──────────────────────────────────────────────────────────
    const overviewTab = {
        id: "overview",
        content: "Overview",
        component: (
            <Box padding="4">
                <VerticalStack gap="3">
                    <Text variant="headingSm">Description</Text>
                    <Text variant="bodyMd" color="subdued">
                        {totalViolations > 0
                            ? `This identity is actively used by ${row.agent} with ${row.access.toLowerCase()}-level access via ${row.type}. It currently has ${totalViolations} security violation${totalViolations > 1 ? "s" : ""} that increase the risk of misuse or unauthorized access.`
                            : `This identity is actively used by ${row.agent} with ${row.access.toLowerCase()}-level access via ${row.type}. No active security violations detected.`
                        }
                    </Text>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Violations tab ────────────────────────────────────────────────────────
    const violationsTab = {
        id: "violations",
        content: `Violations ${totalViolations > 0 ? totalViolations : ""}`.trim(),
        component: violations.length > 0 ? (
            <Box padding="4">
                <VerticalStack gap="0">
                    {violations.map((v, i) => (
                        <Box key={i} paddingBlockStart="3" paddingBlockEnd="3">
                            <VerticalStack gap="1">
                                <Text variant="bodyMd">{v.violation}</Text>
                                <HorizontalStack gap="3" blockAlign="center">
                                    <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                                        <AgentIcon name={v.agent} />
                                        <Text variant="bodySm" color="subdued">{v.agent}</Text>
                                    </HorizontalStack>
                                    <div className={`badge-wrapper-${v.severity.toUpperCase()}`}>
                                        <Badge status={func.getHexColorForSeverity(v.severity.toUpperCase())}>{v.severity}</Badge>
                                    </div>
                                </HorizontalStack>
                            </VerticalStack>
                            {i < violations.length - 1 && <Box paddingBlockStart="3"><Divider /></Box>}
                        </Box>
                    ))}
                </VerticalStack>
            </Box>
        ) : (
            <Box padding="4">
                <Text variant="bodyMd" color="subdued">No violations found for this identity.</Text>
            </Box>
        ),
    };

    const tabsComponent = (
        <LayoutWithTabs
            key={row.identityName}
            tabs={[overviewTab, violationsTab]}
            currTab={() => {}}
            noLoading
        />
    );

    return (
        <FlyLayout
            title="Identity details"
            show={show}
            setShow={setShow}
            components={[<TitleComponent key="title" />, tabsComponent]}
            showDivider
            newComp
        />
    );
}
