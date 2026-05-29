import { useMemo } from "react";
import { Box } from "@shopify/polaris";
import ReactFlow from "react-flow-renderer";
import { AgentNode, AgentEdge } from "../observe/api_collections/AgentDiscoverGraph";
import { isAgenticSecurityCategory } from "../../../main/labelHelper";
import { getAgentType } from "./nhiViolationsData";

const NODE_TYPES = { agentNode: AgentNode };
const EDGE_TYPES = { agentEdge: AgentEdge };

export default function IdentityGraph({ row }) {
    const { nodes, edges, height } = useMemo(() => {
        const isArgus = isAgenticSecurityCategory();

        const resources = (row.targetResource || []).map((tr) => ({
            label: tr.resourceName,
            type: tr.resourceType,
            accessLevel: tr.accessLevel,
            category: tr.resourceType === "Agentic" ? "ai-model" : "ai-tool",
        }));

        const n = resources.length;
        const SPACING = 82;
        const totalH = (n - 1) * SPACING;
        const centerY = Math.max(10, totalH / 2);
        const graphHeight = Math.max(150, totalH + 90);

        const makeNode = (id, x, y, component) => ({
            id,
            type: "agentNode",
            position: { x, y },
            draggable: false,
            data: { component: { id, ...component }, onNodeClick: null },
        });

        // Atlas: owner → agent → identity → resources
        // Argus:          agent → identity → resources  (no owner)
        const agentX    = isArgus ? 10  : 240;
        const identityX = isArgus ? 240 : 470;
        const resourceX = isArgus ? 470 : 700;

        const nodes = [
            ...(isArgus ? [] : [
                makeNode("owner", 10, centerY, {
                    label: row.owner || "Unknown",
                    type:  "Owner",
                    category: "internal",
                    description: `Owner of ${row.identityName}`,
                    status: "active",
                }),
            ]),
            makeNode("agent", agentX, centerY, {
                label: row.agent,
                type:  getAgentType(row.agent),
                category: "agent",
                description: `Agent using ${row.identityName}`,
                status: "active",
            }),
            makeNode("identity", identityX, centerY, {
                label: row.identityName,
                type:  row.type,
                category: "mcp",
                description: `${row.access} access via ${row.type}`,
                status: "active",
            }),
            ...resources.map((r, i) => makeNode(
                `res-${i}`,
                resourceX,
                centerY - totalH / 2 + i * SPACING,
                {
                    label: r.label,
                    type:  r.type,
                    category: r.category,
                    description: `${r.type}: ${r.label}`,
                    status: "connected",
                }
            )),
        ];

        const edges = [
            ...(isArgus ? [] : [
                { id: "e-o-a", source: "owner", target: "agent", type: "agentEdge", data: {} },
            ]),
            { id: "e-a-i", source: "agent",    target: "identity", type: "agentEdge", data: {} },
            ...resources.map((resource, i) => ({
                id: `e-i-r${i}`,
                source: "identity",
                target: `res-${i}`,
                type: "agentEdge",
                // Use accessLevel from resource if available (from API), otherwise use row.access (fallback)
                data: { edgeParam: resource.accessLevel || row.access },
            })),
        ];

        return { nodes, edges, height: graphHeight };
    }, [row]);

    return (
        <Box style={{ height, border: "1px solid #E4E5E7", borderRadius: 8, overflow: "hidden", background: "#F6F6F7" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={NODE_TYPES}
                edgeTypes={EDGE_TYPES}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                panOnDrag={false}
                zoomOnScroll={false}
                fitView
                fitViewOptions={{ padding: 0.15 }}
            />
        </Box>
    );
}
