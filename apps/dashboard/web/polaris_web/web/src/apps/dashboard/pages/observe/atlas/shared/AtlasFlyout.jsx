import { useEffect } from "react";
import { Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import AgenticFlyoutShell from "@/apps/dashboard/pages/observe/agentic/AgenticFlyoutShell";
import DetailGrid from "@/apps/dashboard/pages/observe/agentic/DetailGrid";
import ActionChip from "./ActionChip";
import "./AtlasFlyout.css";

const FLYOUT_WIDTH = 620;
const DETAIL_LABEL_WIDTH = "160px";
const DETAIL_VALUE_MAX_WIDTH = "400px";

// The primary action stays a Polaris button; the rest are outlined chips.
function ActionButton({ action }) {
    if (action.primary) return <Button primary disabled={action.disabled} onClick={action.onClick}>{action.label}</Button>;
    return <ActionChip label={action.label} disabled={action.disabled} onClick={action.onClick} />;
}

function FlyoutActions({ actions }) {
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {actions.map((action) => <ActionButton key={action.label} action={action} />)}
        </HorizontalStack>
    );
}

// A titled list inside a flyout; rows: [{ key, icon, label, sub, trailing }].
export function FlyoutRowList({ title, rows }) {
    if (!rows?.length) return null;
    return (
        <VerticalStack gap="2">
            <Text as="h3" variant="headingSm">{title}</Text>
            <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" overflowX="hidden" overflowY="hidden">
                {rows.map((row, index) => (
                    <Box key={row.key} padding="2" paddingInlineStart="3" paddingInlineEnd="3" borderColor="border-subdued" {...(index > 0 ? { borderBlockStartWidth: "1" } : {})}>
                        <HorizontalStack align="space-between" blockAlign="center" gap="3" wrap={false}>
                            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                {row.icon}
                                <VerticalStack gap="0">
                                    <Text as="span">{row.label}</Text>
                                    {row.sub && <Text as="span" variant="bodySm" color="subdued">{row.sub}</Text>}
                                </VerticalStack>
                            </HorizontalStack>
                            {row.trailing}
                        </HorizontalStack>
                    </Box>
                ))}
            </Box>
        </VerticalStack>
    );
}

// Slide-over for one tool, device or employee; closes on Escape or a click on the backdrop.
export default function AtlasFlyout({ show, onClose, title, icon, subtitle, headerExtra, status, errorText, actions, askBar, stats, details, trailing, children }) {
    useEffect(() => {
        if (!show) return undefined;
        const closeOnEscape = (event) => { if (event.key === "Escape") onClose(); };
        window.addEventListener("keydown", closeOnEscape);
        return () => window.removeEventListener("keydown", closeOnEscape);
    }, [show, onClose]);

    const header = (
        <>
            <Box padding="4">
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            {icon}
                            <VerticalStack gap="1">
                                <Text as="h2" variant="headingLg">{title}</Text>
                                <HorizontalStack gap="2" blockAlign="center">
                                    {headerExtra}
                                    {subtitle && <Text as="span" color="subdued">{subtitle}</Text>}
                                </HorizontalStack>
                            </VerticalStack>
                        </HorizontalStack>
                        <Button plain icon={MobileCancelMajor} onClick={onClose} accessibilityLabel="Close" />
                    </HorizontalStack>
                    {status === "ready" && actions && <FlyoutActions actions={actions} />}
                </VerticalStack>
            </Box>
            <Divider />
        </>
    );

    return (
        <>
            {show && <div className="atlas-flyout-backdrop" onClick={onClose} aria-hidden="true" />}
            <AgenticFlyoutShell show={show} width={FLYOUT_WIDTH} header={header}>
                <Box style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                    <Box padding="4">
                        {status === "loading" && <SpinnerCentered />}
                        {status === "error" && <Text as="p" color="subdued">{errorText}</Text>}
                        {status === "ready" && (
                            <VerticalStack gap="5">
                                {askBar}
                                {stats && <SummaryCardInfo summaryItems={stats} />}
                                {children}
                                {details && <DetailGrid items={details} labelWidth={DETAIL_LABEL_WIDTH} valueMaxWidth={DETAIL_VALUE_MAX_WIDTH} />}
                                {trailing}
                            </VerticalStack>
                        )}
                    </Box>
                </Box>
            </AgenticFlyoutShell>
        </>
    );
}
