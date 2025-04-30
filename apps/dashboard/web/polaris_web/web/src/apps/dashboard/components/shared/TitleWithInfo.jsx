import { Avatar, Box, HorizontalStack, Link, Popover, Text, Tooltip } from '@shopify/polaris'
import React, { useState } from 'react'

function TitleWithInfo(props) {

    const { titleComp, textProps, titleText, tooltipContent, docsUrl } = props
    
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
                            <Avatar shape="round" size="extraSmall" source='/public/info_filled_icon.svg'/>
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
                            <Avatar shape="round" size="extraSmall" source='/public/info_filled_icon.svg'/>
                        </div> </Tooltip> : null
            }
        </HorizontalStack>
    )
}

export default TitleWithInfo