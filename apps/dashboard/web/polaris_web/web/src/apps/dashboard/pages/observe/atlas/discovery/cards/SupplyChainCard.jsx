import { useMemo, useState } from "react";
import useElementWidth from "@/apps/dashboard/components/shared/useElementWidth";
import AssetTopologyGraph from "@/apps/dashboard/pages/observe/agentic/AssetTopologyGraph";
import { buildColumnGraph } from "@/apps/dashboard/pages/observe/agentic/columnGraph";
import DiscoveryCard from "../shared/DiscoveryCard";
import { SUBTITLES } from "../constants";
import { chainModel } from "../transform";
import ChainLegend from "./ChainLegend";

const TOPO_HEAD_TYPE = "topoHead";
const FIT_PADDING = 0.1;

export default function SupplyChainCard({ chain, status }) {
    const [highlight, setHighlight] = useState(null);
    const [frameRef, frameWidth] = useElementWidth();
    const model = useMemo(() => (chain ? chainModel(chain) : null), [chain]);
    const graph = useMemo(() => (model ? buildColumnGraph(model, highlight, frameWidth) : null), [model, highlight, frameWidth]);
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
            legend={<ChainLegend />}
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
