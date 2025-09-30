import { VerticalStack } from '@shopify/polaris'
import React from 'react'
import ChatInterface from './ChatInterface'
import ChatInput from './ChatInput'

function ChatContainer() {
    return (
        <VerticalStack gap={"2"} >
            <div style={{ maxHeight: '450px', overflowY: 'scroll' }}>
                <ChatInterface conversations={[]} />
            </div>
            <ChatInput onSendMessage={() => {}} />
        </VerticalStack>
    )
}

export default ChatContainer