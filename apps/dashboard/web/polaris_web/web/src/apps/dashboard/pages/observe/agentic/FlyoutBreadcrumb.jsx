import React from "react";
import { Link } from "@shopify/polaris";

/**
 * Shared breadcrumb bar for all flyout panels.
 *
 * Props:
 *   items   – Array of { label, badge?, onClick? }
 *             Items with onClick render as a Link; the last item is always plain text.
 *   onClose – Called when the × button is pressed.
 *
 * Usage:
 *   <FlyoutBreadcrumb
 *     items={[
 *       { label: device.endpoint, badge: device.riskScore, onClick: () => onDeviceClick(device) },
 *       { label: "Skills",                                  onClick: onBack },
 *       { label: skill.name },   // last item — no onClick needed
 *     ]}
 *     onClose={onClose}
 *   />
 */

function RiskPill({ score }) {
    if (score == null) return null;
    let bg = "#F0FDF4", color = "#16A34A";
    if (score >= 4.5) { bg = "#FEE2E2"; color = "#DC2626"; }
    else if (score >= 4.0) { bg = "#FFEDD5"; color = "#EA580C"; }
    else if (score >= 3.5) { bg = "#FEF9C3"; color = "#CA8A04"; }
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            padding: "2px 8px", borderRadius: 10,
            fontSize: 12, fontWeight: 600, background: bg, color,
        }}>
            {score.toFixed(1)}
        </span>
    );
}

export default function FlyoutBreadcrumb({ items = [], onClose, children }) {
    return (
        <div style={{
            display: "flex", alignItems: "center", justifyContent: "space-between",
            padding: "12px 16px",
            borderBottom: "1px solid #E1E3E5",
            flexShrink: 0,
        }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap", fontSize: 13, flex: 1, minWidth: 0 }}>
                {items.map((item, i) => {
                    const isLast = i === items.length - 1 && !children;
                    return (
                        <React.Fragment key={i}>
                            {i > 0 && <span style={{ color: "#8C9196" }}>/</span>}
                            {item.onClick ? (
                                <Link url="#" onClick={e => { e.preventDefault(); item.onClick(); }}>
                                    {item.label}
                                </Link>
                            ) : (
                                <span style={{ fontWeight: isLast ? 600 : 400, color: "#202223", whiteSpace: "nowrap" }}>
                                    {item.label}
                                </span>
                            )}
                            {item.badge != null && <RiskPill score={item.badge} />}
                        </React.Fragment>
                    );
                })}
                {/* Slot for extra content after breadcrumb items (e.g. skill picker popover) */}
                {children}
            </div>
            {onClose && (
                <button
                    onClick={onClose}
                    style={{
                        background: "none", border: "none", cursor: "pointer",
                        color: "#6D7175", fontSize: 18, lineHeight: 1,
                        padding: "2px 4px", display: "flex", alignItems: "center", flexShrink: 0,
                    }}
                >×</button>
            )}
        </div>
    );
}
