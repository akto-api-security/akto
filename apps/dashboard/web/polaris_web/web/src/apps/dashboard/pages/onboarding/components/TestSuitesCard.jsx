import { Badge, Box, Card, InlineStack, Icon, Text, Tooltip, BlockStack } from '@shopify/polaris'
import React from 'react'
import onFunc from '../transform'
import { InfoIcon } from "@shopify/polaris-icons";

function TestSuitesCard({cardObj}) {

    const status= onFunc.getStatus(cardObj?.severity)
    const color = onFunc.getTextColor(cardObj?.method)
    return (
        <Card>
            <BlockStack gap="200">
                <InlineStack align="space-between">
                    <InlineStack gap="100">
                        <div style={{color: color, fontSize: '16px', fontWeight: 600}}>
                            {cardObj?.method}
                        </div>
                        <Text variant="headingMd" as='h4'>{cardObj.path}</Text>
                    </InlineStack>
                    <Badge tone={status} size="large-experimental">{cardObj.severity}</Badge>
                </InlineStack>
                <InlineStack gap="200">
                    <Text variant='bodyLg' fontWeight="medium">{cardObj.vulnerability}</Text>
                    <Tooltip content={cardObj.testName}>
                        <Box>
                            <Icon source = {InfoIcon} tone="base"/>
                        </Box>
                    </Tooltip>
                </InlineStack>
            </BlockStack>
        </Card>
    );
}

export default TestSuitesCard