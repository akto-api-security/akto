import { Avatar, Badge, Box, Button, Card, Divider, HorizontalGrid, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { TeamMajor, ToolsMajor, EmailMajor } from "@shopify/polaris-icons"
function ActionItemCard() {
    return (
        <Card padding={"5"}>
            <VerticalStack gap={"3"}>
                <Box width='30px'>
                <Badge status="critical-strong-experimental">P0</Badge>
                </Box>
                <Box>
                    <Text variant="headingSm">3 APIs have no authentication</Text>
                    <Text variant='bodyMd' color='subdued'>
                        Publicly accessible business logic
                    </Text>
                </Box>
                <HorizontalStack gap={"2"}>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={TeamMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Platform</Text>
                    </HorizontalStack>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={ToolsMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>Low</Text>
                    </HorizontalStack>
                </HorizontalStack>
                <Divider />
                <div style={{display: 'flex', justifyContent: 'space-between', gap: "12px"}}>
                    <Button plain removeUnderline>Assign Task</Button>
                    <HorizontalStack gap={"2"}>
                        <Icon source={EmailMajor} color="subdued"/>
                        <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                    </HorizontalStack>
                </div>
            </VerticalStack>
        </Card>
    )
}

export default ActionItemCard