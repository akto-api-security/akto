import { Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'

// A card with a heading (and something small on its right), or a message in place of its content when there is nothing to show.
function Panel({ title, aside, empty, emptyText, children }) {
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="2">
                    {typeof title === 'string' ? <Text variant="headingMd" as="h2">{title}</Text> : title}
                    {aside}
                </HorizontalStack>
                {empty ? <Text variant="bodyMd" color="subdued">{emptyText}</Text> : children}
            </VerticalStack>
        </Card>
    )
}

export default Panel
