import { Box, HorizontalStack, Scrollable, Spinner, Text } from "@shopify/polaris";

export default function CardList({ items, loading, emptyText, selectedKey, getKey, onSelect, renderCard }) {
    if (loading) {
        return (
            <Box padding="6">
                <HorizontalStack align="center"><Spinner size="small" /></HorizontalStack>
            </Box>
        );
    }

    if (!items || items.length === 0) {
        return (
            <Box padding="6">
                <Text tone="subdued" variant="bodySm">{emptyText}</Text>
            </Box>
        );
    }

    return (
        <Scrollable style={{ flex: 1, minHeight: 0 }}>
            {items.map(item => {
                const key = getKey(item);
                const isSelected = key === selectedKey;
                return (
                    <Box
                        key={key}
                        padding="4"
                        borderBlockEndWidth="025"
                        borderColor="border"
                        background={isSelected ? "bg-surface-selected" : "bg-surface"}
                        onClick={() => onSelect(item)}
                        style={{ cursor: "pointer" }}
                    >
                        {renderCard(item)}
                    </Box>
                );
            })}
        </Scrollable>
    );
}
