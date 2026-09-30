import { Box, Card, HorizontalStack, Icon, Text } from '@shopify/polaris'
import { AlertMinor } from '@shopify/polaris-icons'
import ChainVisual from '../ChainVisual'

function DangerousPathCallout({ dangerousPath }) {
    if (!dangerousPath || !dangerousPath.illustrative) return null
    return (
        <Card>
            <Box padding="4">
                <HorizontalStack gap="2" blockAlign="center">
                    <Box><Icon source={AlertMinor} color="critical" /></Box>
                    <Text variant="bodyMd" fontWeight="semibold">Part of a dangerous execution path</Text>
                </HorizontalStack>
                <Box paddingBlockStart="3">
                    <ChainVisual chain={dangerousPath.illustrative.chain} />
                </Box>
            </Box>
        </Card>
    )
}

export default DangerousPathCallout
