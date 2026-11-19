import { Box, Icon } from "@shopify/polaris"

// An icon on a tinted square or circle. padding "1" gives a 28px tile, "1_5-experimental" 32px.
export default function IconTile({ source, background, borderRadius = "2", padding = "1" }) {
    return (
        <Box background={background} borderRadius={borderRadius} padding={padding}>
            <Icon source={source} />
        </Box>
    )
}
