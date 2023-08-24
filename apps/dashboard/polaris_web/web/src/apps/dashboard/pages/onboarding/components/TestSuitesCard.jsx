import { Badge, Box, Card, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import React from 'react'
import onFunc from '../transform'
import { InfoMinor } from "@shopify/polaris-icons"

function TestSuitesCard({cardObj}) {

    const status= onFunc.getStatus(cardObj?.severity)
    const color = onFunc.getTextColor(cardObj?.method)
    return (
        <Card>
            <VerticalStack gap="2">
                <HorizontalStack align="space-between">
                    <HorizontalStack gap="1">
                    <div style={{color: color, fontSize: '16px', fontWeight: 600}}>
                        {cardObj?.method}
                    </div>
                        <Text variant="headingMd" as='h4'>{cardObj.path}</Text>
                    </HorizontalStack>

                    <Badge status={status} size="large-experimental">{cardObj.severity}</Badge>
                </HorizontalStack>

                <HorizontalStack gap="2">
                    <Text variant='bodyLg' fontWeight="medium">{cardObj.vulnerability}</Text>
                    <Tooltip content={cardObj.testName}>
                        <Box>
                            <Icon source = {InfoMinor} color="base"/>
                        </Box>
                    </Tooltip>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    )
}

export default TestSuitesCard