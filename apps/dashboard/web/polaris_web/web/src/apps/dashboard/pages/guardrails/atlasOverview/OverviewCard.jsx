import { Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";

export function OverviewCardHeader({ title, subtitle, action }) {
    return (
        <HorizontalStack align="space-between" blockAlign="start" gap="2" wrap={false}>
            <VerticalStack gap="0">
                <Text variant="headingMd" as="h2">{title}</Text>
                {subtitle && <Text variant="bodyMd" color="subdued">{subtitle}</Text>}
            </VerticalStack>
            {action}
        </HorizontalStack>
    );
}

export function OverviewListRow({ onClick, children }) {
    const row = (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="2" paddingBlockEnd="2" borderBlockStartWidth="1" borderColor="border-subdued">
            {children}
        </Box>
    );
    return onClick ? <div className="insight-row" onClick={onClick}>{row}</div> : row;
}

export function OverviewShowMore({ total, shown, pageSize, onChange }) {
    if (total <= pageSize) return null;
    return (
        <Box padding="4" paddingBlockStart="2">
            <HorizontalStack gap="4">
                {shown < total && <Button plain onClick={() => onChange(shown + pageSize)}>{`Show more (${total - shown} more)`}</Button>}
                {shown > pageSize && <Button plain onClick={() => onChange(pageSize)}>Show less</Button>}
            </HorizontalStack>
        </Box>
    );
}
