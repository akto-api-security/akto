import { Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";

/*
 * Header of a detail side panel: icon, title, a badge + subtitle line, an actions row and close.
 * Pair with AgenticFlyoutShell's `header` slot.
 *
 * <DetailPanelHeader
 *     icon={<AssetIcon ... />}
 *     title="Claude CLI"
 *     badge={<ApprovalStatusBadge status="Approved" />}
 *     subtitle={["AI agent", "Anthropic"]}
 *     actions={<HorizontalStack gap="2">...</HorizontalStack>}
 *     onClose={close}
 * />
 */
function DetailPanelHeader({ icon, title, badge, subtitle = [], actions, onClose }) {
    const subtitleText = subtitle.filter(Boolean).join(" · ");
    return (
        <>
            <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="4">
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            {icon}
                            <VerticalStack gap="1">
                                <Text as="h2" variant="headingLg">{title}</Text>
                                {(badge || subtitleText) && (
                                    <HorizontalStack gap="2" blockAlign="center">
                                        {badge}
                                        {subtitleText && <Text variant="bodySm" color="subdued">{subtitleText}</Text>}
                                    </HorizontalStack>
                                )}
                            </VerticalStack>
                        </HorizontalStack>
                        {onClose && <Button plain icon={MobileCancelMajor} onClick={onClose} accessibilityLabel="Close" />}
                    </HorizontalStack>
                    {actions}
                </VerticalStack>
            </Box>
            <Divider />
        </>
    );
}

export default DetailPanelHeader;
