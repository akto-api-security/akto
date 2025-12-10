import { Box, Button, ButtonGroup, Divider, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function AgenticShield() {
    const goToDocs = () => {
        window.open("https://docs.akto.io/agentic-shield")
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                This feature is under development and will be available soon.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <ButtonGroup>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default AgenticShield


