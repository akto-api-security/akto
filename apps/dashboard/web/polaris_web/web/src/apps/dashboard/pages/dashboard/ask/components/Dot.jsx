import { Box } from "@shopify/polaris"

export default function Dot({ background, size = "8px" }) {
    return <Box background={background} borderRadius="full" width={size} minWidth={size} minHeight={size} />
}
