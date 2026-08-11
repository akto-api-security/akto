import { Badge, Box, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { MagicMajor, SettingsMajor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import { Avatar } from "@shopify/polaris";
import { CellType } from "../../components/tables/rows/GithubRow";
import func from "@/util/func";
import IconCacheService from "@/services/IconCacheService";
import { getDomainForFavicon } from "../observe/agentic/mcpClientHelper";
import { formatRelativeTime } from "./nhiUtils";
import { getFirstIdentityName, getAllIdentityNames } from "./identityHelper";

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
    copilot: "github.com", atlassian: "atlassian.net", razorpay: "razorpay.com",
    kite: "kiteconnect.zerodha.com", postgres: "postgresql.org",
    squareup: "squareup.com", alphavantage: "alphavantage.co",
    jetbrains: "jetbrains.com", huggingface: "huggingface.co", gemini: "gemini.google.com",
    grok: "x.ai", openai: "openai.com", cohere: "cohere.ai",
    replicate: "replicate.com", perplexity: "perplexity.ai",
    jasper: "jasper.ai", luma: "luma.ai", chargebee: "chargebee.com",
    babylon: "babylonhealth.com", langchain: "langchain.com",
    claude: "claude.ai", n8n: "n8n.io", jooksy: "jooksy.io",
    anthropos: "anthropos.io", lttc: "lttc.ai", agentai: "agentai.io",
    k9s: "k9s.trade", replicate: "replicate.com",
};
const INTERNAL_KEYWORDS = new Set(["internal", "connector", "filesystem"]);
const iconCacheService = new IconCacheService();

export function IdentityIcon({ name }) {
    const parts = (name || "").toLowerCase().split(/[-_\d]+/).filter(p => p.length > 2);
    if (parts.some(p => INTERNAL_KEYWORDS.has(p)))
        return <Box style={{width:20,height:20,display:"flex",alignItems:"center",justifyContent:"center"}}><Icon source={SettingsMajor} color="subdued" /></Box>;
    const domain = parts.reduce((found, p) => found || IDENTITY_DOMAIN_MAP[p] || null, null);
    if (!domain) return null;
    const faviconUrl = iconCacheService.getFaviconUrl(domain);
    return <img src={faviconUrl} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
}

// ── Agent icon ─────────────────────────────────────────────────────────────────
const AGENT_SPECIFIC_DOMAIN = {
    "cursor prod":    "cursor.com",
    "cursor":         "cursor.com",
    "vs code":        "code.visualstudio.com",
    "claude cli":     "claude.ai",
    "claude desktop": "claude.ai",
    "windsurf":       "codeium.com",
    "antigravity":    "antigravity.google",
    "gemini":         "gemini.google.com",
    "aws":            "aws.amazon.com",
    "azure":          "azure.microsoft.com",
    "stripe":         "stripe.com",
    "playwright":     "playwright.dev",
    "postgres":       "postgresql.org",
    "atlassian":      "atlassian.net",
    "docker":         "docker.com",
    "claude":         "claude.ai",
    "grok":           "x.ai",
    "openai":         "openai.com",
    "anthropic":      "anthropic.com",
    "cohere":         "cohere.ai",
    "replicate":      "replicate.com",
    "perplexity":     "perplexity.ai",
    "jasper":         "jasper.ai",
    "luma ai":        "luma.ai",
    "chargebee ai":   "chargebee.com",
    "copy.ai":        "copy.ai",
    "babylon health": "babylonhealth.com",
    "langchain":      "langchain.com",
    "k9s trade":      "k9s.trade",
    "vulnerable mcp": "akto.io",
    "ak platform":    "akto.io",
    "n8n":            "n8n.io",
    "jooksy":         "jooksy.io",
    "anthropos ai":   "anthropos.io",
    "lttc ai":        "lttc.ai",
    "agentai":        "agentai.io",
};
const AGENT_MCP  = new Set(["aws","azure","stripe","playwright","postgres","atlassian","docker","filesystem","universal","k9s trade","vulnerable mcp","ak platform"]);
const AGENT_LLM  = new Set(["gemini","grok","claude","openai","anthropic","cohere","perplexity","langchain"]);

export function getAgentType(name) {
    const key = (name || "").toLowerCase().trim();
    if (AGENT_MCP.has(key)) return "MCP Server";
    if (AGENT_LLM.has(key)) return "LLM";
    return "AI Agent";
}

export function AgentIcon({ name }) {
    const key = (name || "").toLowerCase().trim();

    // Try to get domain from KNOWN_CLIENTS via getDomainForFavicon
    const domain = getDomainForFavicon(key);

    // If domain found, use favicon (matches Agentic Assets pattern)
    if (domain) {
        const faviconUrl = iconCacheService.getFaviconUrl(domain);
        return <img src={faviconUrl} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
    }

    // Fallback: Use category-based icons (matches CollectionIcon pattern from Agentic Assets)
    // MCP Servers
    if (AGENT_MCP.has(key))
        return <Avatar source={MCPIcon} shape="square" size="extraSmall" />;

    // LLMs
    if (AGENT_LLM.has(key))
        return <Box style={{width:20,height:20,display:"flex",alignItems:"center",justifyContent:"center"}}><Icon source={MagicMajor} color="base" /></Box>;

    // Default fallback for unknown agents (no icon)
    return null;
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

export const violationsHeaders = [
    { text: "Violation",  value: "violationComp", title: "Violation"                          },
    { text: "Identity",   value: "identityComp",  title: "Identity"                           },
    { text: "Agentic Asset", value: "agentComp",  title: "Agentic Asset"                      },
    { text: "Severity",   value: "severityComp",  title: "Severity"                           },
    { text: "Policy",     value: "policyComp",    title: "Policy"                             },
    { text: "Discovered", value: "discovered",    title: "Discovered", type: CellType.TEXT    },
];

export const violationsSortOptions = [
    { label: "Severity", value: "severity asc",  directionLabel: "Critical first", sortKey: "severityOrder", columnIndex: 3 },
    { label: "Severity", value: "severity desc", directionLabel: "Low first",      sortKey: "severityOrder", columnIndex: 3 },
];

export const transformApiViolations = (apiViolations) => {
    return apiViolations
        .sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity])
        .map((v) => {
            const violationHexId = v.hexId || v.id;
            const policyObj = v.policy && Array.isArray(v.policy)
                ? {
                    primary: v.policy[0] || "N/A",
                    extra: Math.max(0, v.policy.length - 1),
                    extras: v.policy.slice(1) || [],
                  }
                : v.policy;
            const firstIdentityName = getFirstIdentityName(v.identities);
            const allIdentityNames = getAllIdentityNames(v.identities);
            const extraCount = allIdentityNames.length - 1;
            return {
                ...v,
                id: violationHexId,
                violation: v.violationType,
                identity: firstIdentityName,
                identities: v.identities,
                discovered: formatRelativeTime(v.discoveredAt, "Unknown"),
                severityOrder: SEV_ORD[v.severity] || 0,
                policy: policyObj,
                violationComp: <Text variant="bodyMd" fontWeight="medium">{v.violationType}</Text>,
                identityComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <IdentityIcon name={firstIdentityName} />
                        <Text variant="bodyMd">{firstIdentityName}</Text>
                        {extraCount > 0 && (
                            <Badge>{`+${extraCount}`}</Badge>
                        )}
                    </HorizontalStack>
                ),
                agentComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <AgentIcon name={v.agentName} />
                        <Text variant="bodyMd">{v.agentName}</Text>
                    </HorizontalStack>
                ),
                severityComp: sevBadge(v.severity),
                policyComp: <PolicyCell policy={policyObj} />,
            };
        });
};
