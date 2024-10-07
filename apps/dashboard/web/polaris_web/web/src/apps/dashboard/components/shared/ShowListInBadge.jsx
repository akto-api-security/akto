import { Badge, Box, HorizontalStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from './TooltipText'

function ShowListInBadge({itemsArr, maxWidth, status, maxItems, itemWidth}) {
    return (
        <Box maxWidth={maxWidth}>
            <HorizontalStack gap={"1"} wrap={false}>
                {itemsArr.slice(0,maxItems).map((item, index) => {
                    return(
                        <Badge key={index + item} size="medium" status={status}>
                            <Box maxWidth={itemWidth || maxWidth}>
                                <TooltipText tooltip={item} text={item} />
                            </Box>
                        </Badge>
                    )
                })}
                {(itemsArr.length - maxItems) > 0 ? <Badge status={status} size="medium">+ {itemsArr.length - maxItems}</Badge> : null}
            </HorizontalStack>
        </Box>
    )
}

export default ShowListInBadge