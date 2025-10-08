import { Box } from '@shopify/polaris'
import UserMessage from './UserMessage'
import AIMessage from './AIMessage'

function ChatInterface({ conversations, sort = true }) {
    if (!conversations || conversations.length === 0) {
        return <Box style={{ height: '400px', backgroundColor: '#ffffff' }} />
    }

    let sortedConversations = [...conversations]
    if(sort){
       sortedConversations = sortedConversations.sort((a, b) => a.creationTimestamp - b.creationTimestamp)
    }

    return (
        <Box style={{ backgroundColor: '#ffffff', maxHeight: '70vh', overflowY: 'auto' }}>
            {sortedConversations.map((conversation) => (
                conversation.role === 'user' ? (
                    <UserMessage key={conversation._id} message={conversation.message} />
                ) : (
                    <AIMessage 
                        key={conversation._id} 
                        message={conversation.message} 
                        isStreaming={conversation.isStreaming}
                    />
                )
            ))}
        </Box>
    )
}

export default ChatInterface