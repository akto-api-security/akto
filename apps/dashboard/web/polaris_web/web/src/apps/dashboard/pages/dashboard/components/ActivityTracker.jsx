import { Avatar, Box, Button, Card, HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function ActivityTracker({latestActivity, onLoadMore, showLoadMore}) {
    return (
        <Card>
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Latest activity</Text>
                <Scrollable style={{maxHeight: '400px'}} shadow> 
                    <VerticalStack gap={3}>
                        <HorizontalStack gap={3}>
                            <VerticalStack>
                            {latestActivity.map((c,index)=>{
                                return (
                                    <div style={{display: 'flex', gap: '12px'}} key={index}>
                                        <Box>
                                            <Avatar shape="round" size="extraSmall" source="/public/steps_icon.svg"/>
                                            {index < (latestActivity.length - 1) ? <div style={{background: '#7F56D9', width: '2px', margin: 'auto', height: '36px'}} /> : null}
                                        </Box>
                                        <Box>
                                            <Text variant="bodyMd" fontWeight="semibold">{c.type}</Text>
                                            <Text variant="bodyMd" color="subdued">{c.description}</Text>
                                        </Box>
                                    </div>
                                )
                            })}
                            </VerticalStack>
                        </HorizontalStack>

                        {showLoadMore ? <Button plain removeUnderline onClick={onLoadMore} /> : null}
                    </VerticalStack>
                </Scrollable>
            </VerticalStack>
        </Card>
    )
}

export default ActivityTracker