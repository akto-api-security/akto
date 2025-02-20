import { Text, Tooltip } from '@shopify/polaris'
import React from 'react'

function HeadingWithTooltip({title, content}) {
    return (
        <Tooltip borderRadius="2" padding="4" hasUnderline={true} content={content}>
            <Text>{title}</Text>
        </Tooltip> 
    )
}

export default HeadingWithTooltip