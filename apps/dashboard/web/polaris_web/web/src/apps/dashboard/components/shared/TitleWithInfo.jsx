import { Box, HorizontalStack, Icon, Link, Popover, Text } from '@shopify/polaris'
import { InfoMinor } from "@shopify/polaris-icons"
import React, { useState } from 'react'

function TitleWithInfo({titleComp, textProps, titleText, tooltipContent, docsUrl}) {
    
    const content = docsUrl ?

        <Box as="span">
            {tooltipContent} {" "}
            <Link url={docsUrl} target="_blank">Learn more</Link>
        </Box> 
         : tooltipContent
    const [active, setActive] = useState(false)
    const [contentActive,setContentActive] = useState(false)
    return (
        <HorizontalStack gap={"1"}>
            {titleComp ? titleComp : <Text variant="headingLg" {...textProps}>{titleText}</Text>}
            <Popover 
                active={active || contentActive}
                activator={
                    <div  
                        onMouseEnter={()=> setTimeout(()=> {
                            setActive(true)
                        },300)}
                        onMouseLeave={()=> setTimeout(()=> {
                            setActive(false)
                        },100)}
                    > 
                        <Box width='20px' minHeight='20px'>
                            <Icon source={InfoMinor} color="subdued" />
                        </Box> 
                    </div>
                }
                preferredPosition="top"
                onClose={() => setActive(false)}
            >
                <div style={{maxWidth: '350px' ,padding: "12px"}} >
                    <div  
                        onMouseEnter={()=>setContentActive(true)}
                        onMouseLeave={() => setContentActive(false)}
                    >
                        {content}
                    </div>
                </div>
            </Popover>
            
        </HorizontalStack>
    )
}

export default TitleWithInfo