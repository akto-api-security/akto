export const ATLAS_DISCOVERY_CHAT_SCOPE = "atlas_agentic_discovery";

export const CATEGORY_ORDER = ["agents", "mcp", "browser", "skills", "local"];

export const CATEGORY_LABELS = {
    agents: "AI agents & CLIs",
    mcp: "MCP servers",
    browser: "Browser AI apps",
    skills: "Skills & plugins",
    local: "Local models",
};

export const DEVICE_SCOPES = [
    { value: "all", label: "All devices" },
    { value: "desktop", label: "Desktop agents" },
    { value: "browser", label: "Browser extensions" },
];

export const EXPORT_PAGE_SIZE = 500;
export const EXPORT_ROW_LIMIT = 10000;

// Short type tag shown next to a tool name in lists.
export const CATEGORY_SHORT = {
    agents: "CLI",
    mcp: "MCP",
    browser: "Browser",
    skills: "Skill",
    local: "Local",
};

export const SUBTITLES = {
    decisions: "Newly discovered or unreviewed tools, ranked by risk and spread",
    adoption: "Employees using each tool in the selected range",
    trend: "Per week, last 12 weeks, by current status",
    reach: "Each circle is a tool. Size shows employees using it. Select a circle to see details.",
    chain: "How teams reach MCP servers through AI tools. Line width shows devices. Select a node to trace its paths.",
    agentMcp: "Devices where an agent has each MCP server configured. Red columns are unapproved.",
    teams: "Share of each team's employees using a category in the selected range",
};

// Risk-and-reach bubble fill, outline and legend dot per status.
export const BUBBLE_STYLE = {
    APPROVED: { fill: "rgba(179,140,255,.55)", stroke: "#8051FF", legend: "#B38CFF" },
    UNDER_REVIEW: { fill: "rgba(228,195,0,.55)", stroke: "#B98900", legend: "#E4C300" },
    UNAPPROVED: { fill: "rgba(215,44,13,.55)", stroke: "#D72C0D", legend: "#D72C0D" },
    BLOCKED: { fill: "rgba(140,145,150,.4)", stroke: "#8C9196", legend: "#8C9196" },
};
export const RISK_LEVEL_LABELS = ["Low", "Medium", "High", "Critical"];
// Statuses the weekly chart stacks; blocked tools are decided, so they are left out.
export const TREND_STATUSES = ["APPROVED", "UNDER_REVIEW", "UNAPPROVED"];

// Adoption bar colours: the filled part and the empty track.
export const ADOPTION_BAR = { fill: "#8051FF", track: "#F1F2F3" };

// Risk levels that get the critical colour in stat cards.
export const HIGH_RISK_LEVELS = ["CRITICAL", "HIGH"];

// Key for the supply-chain graph: node borders by status, and the red line of a risky path.
export const CHAIN_LEGEND = [
    { label: "Approved", color: "#8051FF" },
    { label: "Under review", color: "#E4C300" },
    { label: "Unapproved", color: "#D72C0D" },
    { label: "Risky path", color: "#E0442A" },
];

// Flyout capabilities: which row types list tools, under what heading, and how names map to tags.
export const CAPABILITY_TITLES = { service: "Tools exposed", agent: "Capabilities" };
export const CAPABILITY_LIST_LIMIT = 10;
export const CAPABILITY_TAGS = [
    { label: "Shell", pattern: /shell|exec|bash|command|terminal/i, critical: true },
    { label: "Write", pattern: /write|edit|create|delete|remove|upload|update|patch/i, critical: true },
    { label: "Network", pattern: /http|fetch|request|browse|web|url|download|search/i },
];

// Flyout "Install method" for the types where it can be told from the discovery tags.
export const INSTALL_METHODS = {
    llm: "Browser",
    service: (localMcp) => (localMcp ? "Local process on the device" : "Remote server"),
};

export const VIOLATION_WINDOW_LABEL = "last 7 days";

export const GUARDRAIL_ACTIVITY_PATH = "/dashboard/guardrails/activity";
export const GUARDRAIL_POLICIES_PATH = "/dashboard/guardrails/policies";

// Row signals, shown as small tags; the red ones are the ones to act on.
export const SIGNAL_META = {
    malicious: { label: "Malicious", critical: true },
    misconfigured: { label: "Misconfigured", critical: true },
    personal: { label: "Personal account" },
    localMcp: { label: "Local MCP" },
};

export const STATUS_ORDER = ["APPROVED", "UNDER_REVIEW", "UNAPPROVED", "BLOCKED"];

export const STATUS_META = {
    APPROVED: { label: "Approved", color: "#8051FF", badge: "success" },
    UNDER_REVIEW: { label: "Under review", color: "#E4C300", badge: "attention" },
    UNAPPROVED: { label: "Unapproved", color: "#D72C0D", badge: "critical" },
    BLOCKED: { label: "Blocked", color: "#8C9196", badge: undefined },
};

export const RISKY_CONFIG_META = {
    malicious: { label: "Malicious components", sub: "MCP servers or skills flagged as malicious" },
    misconfigured: { label: "Misconfigured tool configs", sub: "Agents, servers or skills flagged with a risky config" },
    localMcp: { label: "Local MCP servers", sub: "Running on employee devices, outside any gateway" },
    personal: { label: "Personal AI accounts", sub: "Tools signed in with a personal account" },
    highRiskUnreviewed: { label: "High-risk tools not reviewed", sub: "Unapproved or under review, risk 4.0 or higher" },
};

export const MAX_NODE_LABEL = 22;

// "Needs a decision" row count comes from the measured row height and the card's header and pager.
export const DECISIONS_PAGING = { rowHeight: 65, chromeHeight: 121, minRows: 3, maxRows: 12 };
