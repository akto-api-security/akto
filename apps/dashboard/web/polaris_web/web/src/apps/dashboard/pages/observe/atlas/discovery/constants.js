export const ATLAS_DISCOVERY_CHAT_SCOPE = "atlas_agentic_discovery";

// Tiles on the overview, in the mockup's order.
export const CATEGORY_ORDER = ["agents", "mcp", "browser", "skills"];

export const CATEGORY_LABELS = {
    agents: "AI agents & CLIs",
    mcp: "MCP servers",
    browser: "Browser AI apps",
    skills: "Skills & plugins",
};

export { DEVICE_SCOPES, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT } from "../devices_employees/peopleConstants";

// Short type tag shown next to a tool name in lists.
export const CATEGORY_SHORT = {
    agents: "Agent",
    mcp: "MCP",
    browser: "Browser",
    skills: "Skill",
};

// Mirrors RISK_REACH_LIMIT in AtlasDiscoveryService.
const RISK_REACH_LIMIT = 50;

export const SUBTITLES = {
    decisions: "Newly discovered or unreviewed tools, ranked by risk and spread",
    adoption: "Employees using each tool, last 30 days",
    trend: "Per week, last 12 weeks, by current status",
    reach: `Top ${RISK_REACH_LIMIT} tools by devices. Each circle is a tool, sized by its employees. The shaded corner is high risk on many devices. Select a circle for details.`,
    chain: "Teams reaching MCP servers through AI tools. Top 10 of each, busiest first. Line width shows devices.",
    agentMcp: "Devices where an agent has each MCP server configured; darker means more devices. Shows up to 5 agents and 8 MCP servers, the busiest first.",
    teams: "Share of each team's employees using a category, last 30 days",
};

const BRAND_PURPLE = "#8051FF";

// Every status colour once: color is the status key, bubble the lighter circle tint, stroke the circle outline when it differs.
const STATUS_PALETTE = {
    APPROVED: { color: BRAND_PURPLE, bubble: "#B38CFF" },
    UNDER_REVIEW: { color: "#E4C300", stroke: "#B98900" },
    UNAPPROVED: { color: "#D72C0D" },
    BLOCKED: { color: "#8C9196", fillAlpha: 0.4 },
};
const DEFAULT_FILL_ALPHA = 0.55;
const RISKY_PATH_COLOR = "#E0442A";

const withAlpha = (hex, alpha) => {
    const [red, green, blue] = [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16));
    return `rgba(${red},${green},${blue},${alpha})`;
};

// Risk-and-reach bubble fill, outline and legend dot per status.
export const BUBBLE_STYLE = Object.fromEntries(Object.entries(STATUS_PALETTE).map(([status, palette]) => {
    const tint = palette.bubble || palette.color;
    return [status, { fill: withAlpha(tint, palette.fillAlpha || DEFAULT_FILL_ALPHA), stroke: palette.stroke || palette.color, legend: tint }];
}));

export const RISK_LEVEL_LABELS = ["Low", "Medium", "High", "Critical"];
// Statuses the trend chart stacks; blocked tools are decided, so they are left out.
export const TREND_STATUSES = ["APPROVED", "UNDER_REVIEW", "UNAPPROVED"];

// Adoption bar colours: the filled part and the empty track.
export const ADOPTION_BAR = { fill: BRAND_PURPLE, track: "var(--p-color-bg-hover)" };

// Risk levels that get the critical colour in stat cards.
export const HIGH_RISK_LEVELS = ["CRITICAL", "HIGH"];

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
export const VIOLATION_WINDOW_SECONDS = 7 * 24 * 3600;

export const GUARDRAIL_POLICIES_PATH = "/dashboard/guardrails/policies";
export const DEVICES_PATH = "/dashboard/observe/endpoints";

// Row signals, shown as small tags; the red ones are the ones to act on.
export const SIGNAL_META = {
    malicious: { label: "Malicious", critical: true },
    misconfigured: { label: "Misconfigured", critical: true },
    personal: { label: "Personal account" },
    localMcp: { label: "Local MCP" },
};

export const STATUS_ORDER = ["APPROVED", "UNDER_REVIEW", "UNAPPROVED", "BLOCKED"];

export const STATUS_META = {
    APPROVED: { label: "Approved", color: STATUS_PALETTE.APPROVED.color, badge: "success" },
    UNDER_REVIEW: { label: "Under review", color: STATUS_PALETTE.UNDER_REVIEW.color, badge: "attention" },
    UNAPPROVED: { label: "Unapproved", color: STATUS_PALETTE.UNAPPROVED.color, badge: "critical" },
    BLOCKED: { label: "Blocked", color: STATUS_PALETTE.BLOCKED.color, badge: undefined },
};

// Key for the supply-chain graph: node borders by status, and the red line of a risky path; each entry lights its own paths.
export const RISKY_LEGEND_KEY = "RISKY";
export const CHAIN_LEGEND = [
    ...["APPROVED", "UNDER_REVIEW", "UNAPPROVED"].map((status) => ({ key: status, label: STATUS_META[status].label, color: STATUS_META[status].color })),
    { key: RISKY_LEGEND_KEY, label: "Risky path", color: RISKY_PATH_COLOR },
];

// Load state of a card or flyout.
export const LOAD_STATE = { LOADING: "loading", ERROR: "error", READY: "ready" };

// Asset status values as the backend sends them.
export const ASSET_STATUS = { APPROVED: "APPROVED", UNDER_REVIEW: "UNDER_REVIEW", UNAPPROVED: "UNAPPROVED", BLOCKED: "BLOCKED" };

export const RISKY_CONFIG_META = {
    malicious: { label: "Malicious components", sub: "MCP servers or skills flagged as malicious", question: "Which MCP servers or skills are flagged as malicious?" },
    misconfigured: { label: "Misconfigured tool configs", sub: "Agents, servers or skills flagged with a risky config", question: "Which agents, MCP servers or skills have a risky configuration?" },
    localMcp: { label: "Local MCP servers", sub: "Running on employee devices, outside any gateway", question: "Which MCP servers run locally on employee devices?" },
    personal: { label: "Personal AI accounts", sub: "Tools signed in with a personal account", question: "Who is using personal AI accounts?" },
    highRiskUnreviewed: { label: "High-risk tools not reviewed", sub: "Unapproved or under review, risk 4.0 or higher", question: "Which high-risk tools have not been reviewed yet?" },
};

export const MAX_NODE_LABEL = 22;

// "Needs a decision" row count comes from the measured row height and the card's header and pager.
export const DECISIONS_PAGING = { rowHeight: 65, chromeHeight: 121, minRows: 3, maxRows: 12 };
