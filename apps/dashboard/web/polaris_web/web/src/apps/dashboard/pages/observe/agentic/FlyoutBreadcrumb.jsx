import React from "react";
import { Box, HorizontalStack, Text, Link, Button, Divider } from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";
import { RiskPill } from "./AgenticCellRenderers";

// ─── FlyoutBreadcrumb ─────────────────────────────────────────────────────────
// Shared breadcrumb header used across DeviceFlyout and AgenticAssetFlyout.
//
// Props:
//   items   — [{ label, badge?, onClick? }]
//             Items with onClick render as a Link; the last item is plain text.
//   onClose — Called when the × button is pressed.
//   children — Slot for extra content after items (e.g. a skill-picker Popover).

export default function FlyoutBreadcrumb({ items = [], onClose, children }) {
    return (
        <>
            <Box
                paddingInlineStart="4"
                paddingInlineEnd="4"
                paddingBlockStart="3"
                paddingBlockEnd="3"
            >
                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="2" blockAlign="center" wrap={true}>
                        {items.map((item, i) => {
                            const isLast = i === items.length - 1 && !children;
                            return (
                                <React.Fragment key={i}>
                                    {i > 0 && <Text variant="bodySm" color="subdued">/</Text>}
                                    {item.onClick ? (
                                        <Link url="#" onClick={e => { e.preventDefault(); item.onClick(); }}>
                                            {item.label}
                                        </Link>
                                    ) : (
                                        <Text variant="bodySm" fontWeight={isLast ? "semibold" : "regular"}>
                                            {item.label}
                                        </Text>
                                    )}
                                    {item.badge != null && <RiskPill score={item.badge} />}
                                </React.Fragment>
                            );
                        })}
                        {children}
                    </HorizontalStack>
                    {onClose && (
                        <Button plain icon={MobileCancelMajor} onClick={onClose} accessibilityLabel="Close" />
                    )}
                </HorizontalStack>
            </Box>
            <Divider />
        </>
    );
}
