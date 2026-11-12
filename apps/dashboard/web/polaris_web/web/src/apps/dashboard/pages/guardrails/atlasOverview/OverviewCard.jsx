import { Box, Button, HorizontalStack } from "@shopify/polaris";

export function OverviewListRow({ onClick, children }) {
    const row = (
        <Box paddingBlockStart="2" paddingBlockEnd="2" borderBlockStartWidth="1" borderColor="border-subdued">
            {children}
        </Box>
    );
    return onClick ? <div className="insight-row" onClick={onClick}>{row}</div> : row;
}

export function OverviewShowMore({ total, shown, pageSize, onChange }) {
    if (total <= pageSize) return null;
    return (
        <Box paddingBlockStart="2">
            <HorizontalStack gap="4">
                {shown < total && <Button plain onClick={() => onChange(shown + pageSize)}>{`Show more (${total - shown} more)`}</Button>}
                {shown > pageSize && <Button plain onClick={() => onChange(pageSize)}>Show less</Button>}
            </HorizontalStack>
        </Box>
    );
}
