import { getRiskLevel } from "@/apps/dashboard/pages/observe/agentic/agenticPageBuilders";
import { ASSET_STATUS, BUBBLE_STYLE, CAPABILITY_TAGS, CATEGORY_LABELS, DECISIONS_PAGING, MAX_NODE_LABEL, RISK_LEVEL_BADGE, SIGNAL_META, STATUS_META } from "./constants";

const BAD = "bad";
const PERCENT_MAX = 100;
// Unreviewed tools at or above this risk get their name drawn next to the bubble.
const LABEL_MIN_RISK = 4;
export const TOPO_STATUS = { UNAPPROVED: BAD, UNDER_REVIEW: "review", APPROVED: "ok", BLOCKED: "neutral" };
const CHAIN_HEADS = ["Teams", "AI tools", "MCP servers"];
// Team -> external, AI tool -> agent, MCP server -> mcp: the node styles AssetTopologyGraph already knows.
const CHAIN_CATEGORIES = ["external", "agent", "mcp"];

// Risk is plotted by level (four equal bands), not by raw score, so every level gets the same height.
const RISK_LEVEL_INDEX = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };
const RISK_LEVEL_COUNT = 4;
const BAND_CENTER = 0.5;
const MAX_JITTER = 0.2;
const MIN_DEVICES_AXIS_MAX = 100;
const BUBBLE_BASE_DIAMETER_PX = 10;
const BUBBLE_GROWTH = 1.6;
const BUBBLE_MAX_RADIUS_PX = 24;

// Log scale from one device up to the next power of ten above the busiest tool.
export function devicesAxisMax(rows) {
    const busiest = Math.max(1, ...rows.map((row) => row.devices));
    return Math.max(MIN_DEVICES_AXIS_MAX, 10 ** Math.ceil(Math.log10(busiest)));
}

function devicesTicks(max) {
    const ticks = [];
    for (let tick = 1; tick <= max; tick *= 10) ticks.push(tick);
    return ticks;
}

// The row of labels under the plot: first and last carry the unit, the ones between are plain numbers.
export function devicesTickLabels(max) {
    const ticks = devicesTicks(max);
    return ticks.map((tick, index) => {
        if (index === 0) return "1 device";
        return index === ticks.length - 1 ? `${tick.toLocaleString("en-US")} devices` : String(tick);
    });
}

// Same name always lands on the same spot inside its band, so bubbles of one level don't stack on a line.
function jitterOf(name) {
    let hash = 0;
    for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) % 1000;
    return (hash / 1000 - BAND_CENTER) * 2 * MAX_JITTER;
}

export const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;
// Rows of the decisions list that fit in `availableHeight` px; 0 until the height is known.
export function decisionsPageSize(availableHeight) {
    if (!availableHeight) return 0;
    const fit = Math.floor((availableHeight - DECISIONS_PAGING.chromeHeight) / DECISIONS_PAGING.rowHeight);
    return Math.min(DECISIONS_PAGING.maxRows, Math.max(DECISIONS_PAGING.minRows, fit));
}

const shorten = (label) => (label && label.length > MAX_NODE_LABEL ? `${label.slice(0, MAX_NODE_LABEL - 1)}…` : label);

// Flags set on a tool row, as [{ id, label, critical }] in display order.
export function signalsOf(row) {
    return Object.keys(SIGNAL_META).filter((id) => row[id]).map((id) => ({ id, ...SIGNAL_META[id] }));
}

// Tags for one observed tool: flags the audit already holds first, then what its name suggests it can do.
export function capabilityTags(tool) {
    const tags = [];
    if (tool.isMalicious) tags.push({ label: "Malicious", critical: true });
    if (tool.hasPrivilegedAccess) tags.push({ label: "Privileged", critical: true });
    CAPABILITY_TAGS.filter((tag) => tag.pattern.test(tool.name || "")).forEach((tag) => tags.push({ label: tag.label, critical: tag.critical }));
    return tags;
}

// "High risk" for a score, with the Polaris badge status of its level.
export function riskBadge(score) {
    const level = getRiskLevel(score);
    return { label: `${level.charAt(0)}${level.slice(1).toLowerCase()} risk`, status: RISK_LEVEL_BADGE[level] };
}

