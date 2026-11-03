export const DEVICE_SCOPES = [
    { value: "all", label: "All devices" },
    { value: "desktop", label: "Desktop agents" },
    { value: "browser", label: "Browser extensions" },
];

export const STAT_CARDS = [
    { id: "managedDevices", label: "Managed devices" },
    { id: "agentActive", label: "Atlas agent active" },
    { id: "devicesWithAiTools", label: "Devices with AI tools" },
    { id: "employeesUsingAi", label: "Employees using AI" },
    { id: "devicesWithUnapprovedTools", label: "Devices with unapproved tools" },
    { id: "monitorOnlyDevices", label: "Monitor-only devices" },
];

export const STAT_EMPTY_VALUE = "\u2014";

export const OS_ICON_KEYS = { macos: "mac", windows: "windows", linux: "linux" };

export const HEALTH_ORDER = ["healthy", "outdated", "offline"];
export const MODE_ORDER = ["enforce", "monitor", "none"];
export const CATEGORY_ORDER = ["agents", "ide", "mcp", "browser"];

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
    { key: "usesPersonal", label: "Uses personal AI accounts" },
];

export const SEARCH_HINTS = {
    employees: "Search employees, devices, teams or tools. Narrow with user:, device:, team:, tool:",
    devices: "Search devices, owners, OS or tools. Narrow with owner:, os:, health:, mode:, version:, tool:",
};

export const FLYOUT_GRAPH_LIMIT = 8;
export const FLYOUT_GRAPH_STEP = 16;

export const FOOTPRINT_LEGEND = [
    { color: "#D72C0D", label: "Unapproved or personal account" },
    { color: "#E4C300", label: "Device needs attention" },
    { color: "#6200EA", label: "Selected" },
];

export const PEOPLE_TABLE_HEIGHT = 500;

export const MIN_SEARCH_LENGTH = 3;
export const EXPORT_PAGE_SIZE = 500;
export const EXPORT_ROW_LIMIT = 10000;

export const FILTER_LABELS = {
    teams: "Team",
    osList: "OS",
    healthStates: "Agent",
    guardrailModes: "Guardrail mode",
    categories: "Tool type",
};

export const LIST_FILTERS = {
    employees: ["teams", "osList", "categories"],
    devices: ["osList", "healthStates", "guardrailModes", "categories"],
};

export const FILTER_OPTION_LABELS = {
    osList: { macos: OS_LABELS.macos, windows: OS_LABELS.windows, linux: OS_LABELS.linux },
    healthStates: HEALTH_LABELS,
    guardrailModes: MODE_LABELS,
    categories: CATEGORY_LABELS,
};

export const EMPTY_PEOPLE_FILTERS = {
    teams: [],
    osList: [],
    categories: [],
    healthStates: [],
    hasUnapproved: false,
    usesPersonal: false,
    agentIssues: false,
    guardrailModes: [],
    riskMin: 0,
    riskMax: RISK_MAX,
};
