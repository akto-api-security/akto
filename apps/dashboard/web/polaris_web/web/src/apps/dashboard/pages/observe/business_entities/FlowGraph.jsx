import { useMemo } from "react";
import ReactFlow, { Background, Controls, EdgeText, getBezierEdgeCenter, getBezierPath, Handle, Position } from "react-flow-renderer";
import "react-flow-renderer/dist/style.css";
import { graphlib, layout } from "@dagrejs/dagre";
import { Badge, Text } from "@shopify/polaris";
import { OPERATION_LABELS } from "./BusinessEntityDetails";

const NODE_WIDTH = 240;
const NODE_HEIGHT = 84;
const STEP_COLOR = "#4b3fb3";
const LOOP_COLOR = "#8c9196";

export const humanize = (value) => {
    const text = (value || "").replace(/_/g, " ");
    return text.charAt(0).toUpperCase() + text.slice(1);
};

export const nodeLabel = (node) =>
    `${humanize(node.entityId)} · ${OPERATION_LABELS[node.operation] || node.operation}${node.action ? ` (${humanize(node.action)})` : ""}`;

const pct = (value) => `${Math.round((value || 0) * 100)}%`;

function FlowNode({ data }) {
    const { node } = data;
    const example = (node.exampleApis || [])[0];
    return (
        <div style={{ background: "#ffffff", border: "1px solid #c9cccf", borderLeft: `4px solid ${STEP_COLOR}`, borderRadius: "6px",
            padding: "8px 10px", width: `${NODE_WIDTH}px`, boxSizing: "border-box", boxShadow: "0 1px 3px rgba(0,0,0,0.1)" }}>
            <Handle type="target" position={Position.Left} id="t" style={{ background: "#555" }} />
            <Text variant="headingSm" truncate>{humanize(node.entityId)}</Text>
            <div style={{ display: "flex", gap: "4px", margin: "4px 0" }}>
                <Badge status="info">{OPERATION_LABELS[node.operation] || node.operation}</Badge>
                {node.action && <Badge>{humanize(node.action)}</Badge>}
            </div>
            {example && <Text variant="bodySm" color="subdued" truncate>{`${example.method} ${example.url}`}</Text>}
            <Handle type="source" position={Position.Right} id="s" style={{ background: "#555" }} />
        </div>
    );
}

function FlowEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data }) {
    const params = { sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition };
    const path = getBezierPath(params);
    const [labelX, labelY] = getBezierEdgeCenter(params);
    const isLoop = data.edge.kind === "loop";
    const color = isLoop ? LOOP_COLOR : STEP_COLOR;
    const label = isLoop
        ? `loop · ${data.edge.count.toLocaleString()}×`
        : `← ${pct(data.edge.comesFrom)} · ${pct(data.edge.leadsTo)} → · ${data.edge.count.toLocaleString()}×`;
    return (
        <g>
            <defs>
                <marker id={`flow-arrow-${id}`} markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="userSpaceOnUse">
                    <path d="M0,0 L10,6 L0,12 Z" fill={color} />
                </marker>
                <marker id={`flow-arrow-start-${id}`} markerWidth="12" markerHeight="12" refX="0" refY="6" orient="auto" markerUnits="userSpaceOnUse">
                    <path d="M10,0 L0,6 L10,12 Z" fill={color} />
                </marker>
            </defs>
            <path id={id} className="react-flow__edge-path" d={path} fill="none"
                style={{ stroke: color, strokeWidth: isLoop ? 1.5 : 2.5, strokeDasharray: isLoop ? "6 4" : undefined }}
                markerEnd={`url(#flow-arrow-${id})`} markerStart={isLoop ? `url(#flow-arrow-start-${id})` : undefined} />
            <EdgeText x={labelX} y={labelY} label={label} labelStyle={{ fontSize: 11, fontWeight: 600, fill: color }}
                labelShowBg labelBgStyle={{ fill: "#ffffff", stroke: color, strokeWidth: 0.5 }} labelBgPadding={[6, 3]} labelBgBorderRadius={4} />
        </g>
    );
}

const nodeTypes = { flowNode: FlowNode };
const edgeTypes = { flowEdge: FlowEdge };

function buildLayout(flow) {
    const graph = new graphlib.Graph();
    graph.setGraph({ rankdir: "LR", nodesep: 40, ranksep: 180, marginx: 20, marginy: 20 });
    graph.setDefaultEdgeLabel(() => ({}));
    const ids = new Map((flow.nodes || []).map((node, index) => [node.id, `n${index}`]));
    (flow.nodes || []).forEach((node) => graph.setNode(ids.get(node.id), { width: NODE_WIDTH, height: NODE_HEIGHT }));
    const edges = (flow.edges || []).filter((edge) => ids.has(edge.from) && ids.has(edge.to));
    edges.forEach((edge) => graph.setEdge(ids.get(edge.from), ids.get(edge.to)));
    layout(graph);

    const nodes = (flow.nodes || []).map((node) => {
        const { x, y } = graph.node(ids.get(node.id));
        return { id: ids.get(node.id), type: "flowNode", data: { node }, position: { x: x - NODE_WIDTH / 2, y: y - NODE_HEIGHT / 2 } };
    });
    const flowEdges = edges.map((edge, index) => ({
        id: `e${index}`, source: ids.get(edge.from), target: ids.get(edge.to), sourceHandle: "s", targetHandle: "t",
        type: "flowEdge", data: { edge }
    }));
    return { nodes, edges: flowEdges };
}

function FlowGraph({ flow, height = "55vh" }) {
    const { nodes, edges } = useMemo(() => buildLayout(flow), [flow]);
    return (
        <div style={{ height, minHeight: "420px", border: "1px solid #e1e3e5", borderRadius: "8px" }}>
            <ReactFlow key={flow.id} nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} fitView minZoom={0.1} maxZoom={2}>
                <Background color="#aaa" gap={16} />
                <Controls />
            </ReactFlow>
        </div>
    );
}

export default FlowGraph;
