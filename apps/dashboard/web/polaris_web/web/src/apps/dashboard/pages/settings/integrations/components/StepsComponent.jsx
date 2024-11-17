import { Button, InlineStack, LegacyCard, Text, BlockStack } from '@shopify/polaris'
import React from 'react'

function StepsComponent({integrationSteps, onClickFunc, buttonActive}) {
    return (
        <LegacyCard.Section title="Follow steps">
            <BlockStack gap={300}>
                {integrationSteps.map((x,index)=> {
                    return (
                        <BlockStack gap={200} key={index}>
                            <InlineStack gap={100}>
                                <Text fontWeight="semibold" variant="bodyLg">{index + 1}.</Text>
                                <Text variant="bodyLg">{x.text}</Text>
                            </InlineStack>
                            {x?.component}
                        </BlockStack>
                    );
                })}
                <InlineStack align="end">
                    <Button

                        size="medium"
                        onClick={onClickFunc}
                        disabled={!buttonActive}
                        variant="primary">Next</Button>
                </InlineStack>
            </BlockStack>
        </LegacyCard.Section>
    );
}

export default StepsComponent