import { Box, Button, ButtonGroup, Divider, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function MicrosoftDefenderConnector() {
    const goToDocs = () => {
        window.open("https://docs.microsoft.com/en-us/microsoft-365/security/defender-endpoint/", '_blank')
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect your Microsoft Defender for Endpoint account to Akto for enhanced security insights.
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

export default MicrosoftDefenderConnector
