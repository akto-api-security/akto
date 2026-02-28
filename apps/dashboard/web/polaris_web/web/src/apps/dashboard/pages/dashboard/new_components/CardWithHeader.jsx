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
    useFlexContent = false,
    children
}) {
    return (
        <Card>
            <VerticalStack
                gap={4}
                {...(useFlexContent && { style: { height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 } })}
            >
                <ComponentHeader
                    title={title}
                    itemId={itemId}
                    onRemove={onRemove}
                    tooltipContent={tooltipContent}
                />
                {hasData ? (
                    useFlexContent ? (
                        <Box minHeight={0} style={{ flex: 1 }}>
                            {children}
                        </Box>
                    ) : (
                        children
                    )
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
