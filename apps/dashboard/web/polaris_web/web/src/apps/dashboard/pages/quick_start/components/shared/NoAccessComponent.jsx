import { Button, InlineStack, BlockStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'
import JsonComponent from './JsonComponent'

function NoAccessComponent({steps , dataString, onClickFunc, toolTipContent, title}) {
    const navigate = useNavigate(0)
    const noAccessComponent = (
        <BlockStack gap="100">
            {steps.map((element,index) => (
                <BlockStack gap="100" key={index}>
                    <InlineStack gap="100" wrap={false} key={element.text}>
                        <span>{index + 1}. {element.text}</span>
                        <span>{element.textComponent}</span>
                    </InlineStack>
                    <InlineStack gap="300">
                        <div/>
                        {element?.component}
                    </InlineStack>
                </BlockStack>
            ))}
            <span>6. Click <Button  onClick={() => navigate(0)} variant="plain">here</Button> to refresh.</span>
        </BlockStack>
    )

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: '20px'}}>
            {noAccessComponent}
            <JsonComponent dataString={dataString} onClickFunc={onClickFunc} title={title} toolTipContent={toolTipContent}/>
        </div>
    )
}

export default NoAccessComponent