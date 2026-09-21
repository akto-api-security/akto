import { Box, HorizontalStack, Text, VerticalStack, Button, SkeletonBodyText, SkeletonDisplayText } from "@shopify/polaris"
import { SeverityBadge } from "@/apps/dashboard/pages/observe/agentic/AgenticCellRenderers"
import TrendSpark from "./TrendSpark"

// One "worth doing now" card — a live number plus the prompt it fires. Clicking the card body
// seeds the palette's chat with that prompt (this is what makes the overlay prompt-first rather
// than a dashboard with a search box bolted on); the secondary "Open" link navigates instead,
// for people who'd rather look than ask. It's a sibling of the <button>, not nested inside it —
// browsers handle a <button> inside a <button> inconsistently.
export default function RecommendationTile({ tile, onAsk, onOpenRoute, loading }) {
    if (loading) {
        return (
            <Box background="bg-surface" borderRadius="3" shadow="card" padding="4" minHeight="132px">
                <VerticalStack gap="3">
                    <SkeletonDisplayText size="medium" />
                    <SkeletonBodyText lines={2} />
                </VerticalStack>
            </Box>
        )
    }

    return (
        <Box background="bg-surface" borderRadius="3" shadow="card" overflowX="hidden">
            <HorizontalStack wrap={false} gap="0">
                <Box
                    className={`insight-severity-bar insight-severity-bar-${tile.severity || "DEFAULT"}`}
                    minHeight="100%"
                />
                <Box width="100%">
                    <Box
                        as="button"
                        onClick={() => onAsk(tile.prompt)}
                        paddingBlockStart="4"
                        paddingBlockEnd={tile.route ? "2" : "4"}
                        paddingInlineStart="4"
                        paddingInlineEnd="4"
                        width="100%"
                        background="bg-surface"
                    >
                        <VerticalStack gap="2">
                            <HorizontalStack align="space-between" blockAlign="start">
                                <Text variant="bodySm" color="subdued">{tile.label}</Text>
                                {tile.severity ? <SeverityBadge severity={tile.severity} /> : null}
                            </HorizontalStack>
                            <Text variant="heading2xl" as="p" alignment="start">{tile.value}</Text>
                            <TrendSpark trend={tile.trend} severity={tile.severity} />
                            <Text variant="bodySm" fontWeight="medium" color="interactive">Ask →</Text>
                        </VerticalStack>
                    </Box>
                    {tile.route ? (
                        <Box paddingBlockEnd="3" paddingInlineStart="4" paddingInlineEnd="4">
                            <Button plain removeUnderline onClick={() => onOpenRoute(tile.route, tile.params)}>
                                Open →
                            </Button>
                        </Box>
                    ) : null}
                </Box>
            </HorizontalStack>
        </Box>
    )
}
