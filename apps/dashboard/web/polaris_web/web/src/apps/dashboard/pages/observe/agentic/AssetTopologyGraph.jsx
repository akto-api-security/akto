import React, { useEffect, useMemo, useRef } from "react";
import ReactFlow, { Handle, Position, Background, Controls } from "react-flow-renderer";
import { Box, HorizontalStack, VerticalStack, Text, Card, Icon, Avatar, Tooltip } from "@shopify/polaris";
import { AutomationMajor, MagicMajor, CustomersMinor, ToolsMajor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import PluginIcon from "@/assets/Plugin.svg";
import { getAgentLinkedComponents } from "./agenticPageBuilders";
import { TOOL_EDGE_COLOR, useMcpTools, withToolRows } from "./topologyTools";

export function topoColors(category) {
    switch (category) {
        case "external": return { borderColor: "#3b82f6", backgroundColor: "#eff6ff" };
        case "agent":    return { borderColor: "#f97316", backgroundColor: "#fff7ed" };
        case "mcp":      return { borderColor: "#4cbebb", backgroundColor: "#ecfdf5" };
        // Tools hang off MCP nodes, so they deliberately don't reuse mcp's teal — amber keeps a
        // tool readable as its own thing rather than looking like another MCP Server.
        case "tool":     return { borderColor: TOOL_EDGE_COLOR, backgroundColor: "#FFFBEB" };
        case "ai-model": return { borderColor: "#ec4899", backgroundColor: "#fdf2f8" };
        case "skill":    return { borderColor: "#7C3AED", backgroundColor: "#F3E8FF" };
        case "plugin":   return { borderColor: "#4F46E5", backgroundColor: "#EEF2FF" };
        default:         return { borderColor: "#6b7280", backgroundColor: "#f9fafb" };
    }
}

export function topoIcon(category) {
    switch (category) {
        case "external": return CustomersMinor;
        case "agent":    return AutomationMajor;
        case "mcp":      return MCPIcon;
        case "tool":     return ToolsMajor;
        case "ai-model": return MagicMajor;
        case "skill":    return AutomationMajor;
        case "plugin":   return PluginIcon;
        default:         return CustomersMinor;
    }
}

export function TopoNode({ data }) {
    const { component } = data;
    const colors = topoColors(component.category);
    const IconComponent = topoIcon(component.category);
    const isDevice = component.category === "external";

    return (
        <>
            {!isDevice && <Handle type="target" position={Position.Left} />}
            <Card padding={0}>
                <Box style={{ border: `1px solid ${colors.borderColor}`, borderRadius: "8px", backgroundColor: colors.backgroundColor }}>
                    <Box padding={3}>
                        <VerticalStack gap={1}>
                            <Box width="150px">
                                <Tooltip content={component.type} preferredWidth={300}>
                                    <Text color="subdued" variant="bodySm" truncate>{component.type}</Text>
                                </Tooltip>
                            </Box>
                            <HorizontalStack gap={1} blockAlign="center">
                                {typeof IconComponent === "string"
                                    ? <Avatar source={IconComponent} size="extraSmall" />
                                    : <Icon source={IconComponent} />
                                }
                                <Box width={component.category === "ai-model" ? "140px" : "110px"}>
                                    <Tooltip content={component.label} preferredWidth={400}>
                                        <Text variant="bodySm" color="base" truncate>{component.label}</Text>
                                    </Tooltip>
                                </Box>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                </Box>
            </Card>
            <Handle type="source" position={Position.Right} id="b" />
        </>
    );
}

export const TOPO_NODE_TYPES = { topoNode: TopoNode };

const NODE_H = 84;
const GRAPH_H = 300;
const NODE_W = 176;    // rendered node box, for centering on the focus node
const FOCUS_ZOOM = 1;

// Opens centred on the asset the flyout is actually about (the device on Endpoints, the agent/MCP
// on Agentic Assets) instead of a fitView that shrinks everything to fit the widest branch.
function centerOnFocus(api, nodes, focusId) {
    const target = nodes.find(n => n.id === focusId);
    if (!target) { api.fitView({ padding: 0.2 }); return; }
    // getNode returns the store's copy, which carries the *measured* box — the node's width comes
    // from TopoNode's label widths, so hardcoding it here would drift the moment those change.
    const measured = api.getNode?.(focusId);
    const w = measured?.width || NODE_W;
    const h = measured?.height || NODE_H;
    api.setCenter(target.position.x + w / 2, target.position.y + h / 2, { zoom: FOCUS_ZOOM });
}

// Returns parent AI Agent flat rows for an MCP/Skill asset.
export function findParentAgents(asset, agenticFlatData = []) {
    return agenticFlatData.filter((a) => {
        if (a.type !== "AI Agent") return false;
        if (asset.type === "MCP Server") {
            return (a.mcpServers || []).some(m => m === asset.name || m.toLowerCase() === asset.name?.toLowerCase());
        }
        if (asset.type === "Skill") {
            const assetIds = new Set((asset.collectionIds || []).map(Number));
            return (a.collectionIds || []).some(id => assetIds.has(Number(id)));
        }
        if (asset.type === "Plugin") {
            return (a.pluginNames || []).some(p => p === asset.name || p.toLowerCase() === asset.name?.toLowerCase());
        }
        return false;
    });
}

// devices here is only ever a small server-capped sample now (see AgenticObserveAction's
// assetDeviceCount/assetDeviceSample comment), not the full per-device list — appends a synthetic
// "+N more" summary node (same "external"/User visual as a real device node) whenever the real
// total (deviceCount) exceeds the sample shipped, so a group with hundreds/thousands of devices
// doesn't silently render as if it only had a handful.
function buildDeviceItems(devices, deviceCount) {
    const items = devices.map((d, i) => ({ id: `dev-${i}`, label: d.username || d.endpoint, type: "User" }));
    const extra = Math.max(0, (deviceCount || 0) - devices.length);
    if (extra > 0) {
        items.push({ id: "dev-more", label: `+${extra} more`, type: "More" });
    }
    return items;
}

export default function AssetTopologyGraph({ asset, assetDevices = {}, agenticTreeData = [], agenticFlatData = [], inlineComponents = [], nodes: externalNodes, edges: externalEdges, height: externalHeight, focusNodeId }) {
    // Each MCP's own tools, keyed by collection id — the extra hop the Endpoints graph shows.
    // Only for our own layout; a caller that supplies nodes reserves its own tool rows.
    const mcpCollectionIds = useMemo(() => {
        if (externalNodes || asset?.type !== "AI Agent") return [];
        const ids = Object.values(asset.mcpServerCollectionIds || {}).map(a => a?.[0]).filter(Boolean);
        return [...new Set(ids)];
    }, [asset, externalNodes]);

    const mcpTools = useMcpTools(mcpCollectionIds);

    const { nodes, edges, height, focusId } = useMemo(() => {
        if (externalNodes && externalEdges) {
            // Callers that lay out their own nodes (DeviceFlyout.jsx) size the box to their content.
            return { nodes: externalNodes, edges: externalEdges, height: externalHeight || GRAPH_H, focusId: focusNodeId };
        }

        const devices = buildDeviceItems(assetDevices[asset.id] || [], asset.deviceCount);
        const COL1 = 40, COL2 = 230, COL3 = 420, COL4 = 610;

        if (asset.type === "AI Agent") {
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            const mcps = children.filter(c => c.type === "MCP Server");
            const llms = children.filter(c => c.type === "LLM");
            // Single summary node (not one per skill name) — the flyout's detail fetch only ships
            // skillCount now, not the full name list (Components tab re-derives the actual names
            // independently when opened; see AgenticObserveAction.fetchAgenticAssetDetail's comment).
            const skillCount = asset.skillCount || 0;
            const skillItems = skillCount > 0
                ? [{ id: "skl-0", cat: "skill", type: "Skill", label: skillCount === 1 ? "1 Skill" : `${skillCount} Skills`, edgeColor: "#7C3AED" }]
                : [];
            const pluginItems = (asset.pluginNames || []).map((name, i) => ({ id: `plg-${i}`, cat: "plugin", type: "Plugin", label: name, edgeColor: "#4F46E5" }));
            const inlineItems = (inlineComponents || []).map((item, i) => ({
                id: item.id || `inline-${i}`,
                cat: item.cat,
                type: item.type,
                label: item.label,
                edgeColor: item.edgeColor || "#9ca3af",
            }));

            // MCPs, LLMs, Skills, inline tools/LLM on agent host — same hierarchy level
            const col3Items = [
                ...mcps.map((m, i) => ({ id: `mcp-${i}`, cat: "mcp",      type: "MCP Server", label: m.name, edgeColor: "#4cbebb", collectionId: asset.mcpServerCollectionIds?.[m.name]?.[0] })),
                ...llms.map((l, i) => ({ id: `llm-${i}`, cat: "ai-model", type: "LLM",        label: l.name, edgeColor: "#ec4899" })),
                ...skillItems,
                ...pluginItems,
                ...inlineItems,
            ];

            const rows      = withToolRows(col3Items, mcpTools);
            const maxRows   = Math.max(devices.length, rows.length, 1);
            const totalH    = maxRows * NODE_H;
            const agentY    = (totalH - 44) / 2;
            const devOffset = Math.max(0, (rows.length - devices.length) * NODE_H / 2);

            return {
                height: GRAPH_H,
                focusId: "agent",
                nodes: [
                    { id: "agent", type: "topoNode", draggable: false, position: { x: COL2, y: agentY }, data: { component: { category: "agent", type: "AI Agent", label: asset.name } } },
                    ...devices.map((d, i) => ({ id: d.id, type: "topoNode", draggable: false, position: { x: COL1, y: devOffset + i * NODE_H }, data: { component: { category: "external", type: d.type, label: d.label } } })),
                    ...rows.map((row, i) => (row.tool
                        ? { id: row.tool.id, type: "topoNode", draggable: false, position: { x: COL4, y: i * NODE_H }, data: { component: { category: "tool", type: "Tool", label: row.tool.label } } }
                        : { id: row.item.id, type: "topoNode", draggable: false, position: { x: COL3, y: i * NODE_H }, data: { component: { category: row.item.cat, type: row.item.type, label: row.item.label } } })),
                ],
                edges: [
                    ...devices.map(d => ({ id: `e-${d.id}-a`,   source: d.id, target: "agent",   type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
                    ...rows.map(row => (row.tool
                        ? { id: `e-${row.tool.id}`, source: row.item.id, target: row.tool.id, type: "smoothstep", style: { stroke: TOOL_EDGE_COLOR, strokeWidth: 1.5 } }
                        : { id: `e-a-${row.item.id}`, source: "agent", target: row.item.id, type: "smoothstep", style: { stroke: row.item.edgeColor, strokeWidth: 1.5 } })),
                ],
            };
        }

        // MCP / Skill / Plugin / LLM: show Device → Parent Agent → This Asset
        // agenticFlatData is always [] in this layout, so findParentAgents never matches — a plugin
        // asset already carries its own parent agent's name directly (pluginParentAgent), so use that
        // instead of depending on agenticFlatData ever being populated.
        const parentAgents = asset.type === "Plugin" && asset.pluginParentAgent
            ? [{ name: asset.pluginParentAgent }]
            : findParentAgents(asset, agenticFlatData);
        const cat     = asset.type === "MCP Server" ? "mcp" : asset.type === "Skill" ? "skill" : asset.type === "Plugin" ? "plugin" : "ai-model";
        const edgeCol = asset.type === "MCP Server" ? "#4cbebb" : asset.type === "Skill" ? "#7C3AED" : asset.type === "Plugin" ? "#4F46E5" : "#ec4899";

        if (parentAgents.length > 0) {
            const maxRows   = Math.max(devices.length, parentAgents.length, 1);
            const totalH    = maxRows * NODE_H;
            const assetY    = (totalH - 44) / 2;
            const devOffset = Math.max(0, (parentAgents.length - devices.length) * NODE_H / 2);
            const agOffset  = Math.max(0, (devices.length - parentAgents.length) * NODE_H / 2);

            return {
                height: GRAPH_H,
                focusId: "asset",
                nodes: [
                    { id: "asset", type: "topoNode", draggable: false, position: { x: COL3, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                    ...parentAgents.map((a, i) => ({ id: `agt-${i}`, type: "topoNode", draggable: false, position: { x: COL2, y: agOffset + i * NODE_H }, data: { component: { category: "agent", type: "AI Agent", label: a.name } } })),
                    ...devices.map((d, i) => ({ id: d.id, type: "topoNode", draggable: false, position: { x: COL1, y: devOffset + i * NODE_H }, data: { component: { category: "external", type: d.type, label: d.label } } })),
                ],
                edges: [
                    ...devices.map(d => ({ id: `e-${d.id}-a0`, source: d.id, target: "agt-0", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
                    ...parentAgents.map((_, i) => ({ id: `e-a${i}-as`, source: `agt-${i}`, target: "asset", type: "smoothstep", style: { stroke: edgeCol, strokeWidth: 1.5 } })),
                ],
            };
        }

        // Fallback: Device → Asset
        const maxRows = Math.max(devices.length, 1);
        const totalH  = maxRows * NODE_H;
        const assetY  = (totalH - 44) / 2;
        return {
            height: GRAPH_H,
            focusId: "asset",
            nodes: [
                { id: "asset", type: "topoNode", draggable: false, position: { x: COL2, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                ...devices.map((d, i) => ({ id: d.id, type: "topoNode", draggable: false, position: { x: COL1, y: i * NODE_H }, data: { component: { category: "external", type: d.type, label: d.label } } })),
            ],
            edges: devices.map(d => ({ id: `e-${d.id}-a`, source: d.id, target: "asset", type: "smoothstep", style: { stroke: edgeCol, strokeWidth: 1.5 } })),
        };
    }, [asset, assetDevices, agenticTreeData, agenticFlatData, inlineComponents, mcpTools, externalNodes, externalEdges, externalHeight, focusNodeId]);

    // Re-centres on every graph change, not just init — the flyouts' detail and tool fetches land
    // after the first render, and the view should still be on the focus node once they do.
    const flow = useRef(null);
    useEffect(() => {
        if (flow.current) centerOnFocus(flow.current, nodes, focusId);
    }, [nodes, focusId]);

    return (
        <Box style={{ height, borderRadius: 8, border: "1px solid #E1E5E9", overflow: "hidden", background: "#F8FAFC" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={TOPO_NODE_TYPES}
                onInit={api => { flow.current = api; centerOnFocus(api, nodes, focusId); }}
                minZoom={0.2}
                maxZoom={4}
                nodesDraggable={true}
                nodesConnectable={false}
                elementsSelectable={false}
                zoomOnScroll
                zoomOnPinch
                panOnDrag
                preventScrolling={false}
            >
                <Background color="#E1E5E9" gap={16} />
                <Controls showInteractive={false} />
            </ReactFlow>
        </Box>
    );
}
