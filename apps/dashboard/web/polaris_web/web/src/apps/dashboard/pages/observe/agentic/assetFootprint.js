import { hostSegmentCounts, typeLabelOf } from "./inventoryCategories";
import formatDisplayName from "./mcpClientHelper";
import SkillIcon from "@/assets/Skill.svg";
import { assetIconSrc } from "./AssetIcon";

// AI footprint of one AI inventory asset: Employees -> Devices -> asset -> what it connects to ->
// last-7-days violations by severity. Built only from data the asset panel already loads. Each column
// is capped at FOOTPRINT_LIMIT nodes plus a "+N more" node that lists the rest on hover, so the graph
// is the same size for 10 employees or 10,000. In full screen, selecting a "+N more" node grows that
// column to FOOTPRINT_EXPANDED_LIMIT.

const ASSET_CATEGORY = { "AI Agent": "agent", "MCP Server": "mcp", "LLM": "ai-model", "Skill": "skill", "Plugin": "plugin" };
const ASSET_STATUS = { Unapproved: "bad", Blocked: "bad", "Under review": "review" };
const SEVERITY_STATUS = { critical: "bad", high: "bad", medium: "review", low: undefined };
const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

const FOOTPRINT_LIMIT = 5;
const FOOTPRINT_EXPANDED_LIMIT = 20;
const TOOLTIP_NAMES = 10;

function hiddenTooltip(names, hiddenCount) {
    const listed = names.slice(0, TOOLTIP_NAMES);
    const rest = hiddenCount - listed.length;
    return [...listed, ...(rest > 0 ? [`and ${rest} more`] : [])].join(", ");
}

// First `limit` nodes, then a "+N more" node when `total` (defaults to nodes.length; pass the real
// count when nodes is only a sample) is larger. Its tooltip names the hidden nodes in order.
function capColumn(nodes, { key, category, noun, total = nodes.length, limit = FOOTPRINT_LIMIT, expandable = false }) {
    if (total <= limit) return nodes;
    const shown = nodes.slice(0, limit);
    const hiddenCount = total - shown.length;
    return [...shown, {
        id: `${key}-more`,
        category,
        label: `+${hiddenCount} more ${noun}`,
        sub: expandable ? "Select to show more" : "Hover to see the list",
        tooltip: hiddenTooltip(nodes.slice(limit).map((n) => n.label), hiddenCount),
    }];
}

export const ASSET_FOOTPRINT_LEGEND = [
    { color: "#D72C0D", label: "Unapproved, blocked or critical/high violations" },
    { color: "#E4C300", label: "Under review or medium violations" },
    { color: "#6200EA", label: "Selected" },
];

// [{ head, nodes }] for the column right of the asset; empty when there's nothing to show.
function connectedColumn(asset) {
    const node = (id, category, label, sub, iconSrc) => ({ id, category, label, sub, iconSrc: iconSrc || undefined });
    switch (asset.type) {
        case "AI Agent": {
            const nodes = [
                ...(asset.mcpServers || []).map((name, i) => node(`mcp-${i}`, "mcp", name, "MCP server", assetIconSrc(null, name))),
                ...(asset.pluginNames || []).map((name, i) => node(`plugin-${i}`, "plugin", name, "Plugin", assetIconSrc(null, name))),
                ...(asset.skillCount > 0 ? [node("skills", "skill", plural(asset.skillCount, "skill"), "Skills installed", SkillIcon)] : []),
            ];
            return { head: "Connected", noun: "connections", category: "mcp", nodes };
        }
        case "MCP Server":
            return { head: "AI agents", noun: "agents", category: "agent", nodes: hostSegmentCounts(asset.hostNames).map(({ segment, devices }, i) => node(`agent-${i}`, "agent", formatDisplayName(segment), plural(devices, "device"), assetIconSrc(null, segment))) };
        case "LLM":
            return { head: "Browsers", noun: "browsers", category: "external", nodes: hostSegmentCounts(asset.hostNames).map(({ segment, devices }, i) => node(`browser-${i}`, "external", capitalize(segment), plural(devices, "device"), assetIconSrc(null, segment))) };
        case "Plugin":
            return { head: "AI agent", noun: "agents", category: "agent", nodes: asset.pluginParentAgent ? [node("agent-0", "agent", asset.pluginParentAgent, "AI agent", assetIconSrc(null, asset.pluginParentAgent))] : [] };
        case "Skill":
            return { head: "Plugin", noun: "plugins", category: "plugin", nodes: asset.owningPluginName ? [node("plugin-0", "plugin", asset.owningPluginName, "Bundled by plugin")] : [] };
        default:
            return { nodes: [] };
    }
}

