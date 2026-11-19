import { Box, Text } from "@shopify/polaris"

// A keyboard-key chip. Polaris KeyboardKey uses a grey fill; the design calls for an outlined key.
export default function KeyHint({ children }) {
    return (
        <Box
            as="span"
            background="bg"
            borderWidth="1"
            borderColor="border"
            borderRadius="1"
            paddingInlineStart="1_5-experimental"
            paddingInlineEnd="1_5-experimental"
        >
            <Text as="span" variant="bodySm" color="subdued">{children}</Text>
        </Box>
    )
}
