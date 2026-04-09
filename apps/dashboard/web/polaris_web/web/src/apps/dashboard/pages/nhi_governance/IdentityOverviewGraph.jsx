import { useMemo } from "react";
import { Box } from "@shopify/polaris";
import ReactFlow, { Background } from "react-flow-renderer";
import { AgentNode, AgentEdge } from "../observe/api_collections/AgentDiscoverGraph";
import { getAgentType } from "./nhiViolationsData";

const NODE_TYPES = { agentNode: AgentNode };
const EDGE_TYPES = { agentEdge: AgentEdge };

const NODE_W    = 168;  // approximate node width
const IDENT_SEP = 90;   // vertical gap between identity nodes
const H_GAP     = 80;   // horizontal gap between agent and its identities
const GROUP_GAP = 60;   // horizontal gap between groups

export default function IdentityOverviewGraph({ tableData, onIdentityClick }) {
    const { nodes, edges } = useMemo(() => {
        // Group ALL identities by agent, preserving insertion order
        const agentOrder = [];
        const groups = {};
        tableData.forEach(row => {
            if (!groups[row.agent]) { groups[row.agent] = []; agentOrder.push(row.agent); }
            groups[row.agent].push(row);
        });

        const nodes = [];
        const edges = [];
        let groupX = 0;

        agentOrder.forEach((agentName) => {
            const identities = groups[agentName];
            const n = identities.length;
            const totalH = (n - 1) * IDENT_SEP;
            const centerY = totalH / 2;

            // ── Agent node ────────────────────────────────────────────────────
            nodes.push({
                id: `a-${agentName}`,
                type: "agentNode",
                position: { x: groupX, y: centerY },
                draggable: false,
                data: {
                    component: {
                        id: `a-${agentName}`,
                        label: agentName,
                        type: getAgentType(agentName),
                        category: "agent",
                        description: `${n} identities`,
                        status: "active",
                    },
                    onNodeClick: null,
                },
            });

            // ── Identity nodes — stacked vertically to the right of agent ─────
            const identX = groupX + NODE_W + H_GAP;
            identities.forEach((row, i) => {
                const totalV = row.totalViolations;
                const identY = i * IDENT_SEP;

                nodes.push({
                    id: `i-${row.id}`,
                    type: "agentNode",
                    position: { x: identX, y: identY },
                    draggable: false,
                    data: {
                        component: {
                            id: `i-${row.id}`,
                            label: row.identityName,
                            type: row.type,
                            category: totalV > 0 ? "violation" : "mcp",
                            description: totalV > 0
                                ? `${totalV} violation${totalV !== 1 ? "s" : ""} · ${row.access}`
                                : `${row.access} access`,
                            status: "active",
                        },
                        onNodeClick: () => onIdentityClick(row),
                    },
                });

                // Agent (right handle "b") → identity (left handle, no id)
                edges.push({
                    id: `e-a-${agentName}-${row.id}`,
                    source: `a-${agentName}`,
                    target: `i-${row.id}`,
                    sourceHandle: "b",
                    type: "agentEdge",
                    data: {},
                });
            });

            groupX += NODE_W + H_GAP + NODE_W + GROUP_GAP;
        });

        return { nodes, edges };
    }, [tableData, onIdentityClick]);

    return (
        <Box
            style={{
                height: 480,
                border: "1px solid #E4E5E7",
                borderRadius: 8,
                overflow: "hidden",
                background: "#FFFFFF",
                cursor: "grab",
            }}
            onMouseDown={(e) => { e.currentTarget.style.cursor = "grabbing"; }}
            onMouseUp={(e)   => { e.currentTarget.style.cursor = "grab"; }}
            onMouseLeave={(e) => { e.currentTarget.style.cursor = "grab"; }}
        >
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={NODE_TYPES}
                edgeTypes={EDGE_TYPES}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                panOnDrag
                zoomOnScroll
                fitView
                fitViewOptions={{ padding: 0.06 }}
                minZoom={0.05}
                maxZoom={1.5}
            >
                <Background variant="dots" gap={24} size={1} color="#D1D5DB" />
            </ReactFlow>
        </Box>
    );
}
