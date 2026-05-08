import { Avatar, Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function ActivityTracker({ latestActivity }) {
    const formatDate = (epochTime) => {
        const date = new Date(epochTime * 1000)
        const formattedDate = date.toLocaleDateString('en-US', { 
            year: 'numeric', month: 'short', day: 'numeric' 
        })
        const formattedTime = date.toLocaleTimeString('en-US', { 
            hour: 'numeric', minute: '2-digit', hour12: true 
        })
        return [formattedDate, formattedTime]
    }
    
    // Group events by date
    const groupEventsByDate = (events) => {
        return events.reduce((groupedEvents, event) => {
            const eventDate = formatDate(event.timestamp)[0]
            if (!groupedEvents[eventDate]) {
                groupedEvents[eventDate] = []
            }
            groupedEvents[eventDate].push(event)
            return groupedEvents
        }, {})
    }

    const groupedActivity = groupEventsByDate(latestActivity)

    return (
        <Box padding={5}>
            <VerticalStack>
                {Object.keys(groupedActivity).map((date, dateIndex) => (
                    <Box key={dateIndex}>
                        <HorizontalStack gap={4}>
                            <Box borderColor='border-subdued' borderInlineEndWidth='2' width='0' paddingInlineStart={3} minHeight='44px' />
                            <Text variant="bodySm" color="subdued" style={{ marginBottom: '10px' }}>
                                {date}
                            </Text>
                        </HorizontalStack>
                        {groupedActivity[date].map((event, eventIndex) => (
                            <HorizontalStack key={eventIndex} align='space-between'>
                                <HorizontalStack gap={3}>
                                    <Box>
                                        <div style={{marginBlock: '5px'}}><Avatar shape="round" size="extraSmall" source="/public/issues-event-icon.svg" /></div>
                                        {eventIndex < (groupedActivity[date].length - 1) ? (
                                            <Box borderColor='border-subdued' borderInlineEndWidth='2' width='0' paddingInlineStart={3} minHeight='12px' />
                                        ) : null}
                                    </Box>
                                    <Box>
                                        <div style={{marginBlock: '5px'}}>
                                            <Text variant="bodyMd">{event.description}{event.user ? ` by ${event.user}` : ''}</Text>
                                        </div>
                                    </Box>
                                </HorizontalStack>
                                <div style={{marginBlock: '5px'}}>
                                    <Text variant="bodySm" color="subdued">
                                        {formatDate(event.timestamp)[1]}
                                    </Text>
                                </div>
                            </HorizontalStack>
                        ))}
                    </Box>
                ))}
            </VerticalStack>
        </Box>
    )
}

export default ActivityTracker;
