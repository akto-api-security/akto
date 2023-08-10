import { Box, List, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function IntroComponent() {

    const introText = "Harness the power of ChatGPT for API Security on your fingertips now! Akto integrates with ChatGPT to bring you insights from the most powerful bot."
    return (
        <div className='intro-card'>
            <Box>
                <VerticalStack gap="8">
                    <div style={{margin: "auto"}}>
                        <Text variant='headingXl' as='h3'>AktoGPT</Text>
                    </div>
                    <Text variant="headingMd" as="h5">{introText}</Text>
                </VerticalStack>
            </Box>
        </div>
    )
}

export default IntroComponent