import { Banner, Box, HorizontalGrid, HorizontalStack, SkeletonBodyText, SkeletonDisplayText, Text, VerticalStack } from "@shopify/polaris"
import Pressable from "../components/Pressable"
import Dot from "../components/Dot"
import { tileTone } from "../transform"

const TILE_COLUMNS = "repeat(auto-fit, minmax(160px, 1fr))"
const SKELETON_TILES = [0, 1, 2, 3]

// "Worth doing now" — design_handoff_ask_akto_overlay/README.md, "Home view (empty input)". Each
// tile is a live number plus the prompt it fires; clicking it seeds the chat with that prompt.
function Tile({ tile, onAsk }) {
    const tone = tileTone(tile.severity)
    return (
        <Pressable
            onClick={() => onAsk(tile.prompt)}
            background={tone.background}
            borderWidth="1"
            borderColor="transparent"
            hoverBorderColor={tone.border}
            borderRadius="3"
            padding="3"
        >
            <VerticalStack gap="1">
                <HorizontalStack gap="1" blockAlign="center">
                    <Dot background={tone.dot} />
                    <Box as="span" color={tone.text}>
                        <Text as="span" variant="bodySm" fontWeight="medium">{tone.label}</Text>
                    </Box>
                </HorizontalStack>
                <Box color={tone.text}>
                    <Text as="p" variant="headingLg">{tile.value}</Text>
                </Box>
                <Text as="p" variant="bodySm">{tile.label}</Text>
            </VerticalStack>
        </Pressable>
    )
}

function TileSkeleton() {
    return (
        <Box borderWidth="1" borderColor="border-ask-divider" borderRadius="3" padding="3" minHeight="76px">
            <VerticalStack gap="2">
                <SkeletonBodyText lines={1} />
                <SkeletonDisplayText size="small" />
            </VerticalStack>
        </Box>
    )
}

export default function RecommendationTiles({ tiles, loading, error, onAsk, onRetry }) {
    if (error) {
        return <Banner status="critical" title="Couldn't load your dashboard" action={{ content: "Retry", onAction: onRetry }} />
    }

    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="baseline">
                <Text variant="headingSm" as="h3">Worth doing now</Text>
                {!loading ? <Text as="span" variant="bodySm" color="subdued">Live · updated just now</Text> : null}
            </HorizontalStack>

            {loading ? (
                <HorizontalGrid columns={TILE_COLUMNS} gap="3">
                    {SKELETON_TILES.map((i) => <TileSkeleton key={i} />)}
                </HorizontalGrid>
            ) : tiles.length ? (
                <HorizontalGrid columns={TILE_COLUMNS} gap="3">
                    {tiles.map((tile) => <Tile key={tile.id} tile={tile} onAsk={onAsk} />)}
                </HorizontalGrid>
            ) : (
                <Text as="p" variant="bodyMd" color="subdued">Nothing needs your attention right now.</Text>
            )}
        </VerticalStack>
    )
}
