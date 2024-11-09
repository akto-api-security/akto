import { Button, HorizontalStack, LegacyCard, Link, Text, VerticalStack } from '@shopify/polaris'
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
                    <HorizontalStack gap={"1"}>
                        <Text>Use</Text>
                        <Link>https://app.akto.io/sso-login</Link>
                        <Text>for signing into AKTO dashboard via SSO.</Text>
                    </HorizontalStack>
                </VerticalStack>
                <HorizontalStack align="end">
                    <Button primary onClick={onClickFunc} >Delete SSO</Button>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )
}

export default Details