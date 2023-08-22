import { Box, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function IntroComponent() {

    const introText = "Harness the power of ChatGPT for API Security on your fingertips now! Akto integrates with ChatGPT to bring you insights from the most powerful bot."
    return (
        <Box padding="5">
            <VerticalStack gap="6" align="center">
                <div style={{margin: "auto"}}>
                    <Text variant='headingXl' as='h3'>AktoGPT</Text>
                </div>
                <Box padding="2">
                    <Text variant="headingMd" as="h5" color="subdued">{introText}</Text>
                </Box>
            </VerticalStack>
        </Box>
    )
}

export default IntroComponent