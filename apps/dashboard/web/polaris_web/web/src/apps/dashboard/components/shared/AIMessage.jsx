import { Box, HorizontalStack } from '@shopify/polaris'
import ReactMarkdown from 'react-markdown'
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
        <Box padding={"3"} borderRadius="2" background="bg-subdued">
            <Box maxWidth='800px'>
                <HorizontalStack gap={"3"}>
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
                        <div className="markdown-content">
                            <ReactMarkdown
                                components={{
                                    h1: ({ children }) => <h1 className="markdown-h1">{children}</h1>,
                                    h2: ({ children }) => <h2 className="markdown-h2">{children}</h2>,
                                    h3: ({ children }) => <h3 className="markdown-h3">{children}</h3>,
                                    h4: ({ children }) => <h4 className="markdown-h4">{children}</h4>,
                                    p: ({ children }) => <p className="markdown-p">{children}</p>,
                                    ul: ({ children }) => <ul className="markdown-ul">{children}</ul>,
                                    ol: ({ children }) => <ol className="markdown-ol">{children}</ol>,
                                    li: ({ children }) => <li className="markdown-li">{children}</li>,
                                    code: ({ children, className }) => {
                                        const isInline = !className;
                                        return isInline ? (
                                            <code className="markdown-inline-code">{children}</code>
                                        ) : (
                                            <code className="markdown-code-block">{children}</code>
                                        );
                                    },
                                    pre: ({ children }) => <pre className="markdown-pre">{children}</pre>,
                                    blockquote: ({ children }) => <blockquote className="markdown-blockquote">{children}</blockquote>,
                                    strong: ({ children }) => <strong className="markdown-strong">{children}</strong>,
                                    em: ({ children }) => <em className="markdown-em">{children}</em>,
                                    a: ({ children, href }) => <a href={href} className="markdown-link" target="_blank" rel="noopener noreferrer">{children}</a>
                                }}
                            >
                                {displayedMessage}
                            </ReactMarkdown>
                        </div>
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
                </HorizontalStack>
            </Box>
            <style jsx>{`
                @keyframes blink {
                    0%, 50% { opacity: 1; }
                    51%, 100% { opacity: 0; }
                }
                
                .markdown-content {
                    line-height: 1.6;
                    color: #333;
                }
                
                .markdown-content .markdown-h1 {
                    font-size: 1.5em;
                    font-weight: 700;
                    margin: 1em 0 0.5em 0;
                    color: #1a1a1a;
                    border-bottom: 2px solid #e1e5e9;
                    padding-bottom: 0.3em;
                }
                
                .markdown-content .markdown-h2 {
                    font-size: 1.3em;
                    font-weight: 600;
                    margin: 1em 0 0.5em 0;
                    color: #2c3e50;
                }
                
                .markdown-content .markdown-h3 {
                    font-size: 1.1em;
                    font-weight: 600;
                    margin: 0.8em 0 0.4em 0;
                    color: #34495e;
                }
                
                .markdown-content .markdown-h4 {
                    font-size: 1em;
                    font-weight: 600;
                    margin: 0.6em 0 0.3em 0;
                    color: #34495e;
                }
                
                .markdown-content .markdown-p {
                    margin: 0.5em 0;
                    line-height: 1.6;
                }
                
                .markdown-content .markdown-ul,
                .markdown-content .markdown-ol {
                    margin: 0.5em 0;
                    padding-left: 1.5em;
                }
                
                .markdown-content .markdown-li {
                    margin: 0.2em 0;
                    line-height: 1.5;
                }
                
                .markdown-content .markdown-inline-code {
                    background-color: #f1f3f4;
                    color: #d63384;
                    padding: 0.2em 0.4em;
                    border-radius: 3px;
                    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
                    font-size: 0.9em;
                    border: 1px solid #e1e5e9;
                }
                
                .markdown-content .markdown-pre {
                    background-color: #f8f9fa;
                    border: 1px solid #e1e5e9;
                    border-radius: 6px;
                    padding: 1em;
                    margin: 1em 0;
                    overflow-x: auto;
                    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
                    font-size: 0.9em;
                    line-height: 1.4;
                }
                
                .markdown-content .markdown-code-block {
                    background-color: #f8f9fa;
                    color: #333;
                    padding: 0.2em 0.4em;
                    border-radius: 3px;
                    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
                    font-size: 0.9em;
                }
                
                .markdown-content .markdown-blockquote {
                    border-left: 4px solid #10a37f;
                    margin: 1em 0;
                    padding: 0.5em 1em;
                    background-color: #f8f9fa;
                    color: #555;
                    font-style: italic;
                }
                
                .markdown-content .markdown-strong {
                    font-weight: 700;
                    color: #1a1a1a;
                }
                
                .markdown-content .markdown-em {
                    font-style: italic;
                    color: #555;
                }
                
                .markdown-content .markdown-link {
                    color: #10a37f;
                    text-decoration: none;
                    border-bottom: 1px solid transparent;
                    transition: border-bottom-color 0.2s ease;
                }
                
                .markdown-content .markdown-link:hover {
                    border-bottom-color: #10a37f;
                    text-decoration: none;
                }
                
                /* Emoji styling */
                .markdown-content .emoji {
                    font-size: 1.2em;
                    margin: 0 0.1em;
                }
                
                /* Task list styling */
                .markdown-content input[type="checkbox"] {
                    margin-right: 0.5em;
                }
                
                /* Table styling */
                .markdown-content table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 1em 0;
                }
                
                .markdown-content th,
                .markdown-content td {
                    border: 1px solid #e1e5e9;
                    padding: 0.5em;
                    text-align: left;
                }
                
                .markdown-content th {
                    background-color: #f8f9fa;
                    font-weight: 600;
                }
                
                .markdown-content tr:nth-child(even) {
                    background-color: #f8f9fa;
                }
            `}</style>
        </Box>
    )
}

export default AIMessage