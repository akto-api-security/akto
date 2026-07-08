import React, { useMemo } from "react";
import ReactFlow, { Handle, Position, Background, Controls } from "react-flow-renderer";
import { Box, HorizontalStack, VerticalStack, Text, Card, Icon, Avatar, Tooltip } from "@shopify/polaris";
import { AutomationMajor, MagicMajor, CustomersMinor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import { getAgentLinkedComponents } from "./agenticPageBuilders";

export function topoColors(category) {
    switch (category) {
        case "external": return { borderColor: "#3b82f6", backgroundColor: "#eff6ff" };
        case "agent":    return { borderColor: "#f97316", backgroundColor: "#fff7ed" };
        case "mcp":      return { borderColor: "#4cbebb", backgroundColor: "#ecfdf5" };
        case "ai-model": return { borderColor: "#ec4899", backgroundColor: "#fdf2f8" };
        case "skill":    return { borderColor: "#7C3AED", backgroundColor: "#F3E8FF" };
        default:         return { borderColor: "#6b7280", backgroundColor: "#f9fafb" };
    }
}

export function topoIcon(category) {
    switch (category) {
        case "external": return CustomersMinor;
        case "agent":    return AutomationMajor;
        case "mcp":      return MCPIcon;
        case "ai-model": return MagicMajor;
        case "skill":    return AutomationMajor;
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
        return false;
    });
}

export default function AssetTopologyGraph({ asset, assetDevices = {}, agenticTreeData = [], agenticFlatData = [], inlineComponents = [], nodes: externalNodes, edges: externalEdges }) {
    const { nodes, edges, height } = useMemo(() => {
        if (externalNodes && externalEdges) {
            return { nodes: externalNodes, edges: externalEdges, height: GRAPH_H };
        }

        const devices = assetDevices[asset.id] || [];
        const COL1 = 40, COL2 = 230, COL3 = 420;

        if (asset.type === "AI Agent") {
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            const mcps = children.filter(c => c.type === "MCP Server");
            const llms = children.filter(c => c.type === "LLM");
            const names = asset.skillNames || [];
            const skillItems = names.map((name, i) => ({ id: `skl-${i}`, cat: "skill", type: "Skill", label: name, edgeColor: "#7C3AED" }));
            const inlineItems = (inlineComponents || []).map((item, i) => ({
                id: item.id || `inline-${i}`,
                cat: item.cat,
                type: item.type,
                label: item.label,
                edgeColor: item.edgeColor || "#9ca3af",
            }));

            // MCPs, LLMs, Skills, inline tools/LLM on agent host — same hierarchy level
            const col3Items = [
                ...mcps.map((m, i) => ({ id: `mcp-${i}`, cat: "mcp",      type: "MCP Server", label: m.name, edgeColor: "#4cbebb" })),
                ...llms.map((l, i) => ({ id: `llm-${i}`, cat: "ai-model", type: "LLM",        label: l.name, edgeColor: "#ec4899" })),
                ...skillItems,
                ...inlineItems,
            ];

            const maxRows   = Math.max(devices.length, col3Items.length, 1);
            const totalH    = maxRows * NODE_H;
            const agentY    = (totalH - 44) / 2;
            const devOffset = Math.max(0, (col3Items.length - devices.length) * NODE_H / 2);

            return {
                height: GRAPH_H,
                nodes: [
                    { id: "agent", type: "topoNode", draggable: false, position: { x: COL2, y: agentY }, data: { component: { category: "agent", type: "AI Agent", label: asset.name } } },
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: COL1, y: devOffset + i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
                    ...col3Items.map((item, i) => ({ id: item.id, type: "topoNode", draggable: false, position: { x: COL3, y: i * NODE_H }, data: { component: { category: item.cat, type: item.type, label: item.label } } })),
                ],
                edges: [
                    ...devices.map((_, i)   => ({ id: `e-d${i}-a`,     source: `dev-${i}`, target: "agent",   type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
                    ...col3Items.map(item   => ({ id: `e-a-${item.id}`, source: "agent",    target: item.id,  type: "smoothstep", style: { stroke: item.edgeColor, strokeWidth: 1.5 } })),
                ],
            };
        }

        // MCP / Skill / LLM: show Device → Parent Agent → This Asset
        const parentAgents = findParentAgents(asset, agenticFlatData);
        const cat     = asset.type === "MCP Server" ? "mcp" : asset.type === "Skill" ? "skill" : "ai-model";
        const edgeCol = asset.type === "MCP Server" ? "#4cbebb" : asset.type === "Skill" ? "#7C3AED" : "#ec4899";

        if (parentAgents.length > 0) {
            const maxRows   = Math.max(devices.length, parentAgents.length, 1);
            const totalH    = maxRows * NODE_H;
            const assetY    = (totalH - 44) / 2;
            const devOffset = Math.max(0, (parentAgents.length - devices.length) * NODE_H / 2);
            const agOffset  = Math.max(0, (devices.length - parentAgents.length) * NODE_H / 2);

            return {
                height: GRAPH_H,
                nodes: [
                    { id: "asset", type: "topoNode", draggable: false, position: { x: COL3, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                    ...parentAgents.map((a, i) => ({ id: `agt-${i}`, type: "topoNode", draggable: false, position: { x: COL2, y: agOffset + i * NODE_H }, data: { component: { category: "agent", type: "AI Agent", label: a.name } } })),
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: COL1, y: devOffset + i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
                ],
                edges: [
                    ...devices.map((_, i) => ({ id: `e-d${i}-a0`, source: `dev-${i}`, target: "agt-0", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
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
            nodes: [
                { id: "asset", type: "topoNode", draggable: false, position: { x: COL2, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: COL1, y: i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
            ],
            edges: devices.map((_, i) => ({ id: `e-d${i}-a`, source: `dev-${i}`, target: "asset", type: "smoothstep", style: { stroke: edgeCol, strokeWidth: 1.5 } })),
        };
    }, [asset, assetDevices, agenticTreeData, agenticFlatData, inlineComponents, externalNodes, externalEdges]);

    return (
        <Box style={{ height, borderRadius: 8, border: "1px solid #E1E5E9", overflow: "hidden", background: "#F8FAFC" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={TOPO_NODE_TYPES}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                onInit={api => api.fitView({ padding: 0.2 })}
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