export function formatDate(epochSeconds) {
    return epochSeconds > 0 ? new Date(epochSeconds * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "—";
}

// Circle radius in px grows with the square root of employees, so area tracks headcount.
function bubbleRadius(employees) {
    return Math.min(BUBBLE_MAX_RADIUS_PX, (BUBBLE_BASE_DIAMETER_PX + Math.sqrt(employees) * BUBBLE_GROWTH) / 2);
}

// Position as percentages of the plot: x on a log scale of devices, y by risk level band.
export function bubblePoints(rows, axisMax, onOpen) {
    const logMax = Math.log10(axisMax);
    return rows.map((row) => {
        const radius = bubbleRadius(row.employees || 0);
        const level = RISK_LEVEL_INDEX[getRiskLevel(row.riskScore || 0)];
        return {
            key: row.key,
            x: (Math.log10(Math.max(1, row.devices)) / logMax) * 100,
            y: ((level + BAND_CENTER + jitterOf(row.name || "")) / RISK_LEVEL_COUNT) * 100,
            radius,
            fill: BUBBLE_STYLE[row.status].fill,
            stroke: BUBBLE_STYLE[row.status].stroke,
            tip: `${row.name}: ${plural(row.devices, "device")}, ${plural(row.employees, "employee")}, ${STATUS_META[row.status].label}, risk ${row.riskScore || 0}`,
            label: row.status !== ASSET_STATUS.APPROVED && row.riskScore >= LABEL_MIN_RISK ? row.name : null,
            priority: row.riskScore || 0,
            onClick: () => onOpen(row),
        };
    }).sort((a, b) => b.radius - a.radius);
}

// Team: headcount when known; tool: its type; MCP server: its review status.
function chainSub(columnIndex, node) {
    if (columnIndex === 0) return node.employees ? plural(node.employees, "employee") : plural(node.devices, "device");
    if (columnIndex === 1) return node.clientType || plural(node.devices, "device");
    return node.status ? STATUS_META[node.status].label : plural(node.devices, "device");
}

export function chainModel(chain) {
    const columns = chain.columns.map((nodes, index) => nodes.map((node) => ({
        id: node.id,
        label: shorten(node.label),
        sub: chainSub(index, node),
        category: CHAIN_CATEGORIES[index],
        status: TOPO_STATUS[node.status],
    })));
    return { heads: CHAIN_HEADS, columns, edges: chain.edges };
}

export function agentMcpMatrix(matrix) {
    const columns = matrix.servers.map((server) => ({ label: server.name, danger: server.status === ASSET_STATUS.UNAPPROVED }));
    const rows = matrix.rows.map((row) => ({ label: row.name, cells: row.cells }));
    return { columns, rows, max: Math.max(1, ...rows.flatMap((row) => row.cells)) };
}

export function teamMatrix(matrix) {
    const columns = matrix.categories.map((id) => ({ label: CATEGORY_LABELS[id] || id }));
    return { columns, rows: matrix.rows.map((row) => ({ label: row.name, cells: row.percents })), max: PERCENT_MAX };
}

const CSV_COLUMNS = [
    ["Name", (row) => row.name],
    ["Type", (row) => row.clientType],
    ["Category", (row) => CATEGORY_LABELS[row.category] || row.category],
    ["Status", (row) => (STATUS_META[row.status] ? STATUS_META[row.status].label : row.status)],
    ["Risk score", (row) => row.riskScore],
    ["Devices", (row) => row.devices],
    ["Employees", (row) => row.employees],
    ["First seen", (row) => isoDate(row.firstSeen)],
    ["Last seen", (row) => isoDate(row.lastSeen)],
    ["Personal account", (row) => row.personal],
    ["Local MCP server", (row) => row.localMcp],
    ["Misconfigured", (row) => row.misconfigured],
    ["Malicious", (row) => row.malicious],
];
const FORMULA_PREFIX = /^[=+\-@\t\r]/;

function isoDate(epochSeconds) {
    return epochSeconds > 0 ? new Date(epochSeconds * 1000).toISOString() : "";
}

// A leading = + - @ in a tool name is neutralised so a spreadsheet cannot read it as a formula.
function csvCell(value) {
    const text = value === undefined || value === null ? "" : String(value);
    const safe = FORMULA_PREFIX.test(text) ? `'${text}` : text;
    return `"${safe.replace(/"/g, '""')}"`;
}

export function toCsvLines(rows) {
    return [CSV_COLUMNS.map(([header]) => csvCell(header)).join(",")]
        .concat(rows.map((row) => CSV_COLUMNS.map(([, read]) => csvCell(read(row))).join(",")));
}
