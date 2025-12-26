import React from 'react'
import { ArrowUpMinor } from '@shopify/polaris-icons'
import './AskAktoChatInput.css'

function AskAktoChatInput({ 
    value, 
    onChange, 
    onSubmit, 
    placeholder = "How can I help you today?",
    disabled = false 
}) {


    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSubmit()
        }
    }

    const handleSubmit = () => {
        if (!value.trim()) return
        onSubmit(value, [])
    }

    return (
        <div className="akto-chat-input-main">
            <div className="akto-chat-input-wrapper">
                <input
                    type="text"
                    className="akto-main-chat-input"
                    value={value}
                    onChange={onChange}
                    onKeyPress={handleKeyPress}
                    placeholder={placeholder}
                    disabled={disabled}
                />
            </div>
            <button
                className="akto-send-button"
                onClick={handleSubmit}
                disabled={!value.trim() || disabled}
                title="Send message"
            >
                <ArrowUpMinor className="akto-send-icon" />
            </button>
        </div>
    )
}

export default AskAktoChatInput