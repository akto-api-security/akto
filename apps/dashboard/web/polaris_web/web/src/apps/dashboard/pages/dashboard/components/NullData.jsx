import { Button, HorizontalStack, LegacyCard, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'

function NullData({text,url,description,urlText}) {

    const navigate = useNavigate();

    return (
        <LegacyCard title={text}>
            <LegacyCard.Section>
                <VerticalStack gap={1}>
                    <Text variant="bodyMd">{description}</Text>
                    <HorizontalStack gap={1}>
                        <Text>Click</Text>
                        <Button plain onClick={()=> navigate(url)}>here</Button>
                        <Text>{urlText}</Text>
                    </HorizontalStack>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default NullData