import { useEffect, useState } from "react";
import agenticObserveApi from "./agenticObserveApi";
import { buildMcpComponentsFromStis } from "./agenticPageBuilders";

// Shared by both context graphs — the device flyout's (DeviceFlyout.jsx) and the asset flyout's
// (AssetTopologyGraph.jsx). They lay their nodes out differently but hang tools off an MCP the
// same way, and keeping two copies of that had already let them drift apart.

export const TOOL_CAP = 4;          // tools shown per MCP before collapsing to "+N more"
export const TOOL_EDGE_COLOR = "#D97706";

// One row per component, plus a reserved row per tool hanging off an MCP. Both layouts place a
// row at a fixed pitch, so a tool without its own row lands on top of the next component.
export function withToolRows(items, mcpTools) {
    const rows = [];
    items.forEach((item) => {
        rows.push({ item });
        const tools = item.cat === "mcp" && item.collectionId ? (mcpTools[item.collectionId] || []) : [];
        const shown = tools.slice(0, TOOL_CAP);
        const labels = tools.length > shown.length ? [...shown, `+${tools.length - shown.length} more`] : shown;
        labels.forEach((label, i) => rows.push({ item, tool: { id: `${item.id}-tool-${i}`, label } }));
    });
    return rows;
}

// Tool names per collection id, keyed for withToolRows. Batched — 3 requests for the whole set of
// ids, not 3 per id. Callers derive the ids themselves (they hold different shapes) and must
// memoize them, or this refetches on every render.
export function useMcpTools(collectionIds) {
    const [mcpTools, setMcpTools] = useState({});

    useEffect(() => {
        if (!collectionIds.length) { setMcpTools({}); return; }
        let cancelled = false;
        agenticObserveApi.fetchCollectionStiBundlesBatch(collectionIds)
            .then(bundles => {
                if (cancelled) return;
                const next = {};
                bundles.forEach((b, id) => {
                    const { tools } = buildMcpComponentsFromStis(b.stiEndpoints, b.apiInfoList, b.id, b.auditRows);
                    next[id] = (tools || []).map(t => t.name).filter(Boolean);
                });
                setMcpTools(next);
            })
            .catch(() => { if (!cancelled) setMcpTools({}); });
        return () => { cancelled = true; };
    }, [collectionIds]);

    return mcpTools;
}
