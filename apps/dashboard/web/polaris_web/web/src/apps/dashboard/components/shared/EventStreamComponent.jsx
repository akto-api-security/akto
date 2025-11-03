import React, { useState, useEffect } from 'react'
import {
    ArrowDownMinor, ArrowUpMinor
} from '@shopify/polaris-icons';
import {
    HorizontalStack, Box, LegacyCard,
    Button, Icon, Text
} from '@shopify/polaris';

function EventStreamComponent({ eventStream, minHeight = "300px" }) {
    const [currentEventIndex, setCurrentEventIndex] = useState(0)
    const [events, setEvents] = useState([])

    useEffect(() => {
        // Parse the event stream - handle SSE format
        let parsedEvents = []
        
        try {
            // First try to parse as JSON array
            parsedEvents = JSON.parse(eventStream)
        } catch {
            // Handle SSE format (event: and data: lines)
            const lines = eventStream.split('\n')
            let currentEvent = null
            
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i].trim()
                
                if (line.startsWith('event:')) {
                    // Start of a new event
                    if (currentEvent) {
                        parsedEvents.push(currentEvent)
                    }
                    currentEvent = {
                        type: line.substring(6).trim(),
                        data: null,
                        raw: false
                    }
                } else if (line.startsWith('data:') && currentEvent) {
                    // Data for the current event
                    const dataString = line.substring(5).trim()
                    try {
                        currentEvent.data = JSON.parse(dataString)
                    } catch {
                        currentEvent.data = dataString
                        currentEvent.raw = true
                    }
                } else if (line === '' && currentEvent) {
                    // Empty line indicates end of event
                    parsedEvents.push(currentEvent)
                    currentEvent = null
                }
            }
            
            // Add the last event if it exists
            if (currentEvent) {
                parsedEvents.push(currentEvent)
            }
            
            // If no SSE events found, try other formats
            if (parsedEvents.length === 0) {
                try {
                    // Try to parse as newline-separated JSON objects
                    parsedEvents = eventStream
                        .split('\n')
                        .filter(line => line.trim())
                        .map(line => {
                            try {
                                return JSON.parse(line.trim())
                            } catch {
                                return { raw: line.trim() }
                            }
                        })
                } catch {
                    // If all else fails, treat as raw text split by some delimiter
                    parsedEvents = eventStream
                        .split('\n\n')
                        .filter(event => event.trim())
                        .map((event, index) => ({ 
                            id: index, 
                            content: event.trim(),
                            raw: true 
                        }))
                }
            }
        }
        setEvents(parsedEvents)
        // Start from the last event (most recent)
        setCurrentEventIndex(Math.max(0, parsedEvents.length - 1))
    }, [eventStream])

    const checkButtonActive = (buttonType) => {
        if (buttonType === 'next') {
            // "Next" means go to older events (decrease index)
            return currentEventIndex > 0
        } else {
            // "Prev" means go to newer events (increase index)
            return currentEventIndex < events.length - 1
        }
    }

    const changeIndex = (buttonType) => {
        if (buttonType === 'next') {
            // "Next" means go to older events (decrease index)
            setCurrentEventIndex(prev => Math.max(prev - 1, 0))
        } else {
            // "Prev" means go to newer events (increase index)
            setCurrentEventIndex(prev => Math.min(prev + 1, events.length - 1))
        }
    }

    const formatEvent = (event) => {
        if (event.raw && event.content) {
            return event.content
        }
        
        if (event.data !== null && event.data !== undefined) {
            // For SSE events, format the data
            if (typeof event.data === 'string') {
                return event.data
            }
            try {
                return JSON.stringify(event.data, null, 2)
            } catch {
                return String(event.data)
            }
        }
        
        // Try to format as JSON with proper indentation
        try {
            return JSON.stringify(event, null, 2)
        } catch {
            return String(event)
        }
    }

    const getEventType = (event) => {
        if (event.type) return event.type
        if (event.raw) return 'Raw Text'
        if (event.event) return event.event
        if (event.name) return event.name
        return 'Event'
    }

    if (events.length === 0) {
        return (
            <Box padding="3" borderRadius="2" background="bg-subdued">
                <Text variant="bodyMd" color="subdued">No events to display</Text>
            </Box>
        )
    }

    const currentEvent = events[currentEventIndex]

    return (
        <Box id='event-stream-container'>
            <LegacyCard.Section flush>
                <Box padding="2">
                    <HorizontalStack padding="2" align='space-between'>
                        <Text variant="headingMd">
                            Event Stream ({events.length - currentEventIndex} of {events.length}) - {currentEventIndex === events.length - 1 ? 'Latest' : 'Older'}
                        </Text>
                        <HorizontalStack gap="2">
                            <Box borderInlineEndWidth='1' borderColor="border-subdued" padding="1">
                                <Text variant="bodyMd" color="subdued">
                                    {getEventType(currentEvent)}
                                </Text>
                            </Box>
                            <HorizontalStack gap="1">
                                <Button 
                                    plain 
                                    monochrome 
                                    disabled={!checkButtonActive("prev")} 
                                    onClick={() => changeIndex("prev")}
                                    title="Go to newer event"
                                >
                                    <Box padding="05" borderWidth="1" borderColor="border" borderRadius="1">
                                        <Icon source={ArrowUpMinor} />
                                    </Box>
                                </Button>
                                <Button 
                                    plain 
                                    monochrome 
                                    disabled={!checkButtonActive("next")} 
                                    onClick={() => changeIndex("next")}
                                    title="Go to older event"
                                >
                                    <Box padding="05" borderWidth="1" borderColor="border" borderRadius="1">
                                        <Icon source={ArrowDownMinor} />
                                    </Box>
                                </Button>
                            </HorizontalStack>
                        </HorizontalStack>
                    </HorizontalStack>
                </Box>
            </LegacyCard.Section>
            <LegacyCard.Section flush>
                <Box padding="2">
                    <Box 
                        style={{
                            minHeight: minHeight,
                            maxHeight: '500px',
                            overflow: 'auto',
                            backgroundColor: '#f8f9fa',
                            border: '1px solid #e1e5e9',
                            borderRadius: '6px',
                            padding: '1em',
                            fontFamily: 'Monaco, Menlo, Ubuntu Mono, monospace',
                            fontSize: '0.9em',
                            lineHeight: '1.4',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word'
                        }}
                    >
                        {formatEvent(currentEvent)}
                    </Box>
                </Box>
            </LegacyCard.Section>
        </Box>
    )
}

export default EventStreamComponent
