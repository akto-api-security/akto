import { Button, InlineStack, LegacyCard, BlockStack } from '@shopify/polaris'
import React from 'react'
import LineComponent from './LineComponent'

function Details({onClickFunc, values}) {
    return (
        <LegacyCard.Section title="Integration details">
            <br/>
            <BlockStack gap={300}>
                <BlockStack gap={200}>
                    {values.map((x,index)=> {
                        return (
                            <LineComponent title={x.title} value={x.value} key={index}/>
                        )
                    })}
                </BlockStack>
                <InlineStack align="end">
                    <Button  onClick={onClickFunc} variant="primary">Delete SSO</Button>
                </InlineStack>
            </BlockStack>
        </LegacyCard.Section>
    );
}

export default Details