import { Button, HorizontalStack, LegacyCard, VerticalStack } from '@shopify/polaris'
import React from 'react'
import LineComponent from './LineComponent'
import AllowedAction from '../../../../components/shared/AllowedAction'

function Details({onClickFunc, values, deleteAllowed = true}) {
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
                    <AllowedAction allowed={deleteAllowed}>
                    <Button primary onClick={onClickFunc} >Delete SSO</Button>
                    </AllowedAction>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )
}

export default Details