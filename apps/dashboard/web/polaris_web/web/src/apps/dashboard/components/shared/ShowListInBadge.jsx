import { Badge, Box, HorizontalStack } from '@shopify/polaris'
import React from 'react'

function ShowListInBadge({itemsArr, maxWidth, status, maxItems}) {
    return (
        <Box maxWidth={maxWidth}>
            <HorizontalStack gap={"1"} wrap={false}>
                {itemsArr.slice(0,maxItems).map((item, index) => {
                    return(
                        <Badge key={index + item} size="medium" status={status}>{item}</Badge>
                    )
                })}
                {(itemsArr.length - maxItems) > 0 ? <Badge status={status} size="medium">+ {itemsArr.length - maxItems}</Badge> : null}
            </HorizontalStack>
        </Box>
    )
}

export default ShowListInBadge