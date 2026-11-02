export const OS_LABELS = {
    macos: "macOS",
    windows: "Windows",
    linux: "Linux",
    unknown: "Unknown OS",
};

export const CATEGORY_LABELS = {
    agents: "Agents & CLIs",
    ide: "IDE assistants",
    mcp: "MCP servers",
    browser: "Browser AI",
};

export const CATEGORY_BACKGROUNDS = {
    agents: "bg-magic-strong",
    ide: "bg-magic-active",
    mcp: "bg-magic-hover",
    browser: "bg-magic-subdued",
};

export const HEALTH_LABELS = {
    healthy: "Healthy",
    outdated: "Outdated agent",
    offline: "Offline 7+ days",
};

export const HEALTH_BACKGROUNDS = {
    healthy: "bg-success-strong",
    outdated: "bg-caution-strong",
    offline: "bg-critical-strong",
};

export const HEALTH_BADGE_STATUS = {
    healthy: "success",
    outdated: "warning",
    offline: "critical",
};

export const MODE_LABELS = {
    enforce: "Enforce",
    monitor: "Monitor only",
    none: "No policy",
};

export const MODE_BACKGROUNDS = {
    enforce: "bg-magic-strong",
    monitor: "bg-magic-active",
    none: "bg-strong",
};

export const tokenVar = (token) => `var(--p-color-${token})`;

export const RISK_BUCKET_COUNT = 10;
export const HIGH_RISK_THRESHOLD = 70;
export const RISK_COLORS = { low: "#B38CFF", medium: "#E86A00", high: "#D72C0D" };
export const RISK_FORMULA = "Score out of 100: up to 40 for the share of an employee's AI tools that are unapproved, up to 30 for personal AI accounts (3 or more is the maximum), up to 30 for guardrail violations in the last 7 days (50 or more is the maximum).";

export const EMPLOYEE_CHIPS = [
    { key: "hasUnapproved", label: "Has unapproved tools" },
    { key: "usesPersonal", label: "Uses personal AI accounts" },
    { key: "highRisk", label: "Risk 70+" },
];

export const DEVICE_CHIPS = [
    { key: "agentIssues", label: "Agent issues" },
    { key: "monitorMode", label: "Monitor mode" },
    { key: "hasUnapproved", label: "Has unapproved tools" },
];

export const EMPTY_PEOPLE_FILTERS = {
    osList: [],
    categories: [],
    healthStates: [],
    hasUnapproved: false,
    usesPersonal: false,
    agentIssues: false,
    monitorMode: false,
    riskMin: 0,
    riskMax: 100,
};
