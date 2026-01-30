import { Avatar, Box, DataTable, HorizontalStack, ProgressBar, Scrollable, Text } from '@shopify/polaris'
import React from 'react'

function ServersLayout({items, boxHeight}) {
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
        <Scrollable style={{maxHeight: boxHeight}} focusable shadow>
            <DataTable headings={[]}
                columnContentTypes={[
                    'text',
                    'numeric'
                ]}
                rows={tableRows}
                increasedTableDensity
                hoverable={false}
            />
        </Scrollable>
    )
}

export default ServersLayout