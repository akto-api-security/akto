import { Button, InlineStack, LegacyCard, VerticalStack } from '@shopify/polaris'
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
                <InlineStack align="end">
                    <Button  onClick={onClickFunc} variant="primary">Delete SSO</Button>
                </InlineStack>
            </VerticalStack>
        </LegacyCard.Section>
    );
}

export default Details