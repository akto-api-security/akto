import { Avatar, Box, Card, DataTable, HorizontalStack, ProgressBar, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import ComponentHeader from '../new_components/ComponentHeader'

function ServersLayout({ title, itemId, tooltipContent, items, hasItems, emptyMessage, onRemove, boxHeight = "200px" }) {
    if (!hasItems) {
        return (
            <Card>
                <VerticalStack gap={4}>
                    <ComponentHeader
                        title={title}
                        itemId={itemId}
                        onRemove={onRemove}
                        tooltipContent={tooltipContent}
                    />
                    <Box minHeight={boxHeight}>
                        <Text alignment='center' color='subdued'>{emptyMessage}</Text>
                    </Box>
                </VerticalStack>
            </Card>
        )
    }

    let totalServers = 0

    items.forEach((item) =>{ 
        totalServers += item.value
    })

    let tableRows = [] 
    items.forEach((item, index) =>{ 
        const row1 = [
            (
                <HorizontalStack gap={"2"} wrap={false} key={index}>
                    <Avatar size='extraSmall' source={item.icon}/>
                    <Text variant='bodyMd' fontWeight='medium'>{item.name}</Text>
                </HorizontalStack>
            )
        ]
        const row2 = [
            (
                <HorizontalStack gap={"2"} wrap={false} key={index} align='end'>
                    <Text variant='bodyMd' fontWeight='medium'>{item.value}</Text>
                    <Box width='40px'>
                        <ProgressBar size='small' progress={item.value*100 / totalServers} animated={false} />
                    </Box>
                </HorizontalStack>
            )
        ]
        tableRows.push([row1, row2])
    })

    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader
                    title={title}
                    itemId={itemId}
                    onRemove={onRemove}
                    tooltipContent={tooltipContent}
                />
                <Scrollable style={{maxHeight: boxHeight}} focusable>
                    <DataTable headings={[]}
                        columnContentTypes={[
                            'text',
                            'numeric'
                        ]}
                        rows={tableRows}
                        increasedTableDensity
                        hoverable={false}
                        hideScrollIndicator={true}
                    />
                </Scrollable>
            </VerticalStack>
        </Card>
    )
}

export default ServersLayout