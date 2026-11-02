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

export const RISK_BUCKET_COUNT = 5;
export const RISK_MAX = 5;
export const RISK_BUCKET_WIDTH = RISK_MAX / RISK_BUCKET_COUNT;
export const MEDIUM_RISK_THRESHOLD = 2.5;
export const HIGH_RISK_THRESHOLD = 4;
export const RISK_COLORS = { low: "#B38CFF", medium: "#E86A00", high: "#D72C0D" };
export const RISK_EXPLANATION = "The highest API risk score (0 to 5, from the stored api_info risk score) across the AI tools on an employee's devices.";

export const EMPLOYEE_CHIPS = [
    { key: "hasUnapproved", label: "Has unapproved tools" },
    { key: "usesPersonal", label: "Uses personal AI accounts" },
    { key: "highRisk", label: "Risk 4+" },
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
    riskMax: RISK_MAX,
};
