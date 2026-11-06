import { useMemo, useState } from "react";
import useElementSize from "@/apps/dashboard/components/shared/useElementSize";
import AssetTopologyGraph from "@/apps/dashboard/pages/observe/agentic/AssetTopologyGraph";
import { buildColumnGraph, RISKY_FOCUS } from "@/apps/dashboard/pages/observe/agentic/columnGraph";
import DiscoveryCard from "../shared/DiscoveryCard";
import { RISKY_LEGEND_KEY, SUBTITLES } from "../constants";
import { chainModel, TOPO_STATUS } from "../transform";
import ColorLegend from "./ColorLegend";

const TOPO_HEAD_TYPE = "topoHead";
const FIT_PADDING = 0.1;

export default function SupplyChainCard({ chain, status }) {
    const [highlight, setHighlight] = useState(null);
    // The legend entry picked, if any.
    const [legendPick, setLegendPick] = useState(null);
    const [frameRef, { width: frameWidth }] = useElementSize();
    const model = useMemo(() => (chain ? chainModel(chain) : null), [chain]);
    const focus = legendPick === RISKY_LEGEND_KEY ? RISKY_FOCUS : TOPO_STATUS[legendPick];
    const graph = useMemo(() => (model ? buildColumnGraph(model, highlight, frameWidth, focus) : null), [model, highlight, frameWidth, focus]);
    // Refit when the data or the width changes, but not when a node is highlighted.
    const fitKey = useMemo(() => ({ model, frameWidth }), [model, frameWidth]);
    const empty = !model || model.columns.every((column) => column.length === 0);

    const onNodeClick = (event, node) => {
        if (node.type === TOPO_HEAD_TYPE) return;
        setHighlight((current) => (current === node.id ? null : node.id));
    };

    return (
        <DiscoveryCard
            title="AI supply chain"
            subtitle={SUBTITLES.chain}
            legend={<ColorLegend selected={legendPick} onSelect={setLegendPick} />}
            status={status}
            empty={empty}
            emptyText="No tool connections discovered yet."
        >
            <div ref={frameRef}>
                {graph && (
                    <AssetTopologyGraph
                        nodes={graph.nodes}
                        edges={graph.edges}
                        height={graph.height}
                        onNodeClick={onNodeClick}
                        onPaneClick={() => setHighlight(null)}
                        fitKey={fitKey}
                        fitPadding={FIT_PADDING}
                    />
                )}
            </div>
        </DiscoveryCard>
    );
}
