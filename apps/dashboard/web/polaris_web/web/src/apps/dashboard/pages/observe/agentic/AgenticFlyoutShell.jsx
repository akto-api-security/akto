import React, { useEffect } from "react";
import { Box } from "@shopify/polaris";
import "../../../components/layouts/style.css";

// ─── AgenticFlyoutShell ───────────────────────────────────────────────────────
// Single slide-in panel shell shared by DeviceFlyout and AgenticAssetFlyout.
// Replaces the per-flyout hand-rolled `.flyLayout`/`.flyOuterLayout` + inline-style
// shells. `.flyLayout`/`.show` (in style.css) drive the slide-in transition — a pure
// CSS animation that cannot be expressed via Polaris props, so it stays a class.
//
// Props:
//   show     — controls the slide-in + footer overflow reset
//   width    — panel width in px (default 800)
//   header   — breadcrumb / title region (rendered at the top)
//   footer   — pinned region at the bottom (e.g. AiChatSection)
//   children — scrollable/flex body between header and footer
export default function AgenticFlyoutShell({ show, width = 800, header, footer, children }) {
    // Mirror the previous flyouts: clear any body scroll-lock once hidden.
    useEffect(() => {
        if (!show) document.body.style.overflow = "";
    }, [show]);

    return (
        <Box className={"flyLayout " + (show ? "show" : "")} style={{ width }}>
            <Box
                style={{
                    position: "fixed", right: 0, top: "3.5rem", zIndex: 1000,
                    width,
                    height: "calc(100vh - 3.5rem)",
                    display: "flex", flexDirection: "column",
                    background: "white",
                    borderLeft: "1px solid #E1E3E5",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            >
                {header}
                <div style={{ flex: 1, minHeight: 0, overflow: "hidden", display: "flex", flexDirection: "column" }}>
                    {children}
                </div>
                {footer}
            </Box>
        </Box>
    );
}
