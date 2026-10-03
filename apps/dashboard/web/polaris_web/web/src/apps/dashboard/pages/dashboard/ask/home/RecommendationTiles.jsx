import { Banner, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import { tileSeverityLabel, tileTint } from "../transform"

// "Worth doing now" — design_handoff_ask_akto_overlay/README.md, "Home view (empty input)". Each
// tile is a live number plus the prompt it fires; clicking it seeds the chat with that prompt
// (onAsk), which is what makes the overlay prompt-first rather than a dashboard with a search box
// bolted on. Severity tints (.ask-tile-*) are a hand-kept mirror of the design's own hex values —
// see askOverlay.css's header comment for why they can't be Polaris tokens.
function Tile({ tile, onAsk }) {
    const tint = tileTint(tile.severity)
    return (
        <button type="button" className={`ask-tile ask-tile-${tint}`} onClick={() => onAsk(tile.prompt)}>
            <HorizontalStack gap="1" blockAlign="center">
                <span className="ask-tile-dot" />
                <span className="ask-tile-sev">{tileSeverityLabel(tile.severity)}</span>
            </HorizontalStack>
            <span className="ask-tile-count">{tile.value}</span>
            <span className="ask-tile-label">{tile.label}</span>
        </button>
    )
}

function TileSkeleton() {
    return (
        <div className="ask-tile-skeleton-wrap">
            <VerticalStack gap="2">
                <div className="ask-tile-skeleton ask-tile-skeleton-label" />
                <div className="ask-tile-skeleton ask-tile-skeleton-count" />
            </VerticalStack>
        </div>
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
                {!loading ? <Text variant="bodySm" color="subdued">Live · updated just now</Text> : null}
            </HorizontalStack>

            {loading ? (
                <div className="ask-tiles-grid">
                    {[0, 1, 2, 3].map((i) => <TileSkeleton key={i} />)}
                </div>
            ) : tiles.length ? (
                <div className="ask-tiles-grid">
                    {tiles.map((tile) => <Tile key={tile.id} tile={tile} onAsk={onAsk} />)}
                </div>
            ) : (
                <Text variant="bodyMd" color="subdued">Nothing needs your attention right now.</Text>
            )}
        </VerticalStack>
    )
}
