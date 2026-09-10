import { Box, Checkbox, HorizontalStack } from "@shopify/polaris";

// Pill-shaped checkbox (no native Polaris chip-with-checkbox).
function CheckboxChip({ label, checked, onChange, trailing }) {
    return (
        <Box
            background={checked ? "bg-surface-secondary" : "bg-surface"}
            borderColor={checked ? "border" : "border-subdued"}
            borderWidth="1"
            borderRadius="full"
            paddingBlock="1"
            paddingInlineStart="3"
            paddingInlineEnd="3"
        >
            <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                <Checkbox label={label} checked={checked} onChange={onChange} />
                {trailing}
            </HorizontalStack>
        </Box>
    );
}

export default CheckboxChip;
