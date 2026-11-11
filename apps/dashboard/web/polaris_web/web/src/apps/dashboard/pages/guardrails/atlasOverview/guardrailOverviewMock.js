const guardrailOverviewMock = {
    discoveredAssets: 96,
    attention: [
        { severity: "CRITICAL", title: "Unapproved MCP server with production access", meta: "postgres-mcp (community) on 14 devices · MCP server allowlist · Alerted, not blocked", count: 14, secondaryAction: "Switch policy to Block" },
        { severity: "CRITICAL", title: "Cloud and SaaS credentials typed into agents", meta: "3 employees · Claude Code, Windsurf · Blocked, keys still need rotation", count: 3, secondaryAction: "Create ticket" },
        { severity: "HIGH", title: "Customer data going to personal AI accounts", meta: "9 employees in Support and HR · ChatGPT, Comet · Redacted", count: 9, secondaryAction: "Coach employees" },
        { severity: "HIGH", title: "Hidden prompt injection on a public docs site", meta: "docs.example-sdk.dev read by 5 agents · Prompt injection · Blocked", count: 5, secondaryAction: "Block domain" },
        { severity: "MEDIUM", title: "Unsanctioned AI app spreading in Marketing", meta: "DeepSeek Chat · 22 employees continued past the warning", count: 22, secondaryAction: "Block app" },
    ],
};

export default guardrailOverviewMock;
