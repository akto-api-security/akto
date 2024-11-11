import { Button, HorizontalStack, LegacyCard, VerticalStack } from '@shopify/polaris'
import React from 'react'
import LineComponent from './LineComponent'

function Details({onClickFunc, values}) {
    return (    
        <LegacyCard.Section title="Integration details">
            <br/>
            <VerticalStack gap={3}>
                <VerticalStack gap={2}>
                    {values.map((x,index)=> {
                        return (
                            <LineComponent title={x.title} value={x.value} key={index}/>
                        )
                    })}
                </VerticalStack>
                <HorizontalStack align="end">
                    <Button primary onClick={onClickFunc} >Delete SSO</Button>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )
}

export default Details