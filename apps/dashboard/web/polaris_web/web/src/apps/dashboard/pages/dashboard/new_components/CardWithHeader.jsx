import { Box, Card, Text, VerticalStack } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

function CardWithHeader({
    title,
    itemId,
    tooltipContent,
    onRemove,
    hasData,
    emptyMessage,
    minHeight = "250px",
    children
}) {
    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader
                    title={title}
                    itemId={itemId}
                    onRemove={onRemove}
                    tooltipContent={tooltipContent}
                />
                {hasData ? (
                    children
                ) : (
                    <Box minHeight={minHeight} display="flex" alignItems="center" justifyContent="center">
                        <Text alignment='center' color='subdued'>{emptyMessage}</Text>
                    </Box>
                )}
            </VerticalStack>
        </Card>
    )
}

export default CardWithHeader
