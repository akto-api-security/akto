import { Badge, Box, Card, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import React from 'react'
import onFunc from '../transform'
import { QuestionMarkMinor } from "@shopify/polaris-icons"

function TestSuitesCard({cardObj}) {

    const status= onFunc.getStatus(cardObj.severity)
    return (
        <div className='test-info'>
            <Card background='bg-subdued'>
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between">
                        <HorizontalStack gap="1">
                            <Text variant="headingMd" as='h4' color='subdued'>{cardObj.method}</Text>
                            <Text variant="headingSm" as='h4' color='subdued'>{cardObj.path}</Text>
                        </HorizontalStack>

                        <Badge status={status} size="large-experimental">{cardObj.severity}</Badge>
                    </HorizontalStack>

                    <HorizontalStack gap="1">
                        <Text variant='headingSm' as='h4' color='subdued'>{cardObj.vulnerability}</Text>
                        <Tooltip content={cardObj.testName}>
                            <Box>
                                <Icon source = {QuestionMarkMinor} />
                            </Box>
                        </Tooltip>
                    </HorizontalStack>
                </VerticalStack>
            </Card>
        </div>
    )
}

export default TestSuitesCard