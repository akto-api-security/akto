import { Box } from '@shopify/polaris'
import Markdown from 'react-markdown'
import { useState, useEffect } from 'react'

function AIMessage({ message, isStreaming = false }) {
    const [displayedMessage, setDisplayedMessage] = useState('')
    const [showTypingIndicator, setShowTypingIndicator] = useState(false)

    useEffect(() => {
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
        <Box style={{
            backgroundColor: '#f8f9fa',
            padding: '20px'
        }}>
            <Box style={{ maxWidth: '800px', margin: '0 auto' }}>
                <Box style={{ display: 'flex', gap: '12px' }}>
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
                    <Box style={{ flex: 1, paddingTop: '2px' }}>
                        <Markdown>{displayedMessage}</Markdown>
                        {showTypingIndicator && (
                            <span style={{
                                display: 'inline-block',
                                width: '8px',
                                height: '16px',
                                backgroundColor: '#10a37f',
                                marginLeft: '4px',
                                animation: 'blink 1s infinite'
                            }} />
                        )}
                    </Box>
                </Box>
            </Box>
            <style jsx>{`
                @keyframes blink {
                    0%, 50% { opacity: 1; }
                    51%, 100% { opacity: 0; }
                }
            `}</style>
        </Box>
    )
}

export default AIMessage