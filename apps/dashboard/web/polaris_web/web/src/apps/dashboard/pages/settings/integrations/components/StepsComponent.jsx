import AktoButton from './../../../../components/shared/AktoButton';
import { Button, HorizontalStack, LegacyCard, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function StepsComponent({integrationSteps, onClickFunc, buttonActive}) {
    return (
        <LegacyCard.Section title="Follow steps">
            <VerticalStack gap={3}>
                {integrationSteps.map((x,index)=> {
                    return(
                        <VerticalStack gap={2} key={index}>
                            <HorizontalStack gap={1}>
                                <Text fontWeight="semibold" variant="bodyLg">{index + 1}.</Text>
                                <Text variant="bodyLg">{x.text}</Text>
                            </HorizontalStack>
                            {x?.component}
                        </VerticalStack>
                    )
                })}
                <HorizontalStack align="end">
                    <AktoButton  primary size="medium" onClick={onClickFunc} disabled={!buttonActive}>Next</AktoButton>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )
}

export default StepsComponent