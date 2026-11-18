import { Box, Button, HorizontalStack, Text } from "@shopify/polaris";

/*
 * One-line highlighted message with an optional link on the right, e.g. a violations count above
 * the details of an asset.
 *
 * <CalloutStrip tone="critical" action={{ content: "View violations", onAction: open }}>
 *     <Text as="span" fontWeight="semibold">214 guardrail violations</Text> in the selected period
 * </CalloutStrip>
 */
const TONES = {
    critical: { background: "bg-critical-subdued", color: "critical" },
    warning: { background: "bg-caution-subdued", color: "warning" },
    info: { background: "bg-info-subdued", color: undefined },
};

function CalloutStrip({ tone = "critical", action, children }) {
    const { background, color } = TONES[tone] || TONES.info;
    return (
        <Box background={background} borderRadius="2" paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="2" paddingBlockEnd="2">
            <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                <Text as="span" variant="bodyMd" color={color}>{children}</Text>
                {action && <Button plain onClick={action.onAction}>{action.content}</Button>}
            </HorizontalStack>
        </Box>
    );
}

export default CalloutStrip;
