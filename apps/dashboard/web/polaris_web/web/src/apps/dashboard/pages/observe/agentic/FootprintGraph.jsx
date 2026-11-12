import React, { useCallback, useMemo, useState } from "react";
import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import AssetTopologyGraph from "./AssetTopologyGraph";
import { LegendDot } from "./AgenticStatsCard";
import transform from "../atlas/devices_employees/transform";

/*
 * Column graph laid out like the Devices & Employees AI footprint (transform.footprintLayout): hovering a "+N more" node lists the hidden
 * items; selecting a node highlights what it's connected to, selecting the background clears it. In full screen, selecting a
 * "+N more" node the model lists in expandKeys grows that column; leaving full screen collapses it again.
 *
 * <FootprintGraph title="AI footprint" buildModel={({ fullscreen, expanded }) => ({ heads, columns, edges, expandKeys })} legend={[{ color, label }]} />
 */
function FootprintGraph({ title, buildModel, legend = [] }) {
    const [highlight, setHighlight] = useState(null);
    const [fullscreen, setFullscreen] = useState(false);
    const [expanded, setExpanded] = useState({});
    const model = useMemo(() => buildModel({ fullscreen, expanded }), [buildModel, fullscreen, expanded]);
    const graph = useMemo(() => transform.footprintLayout(model, highlight), [model, highlight]);

    const onNodeClick = useCallback((event, node) => {
        if (node.type !== "topoNode") return;
        const expandKey = model.expandKeys?.[node.id];
        if (expandKey) {
            setExpanded((current) => ({ ...current, [expandKey]: true }));
            return;
        }
        setHighlight((current) => (current === node.id ? null : node.id));
    }, [model]);
    const clearHighlight = useCallback(() => setHighlight(null), []);
    const onFullscreenChange = useCallback((on) => {
        setFullscreen(on);
        if (!on) setExpanded({});
    }, []);

    return (
        <VerticalStack gap="3">
            <Text as="h3" variant="headingSm">{title}</Text>
            <AssetTopologyGraph
                nodes={graph.nodes}
                edges={graph.edges}
                height={graph.height}
                onNodeClick={onNodeClick}
                onPaneClick={clearHighlight}
                onFullscreenChange={onFullscreenChange}
                fitKey={model}
                fitPadding={0.02}
            />
            {legend.length > 0 && (
                <HorizontalStack gap="4" blockAlign="center" wrap>
                    {legend.map((item) => (
                        <HorizontalStack key={item.label} gap="1" blockAlign="center" wrap={false}>
                            <LegendDot color={item.color} />
                            <Text as="span" variant="bodySm" color="subdued">{item.label}</Text>
                        </HorizontalStack>
                    ))}
                </HorizontalStack>
            )}
        </VerticalStack>
    );
}

export default FootprintGraph;
