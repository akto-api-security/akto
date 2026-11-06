const LAYOUT = { nodeWidth: 176, columnGap: 40, maxColumnGap: 520, rowHeight: 84, headHeight: 40, minHeight: 240, dimmedOpacity: 0.3, dimmedEdgeOpacity: 0.07 };
const EDGE_COLOR = "#B38CFF";
const RISKY_EDGE_COLOR = "#E0442A";
const REVIEW_EDGE_COLOR = "#E4C300";
const BASE_EDGE_WIDTH = 1;
const MAX_EDGE_WIDTH_BOOST = 3.5;
const EDGE_WIDTH_SCALE = 1.2;
const EDGE_OPACITY = 0.75;
const BAD = "bad";
const REVIEW = "review";

// Team nodes carry no status, so a status pick lets them through.
const TEAM_CATEGORY = "external";
// A legend pick that lights only the paths touching a "bad" node.
export const RISKY_FOCUS = "risky";

// Everything upstream and downstream of the `starts`, but not their siblings.
function connectedIds(edges, starts) {
    const lit = new Set(starts);
    [["source", "target"], ["target", "source"]].forEach(([from, to]) => {
        const seen = new Set(starts);
        const queue = [...starts];
        while (queue.length) {
            const current = queue.pop();
            edges.forEach((edge) => {
                if (edge[from] === current && !seen.has(edge[to])) {
                    seen.add(edge[to]);
                    lit.add(edge[to]);
                    queue.push(edge[to]);
                }
            });
        }
    });
    return lit;
}

// The risky edges plus every edge feeding them, so a risky path runs from its team to its MCP server.
function riskyPathEdges(model, touchesBad) {
    const lit = new Set(model.edges.filter(touchesBad));
    const queue = [...lit].map((edge) => edge.source);
    const seen = new Set(queue);
    while (queue.length) {
        const id = queue.pop();
        model.edges.filter((edge) => edge.target === id).forEach((edge) => {
            lit.add(edge);
            if (!seen.has(edge.source)) {
                seen.add(edge.source);
                queue.push(edge.source);
            }
        });
    }
    return lit;
}

// What a legend pick keeps lit: the edges that match it, and their ends. A status pick keeps edges whose ends
// all have that status (a team passes), so an approved tool's link to an unapproved server fades.
function focusFilter(model, statusOf, touchesBad, focus) {
    const passThrough = new Set(model.columns.flat().filter((node) => node.category === TEAM_CATEGORY).map((node) => node.id));
    const matches = (id) => statusOf.get(id) === focus || passThrough.has(id);
    const riskyEdges = focus === RISKY_FOCUS ? riskyPathEdges(model, touchesBad) : null;
    const edgeLit = riskyEdges ? (edge) => riskyEdges.has(edge) : (edge) => matches(edge.source) && matches(edge.target);
    const nodes = new Set(model.edges.filter(edgeLit).flatMap((edge) => [edge.source, edge.target]));
    statusOf.forEach((status, id) => { if (status === focus) nodes.add(id); });
    return { nodes, edgeLit };
}

// Lays a column graph out for AssetTopologyGraph; edges touching a "bad" node turn red, "review" yellow.
// `focus` is a node status, or RISKY_FOCUS, picked from the legend; a clicked node wins over it.
export function buildColumnGraph(model, highlight, totalWidth, focus) {
    const tallest = Math.max(1, ...model.columns.map((column) => column.length));
    const statusOf = new Map(model.columns.flat().map((node) => [node.id, node.status]));
    const touchesBad = (edge) => statusOf.get(edge.source) === BAD || statusOf.get(edge.target) === BAD;
    const picked = !highlight && focus ? focusFilter(model, statusOf, touchesBad, focus) : null;
    const lit = highlight ? connectedIds(model.edges, [highlight]) : picked && picked.nodes;
    const gaps = Math.max(1, model.columns.length - 1);
    const spreadGap = totalWidth ? (totalWidth - model.columns.length * LAYOUT.nodeWidth) / gaps : LAYOUT.columnGap;
    const columnGap = Math.min(LAYOUT.maxColumnGap, Math.max(LAYOUT.columnGap, spreadGap));
    const nodes = [];
    model.columns.forEach((column, columnIndex) => {
        const x = columnIndex * (LAYOUT.nodeWidth + columnGap);
        nodes.push({ id: `head-${columnIndex}`, type: "topoHead", position: { x, y: 0 }, draggable: false, selectable: false, data: { label: model.heads[columnIndex] } });
        const offset = (tallest - column.length) / 2;
        column.forEach((node, rowIndex) => {
            nodes.push({
                id: node.id,
                type: "topoNode",
                draggable: false,
                position: { x, y: LAYOUT.headHeight + (rowIndex + offset) * LAYOUT.rowHeight },
                style: lit && !lit.has(node.id) ? { opacity: LAYOUT.dimmedOpacity } : undefined,
                data: { component: { category: node.category, type: node.sub, label: node.label, status: node.status, selected: highlight === node.id } },
            });
        });
    });
    const edges = model.edges.map((edge) => {
        const ends = [statusOf.get(edge.source), statusOf.get(edge.target)];
        const stroke = ends.includes(BAD) ? RISKY_EDGE_COLOR : ends.includes(REVIEW) ? REVIEW_EDGE_COLOR : EDGE_COLOR;
        const edgeLit = !lit || (picked ? picked.edgeLit(edge) : lit.has(edge.source) && lit.has(edge.target));
        const boost = Math.min(MAX_EDGE_WIDTH_BOOST, Math.log10((edge.weight || 1) + 1) * EDGE_WIDTH_SCALE);
        return {
            id: `e-${edge.source}-${edge.target}`,
            source: edge.source,
            target: edge.target,
            type: "default",
            style: { stroke, strokeWidth: BASE_EDGE_WIDTH + boost, opacity: edgeLit ? EDGE_OPACITY : LAYOUT.dimmedEdgeOpacity },
        };
    });
    const height = Math.max(LAYOUT.minHeight, LAYOUT.headHeight + tallest * LAYOUT.rowHeight + 16);
    return { nodes, edges, height };
}
