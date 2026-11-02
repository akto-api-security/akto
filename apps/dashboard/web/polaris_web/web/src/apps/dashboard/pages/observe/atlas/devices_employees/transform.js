import { Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import SpinnerCentered from "../../../../components/progress/SpinnerCentered";
import Pressable from "../../../dashboard/ask/components/Pressable";
import {
    CATEGORY_LABELS, FLYOUT_GRAPH_LIMIT, HEALTH_LABELS, MODE_LABELS, OS_LABELS, STAT_CARDS, STAT_EMPTY_VALUE,
} from "./peopleConstants";

const ALL_CATEGORIES = ["agents", "ide", "mcp", "browser"];

const STAT_CARD_TARGETS = {
    managedDevices: { tab: "devices", filters: {} },
    agentActive: { tab: "devices", filters: { agentIssues: true } },
    devicesWithAiTools: { tab: "devices", filters: { categories: ALL_CATEGORIES } },
    employeesUsingAi: { tab: "employees", filters: {} },
    devicesWithUnapprovedTools: { tab: "devices", filters: { hasUnapproved: true } },
    monitorOnlyDevices: { tab: "devices", filters: { guardrailModes: ["monitor"] } },
};

const FOOTPRINT_LAYOUT = { nodeWidth: 176, columnGap: 40, rowHeight: 84, headHeight: 40, viewWidth: 696, minHeight: 200, dimmedOpacity: 0.3, dimmedEdgeOpacity: 0.07 };
const FOOTPRINT_EDGE_COLOR = "#B38CFF";
const FOOTPRINT_RISKY_EDGE_COLOR = "#E0442A";

const CSV_COLUMNS = {
    employees: [
        ["Employee", (row) => row.username],
        ["Teams", (row) => row.teams],
        ["Devices", (row) => row.devices],
        ["AI tools", (row) => row.toolCount],
        ["Unapproved tools", (row) => row.unapprovedTools],
        ["Personal accounts", (row) => row.personalAccounts],
        ["Violations (7d)", (row) => row.violationCount],
        ["Risk score", (row) => row.riskScore],
        ["Last active", (row) => (row.lastActive ? func.epochToDateTime(row.lastActive) : "")],
    ],
    devices: [
        ["Device", (row) => row.name],
        ["OS", (row) => OS_LABELS[row.os] || row.os],
        ["Owner", (row) => row.owner],
        ["Atlas agent", (row) => HEALTH_LABELS[row.health] || row.health],
        ["Agent version", (row) => row.agentVersion],
        ["Guardrail mode", (row) => MODE_LABELS[row.guardrailMode] || row.guardrailMode],
        ["AI tools", (row) => row.toolCount],
        ["Unapproved tools", (row) => row.unapprovedTools],
        ["Violations (7d)", (row) => row.violationCount],
        ["Risk score", (row) => row.riskScore],
        ["Last check-in", (row) => (row.lastHeartbeat ? func.epochToDateTime(row.lastHeartbeat) : "")],
    ],
};

const formatCount = (count) => (count == null ? STAT_EMPTY_VALUE : count.toLocaleString("en-US"));
const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;

function statSubtexts(deviceStats) {
    const {
        offlineSevenDays, devicesWithAiToolsPercent, totalEmployees, unapprovedTeams, monitorOnlyDevices,
    } = deviceStats;
    return {
        agentActive: offlineSevenDays > 0 ? { text: `${formatCount(offlineSevenDays)} offline for 7+ days`, color: "critical" } : null,
        devicesWithAiTools: devicesWithAiToolsPercent != null ? { text: `${devicesWithAiToolsPercent}% of fleet` } : null,
        employeesUsingAi: totalEmployees != null ? { text: `of ${formatCount(totalEmployees)}` } : null,
        devicesWithUnapprovedTools: unapprovedTeams > 0 ? { text: `Across ${plural(unapprovedTeams, "team")}`, color: "critical" } : null,
        monitorOnlyDevices: monitorOnlyDevices != null ? { text: "Not blocking violations", color: "critical" } : null,
    };
}

function statValue(value, subtext, onClick, label) {
    return (
        <Pressable onClick={onClick} accessibilityLabel={label} hoverBackground="bg-subdued" borderRadius="2">
            <VerticalStack gap="1">
                <Text as="p" variant="heading2xl">{formatCount(value)}</Text>
                {subtext && <Text as="p" variant="bodySm" color={subtext.color || "subdued"}>{subtext.text}</Text>}
            </VerticalStack>
        </Pressable>
    );
}

function capped(items) {
    if (items.length <= FLYOUT_GRAPH_LIMIT) return { shown: items, hidden: 0, rest: [] };
    const shown = items.slice(0, FLYOUT_GRAPH_LIMIT - 1);
    return { shown, hidden: items.length - shown.length, rest: items.slice(shown.length) };
}

function balanced(groups, limit) {
    const total = groups.reduce((sum, group) => sum + group.length, 0);
    if (total <= limit) return { shown: groups.flat(), hidden: 0, rest: [] };
    const taken = groups.map(() => 0);
    let room = limit - 1;
    while (room > 0) {
        let progressed = false;
        groups.forEach((group, index) => {
            if (room > 0 && taken[index] < group.length) {
                taken[index] += 1;
                room -= 1;
                progressed = true;
            }
        });
        if (!progressed) break;
    }
    const shown = groups.flatMap((group, index) => group.slice(0, taken[index]));
    const rest = groups.flatMap((group, index) => group.slice(taken[index]));
    return { shown, hidden: rest.length, rest };
}

const MORE_TITLES = { "device-more": "More devices", "tool-more": "More AI tools", "mcp-more": "More MCP servers, plugins and skills" };

function toolStatus(tool) {
    return !tool.approved || tool.personal ? "bad" : "ok";
}

function toolSub(tool) {
    if (tool.plugin) return "Plugin";
    const category = CATEGORY_LABELS[tool.category] || tool.category;
    if (tool.category === "mcp") return tool.approved ? "Approved" : "Unapproved";
    return tool.personal ? `${category} · Personal account` : category;
}

function clientMatches(client, agentName) {
    const left = (client || "").toLowerCase();
    const right = (agentName || "").toLowerCase();
    return !!left && !!right && (left.startsWith(right) || right.startsWith(left));
}

function footprintModel(root, devices, heads) {
    const withDevices = !!root.devicesColumn;
    const edges = [];
    const deviceNodes = [];
    const agentNodes = [];
    const mcpNodes = [];
    const pluginNodes = [];
    const skillNodes = [];
    const link = (source, target, status) => edges.push({ source, target, risky: status === "bad" });

    const { shown: shownDevices, hidden: hiddenDevices, rest: restDevices } = capped(devices);
    shownDevices.forEach((device, deviceIndex) => {
        const deviceId = withDevices ? `device-${deviceIndex}` : "root";
        if (withDevices) {
            deviceNodes.push({ id: deviceId, category: "external", label: device.name, sub: `${OS_LABELS[device.os] || device.os} · ${HEALTH_LABELS[device.health] || device.health}`, status: device.health === "healthy" ? "neutral" : "review" });
            link("root", deviceId, "ok");
        }
        const tools = device.tools || [];
        const agents = tools.filter((tool) => tool.category !== "mcp" && !tool.plugin);
        const parentOf = (tool) => {
            const parentTool = agents.find((agent) => clientMatches(tool.client, agent.name));
            return parentTool ? `tool-${parentTool.collectionId}` : deviceId;
        };
        agents.forEach((tool) => {
            agentNodes.push({ id: `tool-${tool.collectionId}`, category: "agent", label: tool.name, sub: toolSub(tool), status: toolStatus(tool), parent: deviceId });
        });
        tools.filter((tool) => tool.category === "mcp" && !tool.plugin).forEach((tool) => {
            mcpNodes.push({ id: `tool-${tool.collectionId}`, category: "mcp", label: tool.name, sub: toolSub(tool), status: toolStatus(tool), parent: parentOf(tool) });
        });
        tools.filter((tool) => tool.plugin).forEach((tool) => {
            pluginNodes.push({ id: `plugin-${tool.collectionId}`, category: "plugin", label: tool.plugin, sub: toolSub(tool), status: "neutral", parent: parentOf(tool) });
        });
        agents.filter((tool) => tool.skillCount > 0).forEach((tool) => {
            skillNodes.push({ id: `skills-${tool.collectionId}`, category: "skill", label: plural(tool.skillCount, "skill"), sub: "Skills · select to list", status: "neutral", parent: `tool-${tool.collectionId}` });
        });
    });
    if (hiddenDevices > 0) deviceNodes.push({ id: "device-more", category: "external", label: `+${hiddenDevices} more devices`, sub: "", status: "neutral" });

    const hiddenItems = {};
    if (hiddenDevices > 0) {
        hiddenItems["device-more"] = restDevices.map((device) => ({ label: device.name, sub: `${OS_LABELS[device.os] || device.os} · ${HEALTH_LABELS[device.health] || device.health}` }));
    }
    const visibleAgents = capped(agentNodes);
    const visibleMcps = balanced([mcpNodes, pluginNodes, skillNodes], FLYOUT_GRAPH_LIMIT);
    if (visibleAgents.hidden > 0) hiddenItems["tool-more"] = visibleAgents.rest.map((node) => ({ label: node.label, sub: node.sub }));
    if (visibleMcps.hidden > 0) hiddenItems["mcp-more"] = visibleMcps.rest.map((node) => ({ label: node.label, sub: node.sub }));
    if (visibleAgents.hidden > 0) visibleAgents.shown.push({ id: "tool-more", category: "agent", label: `+${visibleAgents.hidden} more AI tools`, sub: "", status: "neutral" });
    if (visibleMcps.hidden > 0) visibleMcps.shown.push({ id: "mcp-more", category: "mcp", label: `+${visibleMcps.hidden} more MCP servers`, sub: "", status: "neutral" });

    const statusById = new Map([["root", "root"]]);
    const columns = [[{ id: "root", category: "external", label: root.label, sub: root.sub, status: "root" }]];
    if (withDevices) columns.push(deviceNodes);
    columns.push(visibleAgents.shown, visibleMcps.shown);
    columns.flat().forEach((node) => statusById.set(node.id, node.status));
    visibleAgents.shown.concat(visibleMcps.shown).forEach((node) => {
        if (!node.parent || node.id.endsWith("-more")) return;
        const parent = statusById.has(node.parent) ? node.parent : statusById.has("tool-more") && node.category !== "agent" ? "tool-more" : null;
        if (parent) link(parent, node.id, node.status);
    });
    return { heads, columns, edges, hiddenItems };
}

function connectedIds(edges, start) {
    const reached = new Set([start]);
    [["source", "target"], ["target", "source"]].forEach(([from, to]) => {
        const seen = new Set([start]);
        const stack = [start];
        while (stack.length) {
            const current = stack.pop();
            edges.forEach((edge) => {
                if (edge[from] === current && !seen.has(edge[to])) {
                    seen.add(edge[to]);
                    reached.add(edge[to]);
                    stack.push(edge[to]);
                }
            });
        }
    });
    return reached;
}

function footprintLayout(model, highlight) {
    const layout = FOOTPRINT_LAYOUT;
    const lit = highlight ? connectedIds(model.edges, highlight) : null;
    const tallest = Math.max(1, ...model.columns.map((column) => column.length));
    const statusOf = new Map(model.columns.flat().map((node) => [node.id, node.status]));
    const nodes = [];
    model.columns.forEach((column, columnIndex) => {
        const x = columnIndex * (layout.nodeWidth + layout.columnGap);
        nodes.push({ id: `head-${columnIndex}`, type: "topoHead", position: { x, y: 0 }, draggable: false, selectable: false, data: { label: model.heads[columnIndex] } });
        const offset = (tallest - column.length) / 2;
        column.forEach((node, rowIndex) => {
            nodes.push({
                id: node.id,
                type: "topoNode",
                draggable: false,
                position: { x, y: layout.headHeight + (rowIndex + offset) * layout.rowHeight },
                style: lit && !lit.has(node.id) ? { opacity: layout.dimmedOpacity } : undefined,
                data: { component: { category: node.category, type: node.sub || "Select to list", label: node.label, status: node.status, selected: highlight === node.id } },
            });
        });
    });
    const edges = model.edges.map((edge) => {
        const risky = statusOf.get(edge.source) === "bad" || statusOf.get(edge.target) === "bad";
        const edgeLit = !lit || (lit.has(edge.source) && lit.has(edge.target));
        return {
            id: `e-${edge.source}-${edge.target}`,
            source: edge.source,
            target: edge.target,
            type: "default",
            style: { stroke: risky ? FOOTPRINT_RISKY_EDGE_COLOR : FOOTPRINT_EDGE_COLOR, strokeWidth: risky ? 2 : 1.5, opacity: edgeLit ? 1 : layout.dimmedEdgeOpacity },
        };
    });
    const contentWidth = model.columns.length * layout.nodeWidth + (model.columns.length - 1) * layout.columnGap;
    const scale = Math.min(1, layout.viewWidth / contentWidth);
    const height = Math.max(layout.minHeight, Math.ceil((layout.headHeight + tallest * layout.rowHeight) * scale) + 16);
    return { nodes, edges, height };
}

const transform = {
    footprintLayout,
    statCardTarget: (id) => STAT_CARD_TARGETS[id],
    buildStatItems: (deviceStats, onSelect) => {
        const subtexts = deviceStats ? statSubtexts(deviceStats) : {};
        return STAT_CARDS.map((card) => ({
            title: card.label,
            isComp: true,
            data: statValue(deviceStats?.[card.id], subtexts[card.id], () => onSelect(card.id), card.label),
        }));
    },
    cardBody: (status, body) => {
        if (status === "loading") return <SpinnerCentered />;
        if (status === "error") return <Text as="p" color="subdued">This card could not be loaded. The other cards are unaffected.</Text>;
        return body;
    },
    footprintGraph: (kind, detail) => (kind === "device"
        ? footprintModel({ label: detail.name, sub: OS_LABELS[detail.os] || detail.os }, [detail], ["Device", "AI tools", "MCP servers, plugins and skills"])
        : footprintModel({ label: detail.username, sub: (detail.teams || []).join(", ") || "Employee", devicesColumn: true }, detail.deviceDetails || [], ["Employee", "Devices", "AI tools", "MCP servers, plugins and skills"])),
    employeeAiToolCount: (employee) => (employee.deviceDetails || []).reduce(
        (total, device) => total + device.toolCount - (device.toolsByCategory?.mcp || 0), 0),
    pluginCount: (tools) => tools.filter((tool) => tool.plugin).length,
    allTools: (kind, detail) => (kind === "device"
        ? detail.tools || []
        : (detail.deviceDetails || []).flatMap((device) => device.tools || [])),
    nodeList: (model, kind, detail, nodeId) => {
        const hidden = model.hiddenItems?.[nodeId];
        if (hidden) return { title: `${MORE_TITLES[nodeId]} (${hidden.length})`, items: hidden };
        const match = /^(skills|plugin)-(\d+)$/.exec(nodeId || "");
        if (!match) return null;
        const tool = transform.allTools(kind, detail).find((row) => String(row.collectionId) === match[2]);
        if (!tool || !tool.skillCount) return null;
        return {
            title: `Skills in ${match[1] === "plugin" ? `plugin ${tool.plugin}` : tool.name} (${tool.skillCount})`,
            items: (tool.skills || []).map((skill) => ({ label: skill })),
            empty: "The server did not return skill names for this item.",
        };
    },
    peopleParams: (deviceScope, filters, searchString) => ({
        deviceScope,
        atlasQuery: searchString,
        atlasOs: filters.osList,
        toolCategories: filters.categories,
        healthStates: filters.healthStates,
        hasUnapproved: filters.hasUnapproved,
        usesPersonal: filters.usesPersonal,
        agentIssues: filters.agentIssues,
        guardrailModes: filters.guardrailModes,
        riskMin: filters.riskMin,
        riskMax: filters.riskMax,
    }),
    toCsvLines: (tab, rows) => {
        const columns = CSV_COLUMNS[tab];
        const header = columns.map(([title]) => func.csvCell(title)).join(",");
        const body = rows.map((row) => columns.map(([, read]) => func.csvCell(func.csvValue(read(row)))).join(","));
        return [header, ...body];
    },
};

export default transform;
