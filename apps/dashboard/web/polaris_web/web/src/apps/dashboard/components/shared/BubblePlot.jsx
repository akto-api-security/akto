import { useMemo } from "react";
import { Box, HorizontalStack, Text } from "@shopify/polaris";
import { LABEL_GAP_PX, placeLabels } from "./bubbleLabels";
import useElementWidth from "./useElementWidth";
import "./BubblePlot.css";

const GRID_COLOR = "#F1F2F3";
const TIP_CHAR_PX = 7;
const TIP_PAD_PX = 16;

// Horizontal grid lines between bands, drawn as one background so they follow the plot height.
const gridLines = (bands) => Array.from({ length: bands - 1 }, (_, i) => {
    const at = `${((i + 1) / bands) * 100}%`;
    return `linear-gradient(${GRID_COLOR},${GRID_COLOR}) 0 ${at}/100% 1px no-repeat`;
}).join(",");

// Centre the hover text on its circle unless that would cross a plot edge; then it hangs from that edge's side.
function tipClass(bubble, plotWidth) {
    const centerX = Math.min(Math.max((bubble.x / 100) * plotWidth, bubble.radius), plotWidth - bubble.radius);
    const half = (bubble.tip.length * TIP_CHAR_PX + TIP_PAD_PX) / 2;
    if (centerX - half < 0) return " bubble-plot__circle--tip-start";
    return centerX + half > plotWidth ? " bubble-plot__circle--tip-end" : "";
}

const onActivate = (action) => (event) => {
    if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        action();
    }
};

// Circles on a positioned grid; bubbles: [{ key, x, y, radius, fill, stroke, tip, label, priority, onClick }]
export default function BubblePlot({ bubbles, height = 300, bands = 4, bandLabels, tickLabels, zone }) {
    const [plotRef, width] = useElementWidth();
    // Names are placed once the plot's width is known, so none covers another name or circle.
    const labelSides = useMemo(() => placeLabels(bubbles, width, height), [bubbles, width, height]);
    return (
        <HorizontalStack gap="2" wrap={false} blockAlign="start">
            {bandLabels && (
                <div className="bubble-plot__bands" style={{ "--plot-height": `${height}px` }}>
                    {bandLabels.map((label) => <Text key={label} as="span" variant="bodySm" color="subdued">{label}</Text>)}
                </div>
            )}
            <Box width="100%">
                <div ref={plotRef} className="bubble-plot" style={{ "--plot-height": `${height}px`, "--plot-grid": gridLines(bands) }}>
                    {zone && (
                        <>
                            <div className="bubble-plot__zone" style={{ "--zone-width": `${zone.widthPct}%`, "--zone-height": `${zone.heightPct}%` }} />
                            <span className="bubble-plot__zone-label"><Text as="span" variant="bodySm" fontWeight="semibold" color="critical">{zone.label}</Text></span>
                        </>
                    )}
                    {bubbles.map((bubble) => (
                        <div
                            key={bubble.key}
                            className={`bubble-plot__circle${tipClass(bubble, width)}`}
                            role="button"
                            tabIndex={0}
                            data-tip={bubble.tip}
                            aria-label={bubble.tip}
                            onClick={bubble.onClick}
                            onKeyDown={onActivate(bubble.onClick)}
                            style={{ "--x": `${bubble.x}%`, "--y": `${bubble.y}%`, "--d": `${bubble.radius * 2}px`, "--fill": bubble.fill, "--stroke": bubble.stroke }}
                        />
                    ))}
                    {bubbles.filter((bubble) => labelSides[bubble.key]).map((bubble) => (
                        <span
                            key={`label-${bubble.key}`}
                            className={`bubble-plot__label${labelSides[bubble.key] === "left" ? " bubble-plot__label--flip" : ""}`}
                            onClick={bubble.onClick}
                            style={{ "--x": `${bubble.x}%`, "--y": `${bubble.y}%`, "--offset": `${bubble.radius + LABEL_GAP_PX}px` }}
                        >
                            <Text as="span" variant="bodySm" fontWeight="medium">{bubble.label}</Text>
                        </span>
                    ))}
                </div>
                {tickLabels && (
                    <Box paddingBlockStart="1">
                        <HorizontalStack align="space-between">
                            {tickLabels.map((label) => <Text key={label} as="span" variant="bodySm" color="subdued">{label}</Text>)}
                        </HorizontalStack>
                    </Box>
                )}
            </Box>
        </HorizontalStack>
    );
}