// devices: the asset's device sample ({ deviceId, username, lastSeenEpoch }); deviceTotal: the real
// device count; violations: { critical, high, medium, low } for the last 7 days. fullscreen: whether the
// graph is in full screen; expanded: { employees, devices, connected } columns already grown there.
// Returns expandKeys: "+N more" node id -> column key, for the nodes that can still grow.
export function buildAssetFootprint({ asset, devices = [], deviceTotal = 0, violations = {}, fullscreen = false, expanded = {} }) {
    const heads = [];
    const columns = [];
    const edges = [];
    const expandKeys = {};
    const link = (source, target) => edges.push({ source, target });
    const limitOf = (key) => (expanded[key] ? FOOTPRINT_EXPANDED_LIMIT : FOOTPRINT_LIMIT);
    const cap = (nodes, opts) => {
        const expandable = fullscreen && !expanded[opts.key];
        const capped = capColumn(nodes, { ...opts, limit: limitOf(opts.key), expandable });
        const moreId = `${opts.key}-more`;
        if (expandable && capped[capped.length - 1]?.id === moreId) expandKeys[moreId] = opts.key;
        return capped;
    };

    // The employees with the most devices for this asset.
    const devicesByUser = new Map();
    devices.forEach((d) => {
        if (!d.username || d.username === "-") return;
        if (!devicesByUser.has(d.username)) devicesByUser.set(d.username, []);
        devicesByUser.get(d.username).push(d);
    });
    devicesByUser.forEach((list) => list.sort((a, b) => (b.lastSeenEpoch || 0) - (a.lastSeenEpoch || 0)));
    const users = [...devicesByUser.entries()].sort((a, b) => b[1].length - a[1].length);
    const employeeLimit = limitOf("employees");
    const deviceLimit = limitOf("devices");
    const shownUsers = new Set(users.slice(0, employeeLimit).map(([name]) => name));

    // Devices round-robin over the shown employees (each one's latest device, then each one's second,
    // ...) so every shown employee gets a device and edges don't cross; the rest, for the "+N more"
    // tooltip, list the shown employees' other devices before everyone else's.
    const rounds = [];
    users.filter(([name]) => shownUsers.has(name)).forEach(([, list]) => {
        list.forEach((d, round) => { (rounds[round] = rounds[round] || []).push(d); });
    });
    const shownEmployeesDevices = rounds.flat();
    const otherDevices = devices.filter((d) => !shownUsers.has(d.username));
    const orderedDevices = shownEmployeesDevices.length >= deviceLimit
        ? shownEmployeesDevices
        : [...shownEmployeesDevices, ...otherDevices];
    const orderedRest = shownEmployeesDevices.length >= deviceLimit ? otherDevices : [];

    const employeeNodes = cap(
        users.map(([name, list]) => ({ id: `emp-${name}`, category: "external", label: name, sub: plural(list.length, "device") })),
        { key: "employees", category: "external", noun: "employees", total: Math.max(asset.employeesCount || 0, users.length) },
    );
    const deviceNodes = cap(
        [...orderedDevices, ...orderedRest].map((d) => ({ id: `dev-${d.deviceId}`, category: "device", label: d.deviceId, sub: "Device", username: d.username })),
        { key: "devices", category: "device", noun: "devices", total: Math.max(deviceTotal, devices.length) },
    );
    if (employeeNodes.length) { heads.push("Employees"); columns.push(employeeNodes); }
    if (deviceNodes.length) { heads.push("Devices"); columns.push(deviceNodes); }

    // Employee -> device; an employee with more devices than the shown one also links to "+N more devices".
    const shownDevices = new Set(deviceNodes.map((n) => n.id));
    deviceNodes.forEach((d) => {
        if (shownUsers.has(d.username)) link(`emp-${d.username}`, d.id);
        link(d.id, "asset");
    });
    if (shownDevices.has("devices-more")) {
        users.filter(([name]) => shownUsers.has(name)).forEach(([name, list]) => {
            if (list.some((d) => !shownDevices.has(`dev-${d.deviceId}`))) link(`emp-${name}`, "devices-more");
        });
    }

    heads.push(typeLabelOf(asset) || asset.type);
    columns.push([{ id: "asset", category: ASSET_CATEGORY[asset.type], iconSrc: assetIconSrc(asset.type, asset.assetTagValue) || undefined, label: asset.name, sub: asset.approvalStatus || typeLabelOf(asset), status: ASSET_STATUS[asset.approvalStatus] }]);

    const connected = connectedColumn(asset);
    if (connected.nodes.length) {
        const nodes = cap(connected.nodes, { key: "connected", category: connected.category, noun: connected.noun });
        heads.push(connected.head);
        columns.push(nodes);
        nodes.forEach((n) => link("asset", n.id));
    }

    const violationNodes = Object.keys(SEVERITY_STATUS)
        .filter((severity) => violations[severity] > 0)
        .map((severity) => ({ id: `sev-${severity}`, category: "violation", label: `${violations[severity]} ${severity}`, sub: "Guardrail violations", status: SEVERITY_STATUS[severity] }));
    if (violationNodes.length) {
        heads.push("Violations (7 days)");
        columns.push(violationNodes);
        violationNodes.forEach((n) => link("asset", n.id));
    }

    return { heads, columns, edges, expandKeys };
}
