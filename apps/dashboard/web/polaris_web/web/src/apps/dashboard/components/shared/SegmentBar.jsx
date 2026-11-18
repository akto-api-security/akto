import React from "react";
import { Box, Text } from "@shopify/polaris";
import "../layouts/style.css";

const DEFAULT_LABEL_THRESHOLD = 10;

/*
 * Segmented proportion bar, extracted from AgenticStatsCard. With only `segments` it renders
 * exactly as before. Optional props:
 *   height          CSS height of the bar (default is the .agentic-seg-bar height)
 *   showLabels      show each segment's percentage inside it; hidden below `labelThreshold` percent
 *   onSegmentClick  makes segments clickable, called with the segment's key (or label)
 *   activeKey       dims every other segment
 * A segment may also carry `key`, `tooltip` and `lightText` (white label on a dark segment).
 */
export default function SegmentBar({ segments, height, showLabels, labelThreshold = DEFAULT_LABEL_THRESHOLD, onSegmentClick, activeKey }) {
    const total = showLabels ? segments.reduce((sum, s) => sum + s.count, 0) : 0;
    return (
        <Box className="agentic-seg-bar" style={height ? { height } : undefined}>
            {segments.map((s) => {
                if (!(s.count > 0)) return null;
                const key = s.key ?? s.label;
                const style = { flexGrow: s.count, background: s.color };
                if (showLabels) Object.assign(style, { display: "flex", alignItems: "center", justifyContent: "center" });
                if (onSegmentClick) style.cursor = "pointer";
                if (activeKey && activeKey !== key) style.opacity = 0.45;
                const percent = showLabels ? Math.round((100 * s.count) / total) : 0;
                return (
                    <Box
                        key={s.label}
                        className="agentic-seg"
                        title={s.tooltip || `${s.label}: ${s.count}`}
                        onClick={onSegmentClick ? () => onSegmentClick(key) : undefined}
                        style={style}
                    >
                        {showLabels && percent >= labelThreshold && (
                            <Text as="span" variant="bodySm" fontWeight="semibold" color={s.lightText ? "text-inverse" : undefined}>{`${percent}%`}</Text>
                        )}
                    </Box>
                );
            })}
        </Box>
    );
}
