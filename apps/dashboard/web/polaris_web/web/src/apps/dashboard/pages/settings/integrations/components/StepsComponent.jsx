import { Button, InlineStack, LegacyCard, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function StepsComponent({integrationSteps, onClickFunc, buttonActive}) {
    return (
        <LegacyCard.Section title="Follow steps">
            <VerticalStack gap={3}>
                {integrationSteps.map((x,index)=> {
                    return (
                        <VerticalStack gap={2} key={index}>
                            <InlineStack gap={1}>
                                <Text fontWeight="semibold" variant="bodyLg">{index + 1}.</Text>
                                <Text variant="bodyLg">{x.text}</Text>
                            </InlineStack>
                            {x?.component}
                        </VerticalStack>
                    );
                })}
                <InlineStack align="end">
                    <Button

                        size="medium"
                        onClick={onClickFunc}
                        disabled={!buttonActive}
                        variant="primary">Next</Button>
                </InlineStack>
            </VerticalStack>
        </LegacyCard.Section>
    );
}

export default StepsComponent