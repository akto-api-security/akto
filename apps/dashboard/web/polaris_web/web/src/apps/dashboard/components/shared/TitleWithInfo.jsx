import { Avatar, Box, Icon, InlineStack, Link, Popover, Text, Tooltip } from '@shopify/polaris'
import {InfoIcon} from '@shopify/polaris-icons'
import React, { useState } from 'react'

function TitleWithInfo({titleComp, textProps, titleText, tooltipContent, docsUrl, tone}) {
    
    const content = docsUrl ?

        <Box as="span">
            {tooltipContent} {" "}
            <Link url={docsUrl} target="_blank">Learn more</Link>
        </Box> 
         : tooltipContent
    const [active, setActive] = useState(false)
    const [contentActive,setContentActive] = useState(false)
    return (
        <InlineStack gap={"100"}>
            {titleComp ? titleComp : <Text tone={tone?tone:""} variant="headingLg" {...textProps}>{titleText}</Text>}
            {docsUrl ?<Popover 
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
                        <div className='reduce-size'>
                            <Icon source={InfoIcon}></Icon>
                        </div> 
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
            </Popover> : tooltipContent ? <Tooltip content={tooltipContent} dismissOnMouseOut><div className='reduce-size'>
                            <Icon source={InfoIcon}></Icon>
                        </div> </Tooltip> : null
            }
        </InlineStack>
    );
}

export default TitleWithInfo