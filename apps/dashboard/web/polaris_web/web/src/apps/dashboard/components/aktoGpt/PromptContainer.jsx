import React from 'react'
import { Box, InlineStack, Icon, Text, Tooltip } from '@shopify/polaris'

function PromptContainer({itemObj, activePrompt, setActivePrompt}) {

    const changeLabel = (label) =>{
        if(label.includes("${input}")){
            let startString = label.split("${input}")[0]
            let endString = "_ _ _"
            startString = startString + endString
            return startString
        }else{
            return label
        }
    }

    return (
        <div style={{padding: '2px', cursor: "pointer"}} onClick={()=> setActivePrompt(itemObj)}>
            <Tooltip content={changeLabel(itemObj.label)} dismissOnMouseOut preferredPosition='below'>
                <Box background={activePrompt === itemObj.label ? "bg-active" : ""} padding="200" borderRadius="2">
                    <InlineStack gap={"200"}>
                        <Box>
                            <Icon source={itemObj.icon} tone="subdued"/>
                        </Box>
                        <Box maxWidth="10vw">
                            <Text truncate variant="bodyMd" fontWeight="semibold" tone="subdued">{(itemObj.label).split("${input}")[0]}</Text>
                        </Box>
                    </InlineStack>
                </Box>
            </Tooltip>
        </div>
    );
}

export default PromptContainer