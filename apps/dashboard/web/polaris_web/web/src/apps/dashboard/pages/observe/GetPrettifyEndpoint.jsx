import { Avatar, Badge, Box, HorizontalStack, Text, Tooltip } from '@shopify/polaris'
import React, { useRef, useState } from 'react'
import func from '@/util/func'
import transform from '../onboarding/transform'
import observeFunc from "./transform"
function GetPrettifyEndpoint({method,url, isNew}){
    const ref = useRef(null)
    const [copyActive, setCopyActive] = useState(false)
    return(
        <div style={{display: 'flex', gap: '4px'}} ref={ref} onMouseEnter={() => setCopyActive(true)} onMouseLeave={() => setCopyActive(false)}>
            <Box width="54px">
                <HorizontalStack align="end">
                    <span style={{color: transform.getTextColor(method), fontSize: "14px", fontWeight: 500, lineHeight: '20px'}}>{method}</span>
                </HorizontalStack>
            </Box>
            <Box width="30vw">
                <div style={{display: "flex", justifyContent: "space-between", gap:"24px"}}>
                    <div style={{display: "flex"}}>
                        <Box>
                            <Text variant="bodyMd" fontWeight="medium" breakWord>{observeFunc.getTruncatedUrl(url)}</Text>
                        </Box>
                        {copyActive ? 
                            <div onClick={(e) => {e.stopPropagation();func.copyToClipboard(url, ref, "URL copied");}}>
                                <Tooltip content="Copy endpoint" dismissOnMouseOut>
                                    <div className="reduce-size">
                                        <Avatar size="extraSmall" source="/public/copy_icon.svg" />
                                    </div>
                                </Tooltip>
                                <Box ref={ref} />
                            </div>
                        :null}
                    </div>
                    <Box>
                        {isNew ? <Badge size="small">New</Badge> : null}
                    </Box>
                </div>
            </Box>
        </div> 
    )
}


export default GetPrettifyEndpoint