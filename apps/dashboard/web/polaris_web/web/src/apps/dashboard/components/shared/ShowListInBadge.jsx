import { Badge, Box, InlineStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from './TooltipText'

function ShowListInBadge({itemsArr, maxWidth, status, maxItems, itemWidth}) {
    return (
        <Box maxWidth={maxWidth}>
            <InlineStack gap={"100"} wrap={false}>
                {itemsArr.slice(0,maxItems).map((item, index) => {
                    return(
                        <Badge key={index + item} size="medium" tone={status}>
                            <Box maxWidth={itemWidth || maxWidth}>
                                <TooltipText tooltip={item} text={item} />
                            </Box>
                        </Badge>
                    )
                })}
                {(itemsArr.length - maxItems) > 0 ? <Badge tone={status} size="medium">+ {itemsArr.length - maxItems}</Badge> : null}
            </InlineStack>
        </Box>
    );
}

export default ShowListInBadge