import React from "react";

// Waterfall bar: track + coloured fill. Uses plain <span> because Polaris Box's
// `style` prop replaces its entire computed style, making position:absolute unusable.
export default function SpanBar({ offset = 0, width = 0, color = "#3BAFA4" }) {
    return (
        <span style={{ position: "relative", display: "block", width: "100%", height: 12, borderRadius: 3, background: "var(--p-color-bg-subdued)", overflow: "hidden" }}>
            <span style={{ position: "absolute", top: 2, bottom: 2, left: `${(offset * 100).toFixed(2)}%`, width: `${Math.max(2, width * 100).toFixed(2)}%`, borderRadius: 2, background: color }} />
        </span>
    );
}
