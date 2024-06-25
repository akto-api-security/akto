import { Badge, Box, Card, Checkbox, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"

function ItemsGroupCard({ cardObj }) {
    const selectedItemsCount = cardObj.selectedCtr || 0
    const itemsCount = cardObj[cardObj?.itemsListFieldName]?.length || 0
    const itemsResourceName = cardObj?.itemsResourceName
    
    const itemGroupNameField = cardObj?.itemGroupNameField || "name"
    const itemGroupName = cardObj[itemGroupNameField] || ""

    return (
        <div onClick={() => cardObj.onSelect()} style={{cursor: 'auto'}}>
            <Card>
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between">
                        <Text variant="headingSm">{itemGroupName}</Text>
                        <Checkbox checked={selectedItemsCount >= 1} />
                    </HorizontalStack>
                    <Box width="80%">
                        <HorizontalStack align="space-between">
                            <Badge size="small">{selectedItemsCount} of {itemsCount} {itemsCount === 1 ? itemsResourceName.singular : itemsResourceName.plural} selected</Badge>
                            {cardObj.additionalCardBadge || null}
                        </HorizontalStack>
                    </Box>
                </VerticalStack>
            </Card>
        </div>
    )
}

export default ItemsGroupCard
