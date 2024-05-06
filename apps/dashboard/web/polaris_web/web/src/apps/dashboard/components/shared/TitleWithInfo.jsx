import { Box, HorizontalStack, Icon, Link, Text, Tooltip } from '@shopify/polaris'
import { InfoMinor } from "@shopify/polaris-icons"
import React from 'react'

function TitleWithInfo({titleComp, textProps, titleText, tooltipContent, docsUrl}) {
    
    const content = docsUrl ?
        <Box as="span">
            {tooltipContent}
            <Link url={docsUrl} target="_blank">Learn more</Link>
        </Box> 
         : tooltipContent

    return (
        <HorizontalStack gap={"1"}>
            {titleComp ? titleComp : <Text variant="headingLg" {...textProps}>{titleText}</Text>}
            <Tooltip content={content} hoverDelay={"1000"} preferredPosition="above">
                <Box minHeight='20px' width='20px'><Icon color="subdued" source={InfoMinor} /></Box>
            </Tooltip>
        </HorizontalStack>
    )
}

export default TitleWithInfo