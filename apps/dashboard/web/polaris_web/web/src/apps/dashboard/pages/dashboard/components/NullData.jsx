import { Button, InlineStack, LegacyCard, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'

function NullData({text,url,description,urlText}) {

    const navigate = useNavigate();

    return (
        <LegacyCard title={text}>
            <LegacyCard.Section>
                <BlockStack gap={1}>
                    <Text variant="bodyMd">{description}</Text>
                    <InlineStack gap={1}>
                        <Text>Click</Text>
                        <Button  onClick={()=> navigate(url)} variant="plain">here</Button>
                        <Text>{urlText}</Text>
                    </InlineStack>
                </BlockStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
}

export default NullData