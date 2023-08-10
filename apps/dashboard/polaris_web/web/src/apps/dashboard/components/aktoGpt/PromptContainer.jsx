import React from 'react'
import { Box, Icon, Tooltip } from '@shopify/polaris'

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
        <div style={{padding: '4px'}} onClick={()=> setActivePrompt(itemObj)}>
            <Tooltip content={changeLabel(itemObj.label)} dismissOnMouseOut preferredPosition='below'>
                <div className={"nav-prompt " +  (activePrompt===itemObj.label ? "active-prompt": "")}>
                    <Box>
                        <Icon source={itemObj.icon} color="highlight"/>
                    </Box>
                    <span className="prompt-label">{(itemObj.label).split("${input}")[0]}</span>
                </div>
            </Tooltip>
        </div>
    )
}

export default PromptContainer