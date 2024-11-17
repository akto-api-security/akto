import { Box, Text, BlockStack } from '@shopify/polaris'
import React from 'react'

function IntroComponent() {

    const introText = "Harness the power of ChatGPT for API Security on your fingertips now! Akto integrates with ChatGPT to bring you insights from the most powerful bot."
    return (
        <Box padding="500">
            <BlockStack gap="600" align="center">
                <div style={{margin: "auto"}}>
                    <Text variant='headingXl' as='h3'>AktoGPT</Text>
                </div>
                <Box padding="200">
                    <Text variant="headingMd" as="h5" color="subdued">{introText}</Text>
                </Box>
            </BlockStack>
        </Box>
    );
}

export default IntroComponent