import { Box } from '@shopify/polaris'
import { useState, useEffect } from 'react'
import EventStreamComponent from './EventStreamComponent'
import { MarkdownRenderer, markdownStyles } from './MarkdownComponents'

// Function to separate event stream content from regular content
const separateContent = (msg) => {
    if (!msg || typeof msg !== 'string') return { hasEventStream: false, eventStream: '', regular: msg }
    
    const lines = msg.split('\n')
    let eventStreamLines = []
    let regularLines = []
    let inEventStream = false
    let eventStreamStartIndex = -1
    
    // Find where event stream starts
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        console.log(line)
        if (line.startsWith('event:') || line.startsWith('data:')) {
            if (!inEventStream) {
                inEventStream = true
                eventStreamStartIndex = i
            }
            eventStreamLines.push(lines[i])
        } else if (inEventStream) {
            if (line === '') {
                // Empty line might be part of event stream
                eventStreamLines.push(lines[i])
            } else if (line.startsWith('event:') || line.startsWith('data:')) {
                // Continue event stream
                eventStreamLines.push(lines[i])
            } else {
                inEventStream = false;
            }
        } else {
            // Regular content before event stream
            regularLines.push(lines[i])
        }
    }
    
    // If we found event stream content, check if it's valid
    if (eventStreamLines.length > 0) {
        const eventStreamText = eventStreamLines.join('\n')
        const hasValidEventStream = eventStreamText.includes('event:') && eventStreamText.includes('data:')
        
        if (hasValidEventStream) {
            return {
                hasEventStream: true,
                eventStream: eventStreamText,
                regular: regularLines.join('\n').trim()
            }
        }
    }
    
    // Check if entire message is event stream
    const hasEventLines = lines.some(line => line.trim().startsWith('event:'))
    const hasDataLines = lines.some(line => line.trim().startsWith('data:'))
    
    if (hasEventLines && hasDataLines) {
        return {
            hasEventStream: true,
            eventStream: msg,
            regular: ''
        }
    }
    
    return { hasEventStream: false, eventStream: '', regular: msg }
}

function AIMessage({ message, isStreaming = false }) {
    const [displayedMessage, setDisplayedMessage] = useState('')
    const [showTypingIndicator, setShowTypingIndicator] = useState(false)
    const [isEventStream, setIsEventStream] = useState(false)
    const [eventStreamContent, setEventStreamContent] = useState('')
    const [regularContent, setRegularContent] = useState('')

    useEffect(() => {
        // Separate event stream content from regular content
        const { hasEventStream, eventStream, regular } = separateContent(message)
        setIsEventStream(hasEventStream)
        setEventStreamContent(eventStream)
        setRegularContent(regular)

        if (isStreaming) {
            setShowTypingIndicator(true)
            // Simulate streaming effect by gradually revealing the message
            let currentIndex = 0
            const interval = setInterval(() => {
                if (currentIndex < message.length) {
                    setDisplayedMessage(message.substring(0, currentIndex + 1))
                    currentIndex++
                } else {
                    setShowTypingIndicator(false)
                    clearInterval(interval)
                }
            }, 20) // Adjust speed as needed

            return () => clearInterval(interval)
        } else {
            setDisplayedMessage(message)
            setShowTypingIndicator(false)
        }
    }, [message, isStreaming])

    return (
        <Box padding={"3"} borderRadius="2" background="bg-subdued">
            <div style={{ width: '100%', maxWidth: '100%', overflow: 'hidden' }}>
                <div style={{display: 'flex', gap: '12px', width: '100%'}}>
                    <Box style={{
                        width: '28px',
                        height: '28px',
                        backgroundColor: '#10a37f',
                        borderRadius: '4px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                    }}>
                        <span style={{
                            color: 'white',
                            fontSize: '14px',
                            fontWeight: '600'
                        }}>
                            AI
                        </span>
                    </Box>
                    <Box style={{ flex: 1, paddingTop: '2px', minWidth: 0, width: '100%' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            {regularContent && (
                                <div className="markdown-content">
                                    <MarkdownRenderer>
                                        {isStreaming ? displayedMessage : regularContent}
                                    </MarkdownRenderer>
                                </div>
                            )}
                            
                            {isEventStream && eventStreamContent && (
                                <EventStreamComponent eventStream={eventStreamContent} />
                            )}

                            {showTypingIndicator && !isEventStream && (
                                <span style={{
                                    display: 'inline-block',
                                    width: '8px',
                                    height: '16px',
                                    backgroundColor: '#10a37f',
                                    marginLeft: '4px',
                                    animation: 'blink 1s infinite'
                                }} />
                            )}
                        </div>
                    </Box>
                </div>
            </div>
            <style jsx>{`
                @keyframes blink {
                    0%, 50% { opacity: 1; }
                    51%, 100% { opacity: 0; }
                }
                ${markdownStyles}
            `}</style>
        </Box>
    )
}

export default AIMessage