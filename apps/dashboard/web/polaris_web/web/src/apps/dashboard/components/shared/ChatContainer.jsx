import { VerticalStack } from '@shopify/polaris'
import React, { useState, useEffect, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import ChatInterface from './ChatInterface'
import ChatInput from './ChatInput'
import SessionStore from '../../../main/SessionStore'
import LocalStore from '../../../main/LocalStorageStore'

function ChatContainer() {
    const [conversations, setConversations] = useState([])
    const [conversationId, setConversationId] = useState(null)
    const [isLoading, setIsLoading] = useState(false)
    
    const params = useParams()
    const testId = params.testId
    
    const currentAgentConversationId = SessionStore(state => state.currentAgentConversationId)
    const agentConversation = SessionStore(state => state.agentConversation)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    
    const getCategoryName = useCallback((testId) => {
        if (!testId || !subCategoryMap) return 'Unknown Category'
        
        const subCategory = subCategoryMap[testId]
        if (subCategory) {
            return subCategory.superCategory?.displayName || subCategory.testName || 'Unknown Category'
        }
        return 'Unknown Category'
    }, [subCategoryMap])
    
    // Generate welcome message
    const generateWelcomeMessage = (categoryName) => {
        const username = window.USER_NAME || window.LOGIN
        return `Hey ${username}! You have selected the **${categoryName}** category. I'm here to help you analyze and understand the security aspects of this test category. What would you like to know about it?`
    }
    
    // Initialize conversations
    useEffect(() => {
        const initializeConversations = () => {
            
            const categoryName = getCategoryName(testId)
            
            // Check if we have existing conversation in SessionStore
            if (currentAgentConversationId && agentConversation && Object.keys(agentConversation).length > 0) {
                // Load existing conversation
                const existingConversations = Object.values(agentConversation).sort((a, b) => a.creationTimestamp - b.creationTimestamp)
                setConversations(existingConversations)
                setConversationId(currentAgentConversationId)
            } else {
                // Fresh conversation - generate welcome message
                const welcomeMessage = {
                    _id: `welcome_${Date.now()}`,
                    conversationId: `conv_${Date.now()}`,
                    role: 'system',
                    message: generateWelcomeMessage(categoryName),
                    creationTimestamp: Date.now(),
                    model: 'claude-3-sonnet',
                    isStreaming: true
                }
                
                setConversations([welcomeMessage])
                setConversationId(welcomeMessage.conversationId)
                
                // Store in SessionStore
                SessionStore.getState().setCurrentAgentConversationId(welcomeMessage.conversationId)
                SessionStore.getState().setAgentConversation({
                    [welcomeMessage._id]: welcomeMessage
                })
            }
            
            setIsLoading(false)
        }
        
        initializeConversations()
    }, [currentAgentConversationId, subCategoryMap])
    
    // Handle sending message
    const handleSendMessage = async (messageData) => {
        const { message, model } = messageData
        
        // Add user message to conversations
        const userMessage = {
            _id: `user_${Date.now()}`,
            conversationId: conversationId,
            role: 'user',
            message: message,
            creationTimestamp: Date.now(),
            model: model || 'claude-3-sonnet'
        }
        
        setConversations(prev => [...prev, userMessage])
        
        // Add AI response (placeholder for now - will be replaced with actual API call)
        const aiMessage = {
            _id: `ai_${Date.now()}`,
            conversationId: conversationId,
            role: 'system',
            message: 'This is a placeholder response. The actual API streaming will be implemented here.',
            creationTimestamp: Date.now() + 1,
            model: model || 'claude-3-sonnet',
            isStreaming: true
        }
        
        setConversations(prev => [...prev, aiMessage])
        
        // Update SessionStore
        const updatedConversation = {
            ...agentConversation,
            [userMessage._id]: userMessage,
            [aiMessage._id]: aiMessage
        }
        SessionStore.getState().setAgentConversation(updatedConversation)
        
        // TODO: Implement actual API call for streaming response
        // This will replace the placeholder AI message with streaming content
    }
    
    return (
        <VerticalStack gap={"4"}>
            <div style={{ maxHeight: '450px', overflowY: 'scroll' }}>
                <ChatInterface conversations={conversations} />
            </div>
            <ChatInput onSendMessage={handleSendMessage} isLoading={isLoading}/>
        </VerticalStack>
    )
}

export default ChatContainer