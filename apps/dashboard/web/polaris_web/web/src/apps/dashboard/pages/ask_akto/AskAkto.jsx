import React, { useState, useEffect } from 'react'
import { SearchMinor } from '@shopify/polaris-icons'
import { useNavigate } from 'react-router-dom'
import AskAktoChatInput from '../../components/shared/AskAktoChatInput'
import SuggestionsComponent from '../../components/shared/SuggestionsComponent'
import HistorySidebar from '../../components/shared/HistorySidebar'
import SessionStore from '../../../main/SessionStore'
import Store from '../../../dashboard/store'
import './AskAkto.css'

function AskAkto() {
    const navigate = useNavigate()
    const [inputValue, setInputValue] = useState('')
    const [conversations, setConversations] = useState([])
    const [showHistory, setShowHistory] = useState(false)
    
    const agentConversation = SessionStore(state => state.agentConversation)
    const username = Store((state) => state.username)

    // Function to format username: extract part before @ and capitalize first letter
    const formatUsername = (username) => {
        if (!username) return 'User'
        
        // If username contains @, take the part before @
        const nameBeforeAt = username.includes('@') ? username.split('@')[0] : username
        
        // Capitalize the first letter
        return nameBeforeAt.charAt(0).toUpperCase() + nameBeforeAt.slice(1).toLowerCase()
    }

    // Sample history data matching the screenshot exactly
    const historyData = [
        {
            id: 1,
            title: "Validate agent resilience to instruction override attacks",
            date: "1 day ago"
        },
        {
            id: 2,
            title: "Prompt injection risks across agents",
            date: "1 day ago"
        },
        {
            id: 3,
            title: "Investigating agent behavior",
            date: "1 day ago"
        }
    ]

    useEffect(() => {
        if (agentConversation && Object.keys(agentConversation).length > 0) {
            const conversationList = Object.values(agentConversation)
                .sort((a, b) => b.creationTimestamp - a.creationTimestamp)
            setConversations(conversationList)
        }
    }, [agentConversation])

    const suggestions = [
        "Help me understand what kind of guardrails should I use?",
        "Summarize my agentic security posture in one view", 
        "What attacks should I red team against my agents first?"
    ]

    const handleSuggestionClick = (suggestion) => {
        // Navigate directly to conversation page with the suggestion
        navigate('/dashboard/ask-akto/conversation', {
            state: {
                message: suggestion,
                attachments: []
            }
        })
    }

    const handleInputChange = (e) => {
        setInputValue(e.target.value)
    }

    const handleSubmitMessage = (message, attachments) => {
        if (!message.trim() && attachments.length === 0) return
        
        // Navigate to conversation page with the message
        navigate('/dashboard/ask-akto/conversation', {
            state: {
                message: message,
                attachments: attachments
            }
        })
    }

    const handleHistoryClick = (historyItem) => {
        setInputValue(historyItem.title)
    }

    const handleViewAllClick = () => {
        setShowHistory(true)
    }

    return (
        <div className="ask-akto-container">
            <div className="ask-akto-main">
                <div className="ask-akto-welcome">
                    {/* Header with Akto logo and greeting */}
                    <div className="ask-akto-header">
                        <div className="greeting">
                            <img src="/public/akto_colored.svg" alt="Akto Logo" className="akto-logo-image" />
                            Hi {formatUsername(username)}, Welcome back!
                        </div>
                    </div>

                    {/* Reusable chat input component */}
                    <AskAktoChatInput 
                        value={inputValue}
                        onChange={handleInputChange}
                        onSubmit={handleSubmitMessage}
                        placeholder="How can I help you today?"
                    />

                    {/* Use reusable suggestions component */}
                    <SuggestionsComponent 
                        suggestions={suggestions}
                        onSuggestionClick={handleSuggestionClick}
                    />

                    {/* History section with 3 horizontal tiles */}
                    <div className="ask-akto-history">
                        <div className="history-header">
                            <div className="history-title">History</div>
                            <button className="view-all-button" onClick={handleViewAllClick}>
                                View all
                            </button>
                        </div>
                        
                        <div className="history-items">
                            {historyData.map((item) => (
                                <div
                                    key={item.id}
                                    className="history-item"
                                    onClick={() => handleHistoryClick(item)}
                                >
                                    <div className="history-item-title">
                                        {item.title}
                                    </div>
                                    <div className="history-item-date">
                                        {item.date}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* History Sidebar */}
            <HistorySidebar 
                isOpen={showHistory}
                onClose={() => setShowHistory(false)}
                onHistoryItemClick={(item) => {
                    setInputValue(item.title)
                    setShowHistory(false)
                }}
            />
        </div>
    )
}

export default AskAkto