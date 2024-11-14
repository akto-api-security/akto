import { Button, InlineStack, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'
import JsonComponent from './JsonComponent'

function NoAccessComponent({steps , dataString, onClickFunc, toolTipContent, title}) {
    const navigate = useNavigate(0)
    const noAccessComponent = (
        <VerticalStack gap="1">
            {steps.map((element,index) => (
                <VerticalStack gap="1" key={index}>
                    <InlineStack gap="1" wrap={false} key={element.text}>
                        <span>{index + 1}.</span>
                        <span>{element.text}</span>
                        <span>{element.textComponent}</span>
                    </InlineStack>
                    <InlineStack gap="3">
                        <div/>
                        {element?.component}
                    </InlineStack>
                </VerticalStack>
            ))}
            <span>6. Click <Button  onClick={() => navigate(0)} variant="plain">here</Button> to refresh.</span>
        </VerticalStack>
    )

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: '20px'}}>
            {noAccessComponent}
            <JsonComponent dataString={dataString} onClickFunc={onClickFunc} title={title} toolTipContent={toolTipContent}/>
        </div>
    )
}

export default NoAccessComponent