import { Banner, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris"
import RecommendationTile from "./RecommendationTile"

// The "Worth doing now" row — a fixed 3-up grid of loading skeletons while the overlay's first
// fetch is in flight, real tiles once it resolves, or a Banner/empty state. Never a spinner — a
// spinner in a 3-up grid causes a visible reflow when it resolves, which reads as cheap on a
// minimalist page.
export default function RecommendationTiles({ tiles, loading, error, onAsk, onOpenRoute, onRetry }) {
    if (error) {
        return (
            <Banner status="critical" title="Couldn't load your dashboard" action={{ content: "Retry", onAction: onRetry }} />
        )
    }

    if (loading) {
        return (
            <HorizontalGrid columns={{ xs: 1, sm: 1, md: 3, lg: 3, xl: 3 }} gap="5">
                {[0, 1, 2].map((i) => <RecommendationTile key={i} loading />)}
            </HorizontalGrid>
        )
    }

    if (!tiles.length) {
        return (
            <VerticalStack gap="2">
                <Text variant="bodyMd" color="subdued" alignment="center">
                    Nothing needs your attention right now.
                </Text>
            </VerticalStack>
        )
    }

    return (
        <HorizontalGrid columns={{ xs: 1, sm: 1, md: 3, lg: 3, xl: 3 }} gap="5">
            {tiles.map((tile) => (
                <RecommendationTile key={tile.id} tile={tile} onAsk={onAsk} onOpenRoute={onOpenRoute} />
            ))}
        </HorizontalGrid>
    )
}
