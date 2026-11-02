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
    agentActive: { tab: "devices", filters: {} },
    devicesWithAiTools: { tab: "devices", filters: { categories: ALL_CATEGORIES } },
    employeesUsingAi: { tab: "employees", filters: {} },
    devicesWithUnapprovedTools: { tab: "devices", filters: { hasUnapproved: true } },
    monitorOnlyDevices: { tab: "devices", filters: { guardrailModes: ["monitor"] } },
};

const GRAPH_COLUMNS_X = [40, 260, 480, 700];
const GRAPH_ROW_HEIGHT = 84;
const GRAPH_MIN_HEIGHT = 300;
const EDGE_COLOR = "#9ca3af";
const RISKY_EDGE_COLOR = "#D72C0D";

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

function graphNode(id, column, row, offset, category, type, label) {
    return {
        id,
        type: "topoNode",
        draggable: false,
        position: { x: GRAPH_COLUMNS_X[column], y: (row + offset) * GRAPH_ROW_HEIGHT },
        data: { component: { category, type, label } },
    };
}

function graphEdge(source, target, risky) {
    return {
        id: `e-${source}-${target}`,
        source,
        target,
        type: "smoothstep",
        style: { stroke: risky ? RISKY_EDGE_COLOR : EDGE_COLOR, strokeWidth: 1.5 },
    };
}

function capped(items) {
    if (items.length <= FLYOUT_GRAPH_LIMIT) return { shown: items, hidden: 0 };
    return { shown: items.slice(0, FLYOUT_GRAPH_LIMIT - 1), hidden: items.length - FLYOUT_GRAPH_LIMIT + 1 };
}

function toolType(tool) {
    const category = CATEGORY_LABELS[tool.category] || tool.category;
    if (tool.personal) return `${category} · Personal account`;
    return tool.approved ? category : `${category} · Unapproved`;
}

function clientMatches(client, agentName) {
    const left = (client || "").toLowerCase();
    const right = (agentName || "").toLowerCase();
    return !!left && !!right && (left.startsWith(right) || right.startsWith(left));
}

function buildGraph(root, devices) {
    const columns = [[root]];
    const edges = [];
    const toolColumn = root.devicesColumn ? 2 : 1;
    const deviceNodes = [];
    const agentNodes = [];
    const mcpNodes = [];

    const { shown: shownDevices, hidden: hiddenDevices } = capped(devices);
    shownDevices.forEach((device, deviceIndex) => {
        const deviceId = root.devicesColumn ? `device-${deviceIndex}` : "root";
        if (root.devicesColumn) {
            deviceNodes.push({ id: deviceId, category: "external", type: `${OS_LABELS[device.os] || device.os} · ${HEALTH_LABELS[device.health] || device.health}`, label: device.name });
            edges.push(graphEdge("root", deviceId, device.health !== "healthy"));
        }
        const tools = device.tools || [];
        const agents = tools.filter((tool) => tool.category !== "mcp");
        const mcps = tools.filter((tool) => tool.category === "mcp");
        const agentIds = new Map();
        agents.forEach((tool) => {
            const id = `tool-${tool.collectionId}`;
            agentIds.set(tool, id);
            agentNodes.push({ id, parent: deviceId, category: "agent", type: toolType(tool), label: tool.name, risky: !tool.approved || tool.personal });
        });
        mcps.forEach((tool) => {
            const parentTool = agents.find((agent) => clientMatches(tool.client, agent.name));
            mcpNodes.push({
                id: `tool-${tool.collectionId}`,
                parent: parentTool ? agentIds.get(parentTool) : deviceId,
                category: "mcp",
                type: toolType(tool),
                label: tool.name,
                risky: !tool.approved || tool.personal,
            });
        });
    });
    if (hiddenDevices > 0) deviceNodes.push({ id: "device-more", category: "external", type: "More", label: `+${hiddenDevices} more devices` });

    const visibleAgents = capped(agentNodes);
    const visibleMcps = capped(mcpNodes);
    if (visibleAgents.hidden > 0) visibleAgents.shown.push({ id: "tool-more", category: "agent", type: "More", label: `+${visibleAgents.hidden} more AI tools` });
    if (visibleMcps.hidden > 0) visibleMcps.shown.push({ id: "mcp-more", category: "mcp", type: "More", label: `+${visibleMcps.hidden} more MCP servers` });
    const visibleIds = new Set(["root", ...deviceNodes.map((n) => n.id), ...visibleAgents.shown.map((n) => n.id)]);

    if (root.devicesColumn) columns.push(deviceNodes);
    columns[toolColumn] = visibleAgents.shown;
    columns[toolColumn + 1] = visibleMcps.shown;

    visibleAgents.shown.concat(visibleMcps.shown).forEach((node) => {
        if (node.parent && visibleIds.has(node.parent)) edges.push(graphEdge(node.parent, node.id, node.risky));
    });

    const tallest = Math.max(1, ...columns.map((column) => column.length));
    const nodes = [];
    columns.forEach((column, columnIndex) => {
        const offset = (tallest - column.length) / 2;
        column.forEach((node, rowIndex) => {
            nodes.push(graphNode(node.id, columnIndex, rowIndex, offset, node.category, node.type, node.label));
        });
    });
    return { nodes, edges, height: `${Math.max(GRAPH_MIN_HEIGHT, tallest * GRAPH_ROW_HEIGHT + 60)}px` };
}

const transform = {
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
        ? buildGraph({ id: "root", category: "external", type: OS_LABELS[detail.os] || detail.os, label: detail.name }, [detail])
        : buildGraph({ id: "root", category: "external", type: (detail.teams || []).join(", ") || "Employee", label: detail.username, devicesColumn: true }, detail.deviceDetails || [])),
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
