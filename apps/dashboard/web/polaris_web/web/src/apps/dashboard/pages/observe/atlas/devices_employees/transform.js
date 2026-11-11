import { Box, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import SpinnerCentered from "../../../../components/progress/SpinnerCentered";
import {
    BROWSER_ICONS, CATEGORY_LABELS, CATEGORY_ORDER, EMPTY_PEOPLE_FILTERS, FLYOUT_GRAPH_LIMIT, FLYOUT_GRAPH_STEP, HEALTH_LABELS, HEALTH_ORDER, MODE_LABELS, GENERIC_BROWSER_ICON, OS_ICON_KEYS, STAT_CARDS, STAT_EMPTY_VALUE, osLabel,
} from "./peopleConstants";
import { getDomainForFavicon } from "../../agentic/mcpClientHelper";

const ALL_CATEGORIES = ["agents", "mcp", "browser"];

const STAT_CARD_TARGETS = {
    managedDevices: { tab: "devices", filters: {} },
    agentActive: { tab: "devices", filters: { agentActive: true } },
    devicesWithAiTools: { tab: "devices", filters: { categories: ALL_CATEGORIES } },
    employeesUsingAi: { tab: "employees", filters: { usesAi: true } },
    devicesWithUnapprovedTools: { tab: "devices", filters: { hasUnapproved: true } },
    monitorOnlyDevices: { tab: "devices", filters: { guardrailModes: ["monitor"] } },
};

const FOOTPRINT_LAYOUT = { nodeWidth: 176, columnGap: 40, rowHeight: 84, headHeight: 40, viewWidth: 696, minHeight: 200, dimmedOpacity: 0.3, dimmedEdgeOpacity: 0.07 };
const FOOTPRINT_EDGE_COLOR = "#E1E3E5";

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
        ["Device", (row) => row.label || row.name],
        ["OS/Browser", (row) => (row.extension ? row.browser : osLabel(row.os))],
        ["Owner", (row) => row.owner],
        ["Tags", (row) => (row.tags || []).map((tag) => `${tag.key}=${tag.value}`)],
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

function statSubtexts(deviceStats, discovery) {
    const {
        managedDevices, desktopDevices, browserDevices, desktopActive, browserActive, offlineDevices, totalEmployees, unapprovedTeams, unapprovedNoTeam, monitorOnlyDevices,
    } = deviceStats;
    const agentActiveParts = [`${formatCount(desktopActive)} desktop`, `${formatCount(browserActive)} browsers`];
    if (offlineDevices > 0) agentActiveParts.push(`${formatCount(offlineDevices)} offline 7+ days`);
    const unapprovedParts = [];
    if (unapprovedTeams > 0) unapprovedParts.push(`Across ${plural(unapprovedTeams, "team")}`);
    if (unapprovedNoTeam > 0) unapprovedParts.push(`${formatCount(unapprovedNoTeam)} ${unapprovedNoTeam === 1 ? "employee" : "employees"} with no team`);
    const managedParts = [];
    if (desktopDevices > 0) managedParts.push(`${formatCount(desktopDevices)} desktop`);
    if (browserDevices > 0) managedParts.push(`${formatCount(browserDevices)} browsers`);
    return {
        managedDevices: managedParts.length ? { text: managedParts.join(" · ") } : null,
        agentActive: agentActiveParts.length ? { text: agentActiveParts.join(" · "), color: offlineDevices > 0 ? "critical" : undefined } : null,
        devicesWithAiTools: discovery && managedDevices > 0 ? { text: `${Math.min(100, Math.round((100 * discovery.devicesCount) / managedDevices))}% of fleet` } : null,
        employeesUsingAi: totalEmployees != null ? { text: `of ${formatCount(totalEmployees)}` } : null,
        devicesWithUnapprovedTools: unapprovedParts.length ? { text: unapprovedParts.join(" · "), color: "critical" } : null,
        monitorOnlyDevices: monitorOnlyDevices != null ? { text: "Not blocking violations", color: "critical" } : null,
    };
}

function statValue(value, subtext) {
    return (
        <VerticalStack gap="1">
            <Text as="p" variant="heading2xl">{formatCount(value)}</Text>
            {subtext && <Text as="p" variant="bodySm" fontWeight="medium" color={subtext.color || "subdued"}>{subtext.text}</Text>}
        </VerticalStack>
    );
}

function capped(items, limit) {
    if (items.length <= limit) return { shown: items, hidden: 0 };
    const shown = items.slice(0, limit - 1);
    return { shown, hidden: items.length - shown.length };
}

function balanced(groups, limit) {
    const total = groups.reduce((sum, group) => sum + group.length, 0);
    if (total <= limit) return { shown: groups.flat(), hidden: 0 };
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
    return { shown, hidden: total - shown.length };
}

const defaultFootprintView = () => ({ devices: FLYOUT_GRAPH_LIMIT, agents: FLYOUT_GRAPH_LIMIT, mcps: FLYOUT_GRAPH_LIMIT, skills: {} });

function toolStatus(tool) {
    return !tool.approved || tool.personal ? "bad" : "ok";
}

function toolSub(tool) {
    if (tool.plugin) return tool.skills?.length ? `Plugin · ${plural(tool.skillCount, "skill")}` : "Plugin";
    const category = CATEGORY_LABELS[tool.category] || tool.category;
    if (tool.category === "mcp") return tool.approved ? "Approved" : "Unapproved";
    return tool.personal ? `${category} · Personal account` : category;
}

function clientMatches(client, agentName) {
    const left = (client || "").toLowerCase();
    const right = (agentName || "").toLowerCase();
    return !!left && !!right && (left.startsWith(right) || right.startsWith(left));
}

function moreNode(id, category, count, noun) {
    return { id, category, label: `+${count} more ${noun}`, sub: "Select to show more", status: "neutral" };
}

const faviconUrl = (name) => {
    const domain = getDomainForFavicon(name);
    return domain ? `https://www.google.com/s2/favicons?domain=${domain}&sz=64` : undefined;
};

const deviceIcon = (device) => (device.extension
    ? BROWSER_ICONS[device.browser] || GENERIC_BROWSER_ICON
    : OS_ICON_KEYS[device.os] ? `/public/os-${OS_ICON_KEYS[device.os]}.svg` : undefined);

function footprintModel(root, devices, heads, view) {
    const withDevices = !!root.devicesColumn;
    const edges = [];
    const actions = {};
    const skillSources = {};
    const deviceNodes = [];
    const agentNodes = [];
    const mcpNodes = [];
    const pluginNodes = [];
    const skillNodes = [];
    const link = (source, target, status) => edges.push({ source, target, risky: status === "bad" });

    const visibleDevices = capped(devices, view.devices);
    visibleDevices.shown.forEach((device, deviceIndex) => {
        const deviceId = withDevices ? `device-${deviceIndex}` : "root";
        if (withDevices) {
            deviceNodes.push({ id: deviceId, category: "external", iconSrc: deviceIcon(device), label: device.name, sub: [osLabel(device.os), HEALTH_LABELS[device.health] || device.health].filter(Boolean).join(" · "), status: device.health === "healthy" ? "neutral" : "review" });
            link("root", deviceId, "ok");
        }
        const tools = device.tools || [];
        const agents = tools.filter((tool) => tool.category !== "mcp" && !tool.plugin && !tool.orphan);
        const parentOf = (tool) => {
            const parentTool = agents.find((agent) => clientMatches(tool.client, agent.name));
            return parentTool ? `tool-${parentTool.collectionId}` : deviceId;
        };
        agents.forEach((tool) => {
            agentNodes.push({ id: `tool-${tool.collectionId}`, category: "agent", iconSrc: faviconUrl(tool.name), label: tool.name, sub: toolSub(tool), status: toolStatus(tool), parent: deviceId });
        });
        tools.filter((tool) => tool.category === "mcp" && !tool.plugin).forEach((tool) => {
            mcpNodes.push({ id: `tool-${tool.collectionId}`, category: "mcp", iconSrc: faviconUrl(tool.name), label: tool.name, sub: toolSub(tool), status: toolStatus(tool), parent: parentOf(tool) });
        });
        tools.filter((tool) => tool.plugin).forEach((tool) => {
            const id = `plugin-${tool.collectionId}`;
            if (tool.skills?.length) skillSources[id] = tool;
            pluginNodes.push({ id, category: "plugin", label: tool.plugin, sub: toolSub(tool), status: "neutral", parent: parentOf(tool) });
        });
        const skillOwners = agents.filter((tool) => tool.skillCount > 0).map((tool) => ({ tool, sub: "Skills", parent: `tool-${tool.collectionId}` }))
            .concat(tools.filter((tool) => tool.orphan && tool.skillCount > 0).map((tool) => ({ tool, sub: "Not attached to an agent", parent: deviceId })));
        skillOwners.forEach(({ tool, sub, parent }) => {
            const id = `skills-${tool.collectionId}`;
            const expandable = !!tool.skills?.length;
            if (expandable) skillSources[id] = tool;
            const hint = expandable ? `${sub} · select to ${view.skills[id] === undefined ? "expand" : "collapse"}` : sub;
            skillNodes.push({ id, category: "skill", label: plural(tool.skillCount, "skill"), sub: hint, status: "neutral", parent });
        });
    });
    if (visibleDevices.hidden > 0) {
        deviceNodes.push(moreNode("device-more", "external", visibleDevices.hidden, "devices"));
        actions["device-more"] = { type: "more", key: "devices" };
    }

    const visibleAgents = capped(agentNodes, view.agents);
    const visibleMcps = balanced([mcpNodes, pluginNodes, skillNodes], view.mcps);
    if (visibleAgents.hidden > 0) {
        visibleAgents.shown.push(moreNode("tool-more", "agent", visibleAgents.hidden, "AI tools"));
        actions["tool-more"] = { type: "more", key: "agents" };
    }
    if (visibleMcps.hidden > 0) {
        visibleMcps.shown.push(moreNode("mcp-more", "mcp", visibleMcps.hidden, "MCP servers, plugins and skills"));
        actions["mcp-more"] = { type: "more", key: "mcps" };
    }

    const skillChildren = [];
    visibleMcps.shown.forEach((node) => {
        const tool = skillSources[node.id];
        if (!tool) return;
        actions[node.id] = { type: "skills-toggle", id: node.id };
        const limit = view.skills[node.id];
        if (limit === undefined) return;
        tool.skills.slice(0, limit).forEach((name, index) => {
            const child = { id: `skill-${node.id}-${index}`, category: "skill", label: name, sub: "Skill", status: "neutral", parent: node.id };
            skillChildren.push(child);
            link(node.id, child.id, "neutral");
        });
        if (tool.skills.length > limit) {
            const more = moreNode(`skillmore-${node.id}`, "skill", tool.skills.length - limit, "skills");
            skillChildren.push(more);
            actions[more.id] = { type: "skills-more", id: node.id };
            link(node.id, more.id, "neutral");
        }
    });

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
    const columnHeads = [...heads];
    if (skillChildren.length) {
        columns.push(skillChildren);
        columnHeads.push("Skills");
    }
    return { heads: columnHeads, columns, edges, actions };
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
                data: { component: { category: node.category, iconSrc: node.iconSrc, type: node.sub || "Select to list", label: node.label, status: node.status, tooltip: node.tooltip, selected: highlight === node.id } },
            });
        });
    });
    const edges = model.edges.map((edge) => {
        const edgeLit = !lit || (lit.has(edge.source) && lit.has(edge.target));
        return {
            id: `e-${edge.source}-${edge.target}`,
            source: edge.source,
            target: edge.target,
            type: "default",
            style: { stroke: FOOTPRINT_EDGE_COLOR, strokeWidth: 1.5, opacity: edgeLit ? 1 : layout.dimmedEdgeOpacity },
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
    tooltip: (text) => <Box width="350px">{text}</Box>,
    activeStatCard: (tab, filters, clickedId) => Object.keys(STAT_CARD_TARGETS).find((id) => {
        const target = STAT_CARD_TARGETS[id];
        return (Object.keys(target.filters).length > 0 || id === clickedId) && target.tab === tab && JSON.stringify({ ...EMPTY_PEOPLE_FILTERS, ...target.filters }) === JSON.stringify(filters);
    }),
    filterOptions: (overview) => ({
        teams: overview?.teamOptions || [],
        tags: overview?.tagOptions || [],
        osBrowser: [...(overview?.osMatrix || []).filter((row) => row.os !== "unknown").map((row) => `os:${row.os}`), ...(overview?.browserMatrix || []).map((row) => `browser:${row.browser}`)],
        healthStates: HEALTH_ORDER.filter((state) => (overview?.agentHealth?.[state] || 0) > 0),
        guardrailModes: Object.keys(MODE_LABELS).filter((mode) => (overview?.guardrailModes?.[mode] || 0) > 0),
        categories: CATEGORY_ORDER.filter((category) => (overview?.categoryTotals?.[category] || 0) > 0),
    }),
    buildStatItems: (deviceStats, onSelect, discovery, activeId) => {
        const subtexts = deviceStats ? statSubtexts(deviceStats, discovery) : {};
        const values = { ...deviceStats, devicesWithAiTools: discovery?.devicesCount, employeesUsingAi: discovery?.usersCount };
        return STAT_CARDS.map((card) => ({
            title: card.label,
            isComp: true,
            data: statValue(values[card.id], subtexts[card.id]),
            onClick: () => onSelect(card.id),
            selected: card.id === activeId,
        }));
    },
    cardBody: (status, body) => {
        if (status === "loading") return <SpinnerCentered />;
        if (status === "error") return <Text as="p" color="subdued">This card could not be loaded. The other cards are unaffected.</Text>;
        return body;
    },
    defaultFootprintView,
    isDefaultFootprintView: (view) => JSON.stringify(view) === JSON.stringify(defaultFootprintView()),
    nextFootprintView: (view, action) => {
        if (action.type === "more") return { ...view, [action.key]: view[action.key] + FLYOUT_GRAPH_STEP };
        const skills = { ...view.skills };
        if (action.type === "skills-more") skills[action.id] += FLYOUT_GRAPH_STEP;
        else if (skills[action.id] === undefined) skills[action.id] = FLYOUT_GRAPH_STEP;
        else delete skills[action.id];
        return { ...view, skills };
    },
    footprintGraph: (kind, detail, view) => (kind === "device"
        ? footprintModel({ label: detail.name, sub: osLabel(detail.os) }, [detail], ["Device", "AI tools", "MCP servers, plugins and skills"], view)
        : footprintModel({ label: detail.username, sub: (detail.teams || []).join(", ") || "Employee", devicesColumn: true }, detail.deviceDetails || [], ["Employee", "Devices", "AI tools", "MCP servers, plugins and skills"], view)),
    employeeAiToolCount: (employee) => (employee.deviceDetails || []).reduce(
        (total, device) => total + device.toolCount - (device.toolsByCategory?.mcp || 0), 0),
    pluginCount: (tools) => tools.filter((tool) => tool.plugin).length,
    assetDeviceOptions: (detail) => {
        const seen = new Set();
        const options = [];
        (detail.deviceDetails || []).forEach((device) => {
            if (!(device.toolCount > 0 || device.skillCount > 0)) return;
            const firstHost = (device.tools || []).map((tool) => tool.hostName).find(Boolean);
            const value = firstHost ? firstHost.split(".")[0] : (device.extension ? "" : device.name);
            if (!value || seen.has(value)) return;
            seen.add(value);
            options.push({ label: device.label || device.name || value, value });
        });
        return options;
    },
    flyoutHosts: (detail, isDevice) => {
        const devices = isDevice ? [detail] : detail.deviceDetails || [];
        const hostNames = [...new Set(devices.flatMap((device) => (device.tools || []).map((tool) => tool.hostName)).filter(Boolean))];
        const prefix = hostNames.length ? hostNames[0].split(".")[0] : "";
        const deviceId = isDevice ? (prefix || (detail.extension ? undefined : detail.name)) : undefined;
        return { hostNames, deviceId };
    },
    peopleParams: (deviceScope, filters, searchString) => ({
        deviceScope,
        atlasQuery: searchString,
        teams: filters.teams,
        tagPairs: filters.tags,
        violationSeverities: filters.violationSeverities,
        atlasOs: filters.osList,
        atlasBrowsers: filters.browserList,
        toolCategories: filters.categories,
        healthStates: filters.healthStates,
        hasUnapproved: filters.hasUnapproved,
        usesPersonal: filters.usesPersonal,
        agentIssues: filters.agentIssues,
        usesAi: filters.usesAi,
        agentActive: filters.agentActive,
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
