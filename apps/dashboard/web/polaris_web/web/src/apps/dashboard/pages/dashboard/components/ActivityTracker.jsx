import { Avatar, Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function ActivityTracker({latestActivity}) {
    return (
        <Card>
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Latest activity</Text>
                <HorizontalStack gap={3}>
                    <VerticalStack>
                    {latestActivity.map((c,index)=>{
                        return (
                            <div style={{display: 'flex', gap: '12px'}}>
                                <Box>
                                    <Avatar shape="round" size="extraSmall" source="/public/steps_icon.svg"/>
                                    {index < (latestActivity.length - 1) ? <div style={{background: '#7F56D9', width: '2px', margin: 'auto', height: '36px'}} /> : null}
                                </Box>
                                <Box>
                                    <Text variant="bodyMd" fontWeight="semibold">{c.title}</Text>
                                    <Text variant="bodyMd" color="subdued">{c.description}</Text>
                                </Box>
                            </div>
                        )
                    })}
                    </VerticalStack>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    )
}

export default ActivityTracker