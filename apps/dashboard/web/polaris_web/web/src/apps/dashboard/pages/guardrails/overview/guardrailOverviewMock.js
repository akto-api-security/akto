const DECISION_COUNTS = [
    [88, 40, 52], [96, 44, 60], [70, 31, 48], [42, 18, 30], [38, 15, 26], [104, 52, 61], [118, 58, 70],
    [126, 61, 66], [131, 66, 72], [98, 47, 59], [51, 22, 33], [47, 20, 29], [142, 71, 80], [151, 74, 84],
];

const guardrailOverviewMock = {
    periodDays: 7,
    managedDevices: 1240,
    discoveredAssets: 96,
    kpis: {
        openViolations: { count: 248, changePct: 18 },
        criticalUnassigned: { count: 5, oldestAgeHours: 6 },
        blockedOrRedacted: { count: 1932, sharePct: 71 },
        employeesInvolved: { count: 87, devices: 112 },
        medianTriage: { hours: 3.4, targetHours: 4 },
    },
    attention: [
        { severity: "CRITICAL", title: "Unapproved MCP server with production access", meta: "postgres-mcp (community) on 14 devices · MCP server allowlist · Alerted, not blocked", count: 14, secondaryAction: "Switch policy to Block" },
        { severity: "CRITICAL", title: "Cloud and SaaS credentials typed into agents", meta: "3 employees · Claude Code, Windsurf · Blocked, keys still need rotation", count: 3, secondaryAction: "Create ticket" },
        { severity: "HIGH", title: "Customer data going to personal AI accounts", meta: "9 employees in Support and HR · ChatGPT, Comet · Redacted", count: 9, secondaryAction: "Coach employees" },
        { severity: "HIGH", title: "Hidden prompt injection on a public docs site", meta: "docs.example-sdk.dev read by 5 agents · Prompt injection · Blocked", count: 5, secondaryAction: "Block domain" },
        { severity: "MEDIUM", title: "Unsanctioned AI app spreading in Marketing", meta: "DeepSeek Chat · 22 employees continued past the warning", count: 22, secondaryAction: "Block app" },
    ],
    triage: {
        statuses: [
            { key: "OPEN", label: "Open", count: 118 },
            { key: "IN_REVIEW", label: "In review", count: 34 },
            { key: "ASSIGNED", label: "Assigned", count: 41 },
            { key: "TICKETED", label: "Ticketed", count: 27 },
            { key: "IGNORED", label: "Ignored", count: 66 },
            { key: "RESOLVED", label: "Resolved", count: 89 },
        ],
        staleCriticalUnassigned: 5,
        staleAfterHours: 4,
    },
    decisions: DECISION_COUNTS.map(([blocked, redacted, alerted], i) => ({
        day: new Date(2026, 9, 15 + i).getTime(),
        blocked,
        redacted,
        alerted,
    })),
    byPolicy: [
        { name: "Sensitive data (PII)", count: 412, ignoredPct: 18 },
        { name: "Shadow AI apps", count: 366, ignoredPct: 9 },
        { name: "Secrets & credentials", count: 241, ignoredPct: 4 },
        { name: "MCP server allowlist", count: 198, ignoredPct: 12 },
        { name: "Word filters", count: 164, ignoredPct: 62 },
        { name: "Code exfiltration", count: 131, ignoredPct: 11 },
        { name: "Prompt injection", count: 96, ignoredPct: 47 },
        { name: "Dangerous commands", count: 74, ignoredPct: 6 },
    ],
    bySurface: [
        { name: "Browser AI apps", examples: "ChatGPT, Gemini, Claude.ai, DeepSeek, Copilot", count: 734, changePct: 22 },
        { name: "AI agents & CLIs", examples: "Claude Code, Codex CLI, Gemini CLI, Aider", count: 612, changePct: 9 },
        { name: "IDE assistants", examples: "Cursor, Copilot, Windsurf, JetBrains AI", count: 402, changePct: -4 },
        { name: "MCP servers", examples: "38 servers on 214 devices · 9 unapproved", count: 288, changePct: 31 },
        { name: "Skills & plugins", examples: "Claude skills, Cursor rules, IDE extensions", count: 96, changePct: 12 },
        { name: "Local models", examples: "Ollama, LM Studio, llama.cpp", count: 41, changePct: -8 },
    ],
    topEmployees: [
        { name: "meera.kapoor", department: "Support", devices: 1, count: 46, critical: 0 },
        { name: "tom.becker", department: "Marketing", devices: 2, count: 39, critical: 0 },
        { name: "rahul.verma", department: "Payments", devices: 1, count: 27, critical: 3 },
        { name: "priya.nair", department: "Platform Eng", devices: 2, count: 22, critical: 2 },
        { name: "arjun.rao", department: "Payments", devices: 1, count: 19, critical: 1 },
    ],
    topAssets: [
        { name: "ChatGPT", kind: "Browser AI app", tag: "Personal", count: 288 },
        { name: "Claude Code", kind: "AI agent CLI", tag: "Approved", count: 214 },
        { name: "DeepSeek Chat", kind: "Browser AI app", tag: "Unapproved", count: 172 },
        { name: "postgres-mcp (community)", kind: "MCP server · 14 devices", tag: "Unapproved", count: 96 },
        { name: "Cursor", kind: "IDE assistant", tag: "Approved", count: 88 },
    ],
};

export default guardrailOverviewMock;
