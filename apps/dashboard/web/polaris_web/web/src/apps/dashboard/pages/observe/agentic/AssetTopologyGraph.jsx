import React, { useMemo } from "react";
import ReactFlow, { Handle, Position, Background, Controls } from "react-flow-renderer";
import { Box, HorizontalStack, VerticalStack, Text, Card, Icon, Avatar, Tooltip } from "@shopify/polaris";
import { AutomationMajor, MagicMajor, CustomersMinor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import { getAgentLinkedComponents } from "./agenticPageBuilders";

// ─── Topology graph ───────────────────────────────────────────────────────────
// ReactFlow node colours are category-driven (outside the Polaris token set) and the
// fixed graph canvas size is a layout constant — inline styles are justified here.

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

const GRAPH_HEIGHT = 280;
const NODE_H       = 84;

export default function AssetTopologyGraph({ asset, assetDevices = {}, agenticTreeData = [], agenticFlatData = [], nodes: externalNodes, edges: externalEdges }) {
    if (externalNodes && externalEdges) {
        return (
            <Box style={{ height: GRAPH_HEIGHT, borderRadius: 8, border: "1px solid #E1E5E9", overflow: "hidden", background: "#F8FAFC" }}>
                <ReactFlow
                    nodes={externalNodes}
                    edges={externalEdges}
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

    const { nodes, edges } = useMemo(() => {
        const devices = assetDevices[asset.id] || [];

        if (asset.type === "AI Agent") {
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            const mcps = children.filter(c => c.type === "MCP Server");
            const llms = children.filter(c => c.type === "LLM");
            const rightCount = mcps.length + llms.length;
            const maxRows  = Math.max(devices.length, rightCount, 1);
            const totalH   = maxRows * NODE_H;
            const agentY   = (totalH - 44) / 2;
            const devOffset = Math.max(0, (rightCount - devices.length) * NODE_H / 2);

            return {
                nodes: [
                    { id: "agent", type: "topoNode", draggable: false, position: { x: 270, y: agentY }, data: { component: { category: "agent",    type: "AI Agent",    label: asset.name } } },
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40,  y: devOffset + i * NODE_H          }, data: { component: { category: "external",  type: "User",        label: d.username || d.endpoint } } })),
                    ...mcps.map((m, i)    => ({ id: `mcp-${i}`, type: "topoNode", draggable: false, position: { x: 500, y: i * NODE_H                       }, data: { component: { category: "mcp",       type: "MCP Server",  label: m.name     } } })),
                    ...llms.map((l, i)    => ({ id: `llm-${i}`, type: "topoNode", draggable: false, position: { x: 500, y: (mcps.length + i) * NODE_H       }, data: { component: { category: "ai-model",  type: "LLM",         label: l.name     } } })),
                ],
                edges: [
                    ...devices.map((_, i) => ({ id: `e-d${i}-a`,  source: `dev-${i}`, target: "agent",   type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
                    ...mcps.map((_, i)    => ({ id: `e-a-m${i}`,  source: "agent",    target: `mcp-${i}`, type: "smoothstep", style: { stroke: "#4cbebb", strokeWidth: 1.5 } })),
                    ...llms.map((_, i)    => ({ id: `e-a-l${i}`,  source: "agent",    target: `llm-${i}`, type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } })),
                ],
            };
        }

        if (asset.type === "Skill") {
            // Devices (left, sources) → Skill (right, target)
            const totalH  = Math.max(devices.length, 1) * NODE_H;
            const skillY  = (totalH - 44) / 2;
            return {
                nodes: [
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40,  y: i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
                    { id: "skill", type: "topoNode", draggable: false, position: { x: 380, y: skillY }, data: { component: { category: "skill", type: "Skill", label: asset.name } } },
                ],
                edges: devices.map((_, i) => ({ id: `e-d${i}-s`, source: `dev-${i}`, target: "skill", type: "smoothstep", style: { stroke: "#7E22CE", strokeWidth: 1.5 } })),
            };
        }

        // MCP Server & LLM: Devices (left) → Asset (right)
        const totalH  = Math.max(devices.length, 1) * NODE_H;
        const assetY  = (totalH - 44) / 2;
        const cat     = asset.type === "MCP Server" ? "mcp" : "ai-model";
        const edgeCol = asset.type === "MCP Server" ? "#4cbebb" : "#ec4899";
        return {
            nodes: [
                { id: "asset", type: "topoNode", draggable: false, position: { x: 310, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40, y: i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
            ],
            edges: devices.map((_, i) => ({ id: `e-d${i}-a`, source: `dev-${i}`, target: "asset", type: "smoothstep", style: { stroke: edgeCol, strokeWidth: 1.5 } })),
        };
    }, [asset, assetDevices, agenticTreeData, agenticFlatData]);

    // Fixed-size container — ReactFlow's fitView + zoom handles overflow
    return (
        <Box style={{ height: GRAPH_HEIGHT, borderRadius: 8, border: "1px solid #E1E5E9", overflow: "hidden", background: "#F8FAFC" }}>
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
