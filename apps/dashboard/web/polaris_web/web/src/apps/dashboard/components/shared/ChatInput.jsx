import { Button, TextField } from '@shopify/polaris'
import { SendMajor } from '@shopify/polaris-icons'
import { useState } from 'react'
import { ModelPicker } from '../../pages/agent_team/components/ModelPicker'
import { availableModels } from '../../pages/testing/sampleConversations'

function ChatInput({ 
    onSendMessage
}) {
    const [currentPrompt, setCurrentPrompt] = useState('')

    const [currentModel, setCurrentModel] = useState(null)
    const [loading, setLoading] = useState(false)

    const handleSubmit = () => {
        setLoading(true)
        onSendMessage({
            message: currentPrompt.trim(),
            model: currentModel,
            prompt: currentPrompt
        })
        setCurrentPrompt('')
        setTimeout(() => {
            setLoading(false)
        }, 1000)
    }

    const handleKeyPress = (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            handleSubmit()
        }
    }

    const modelPicker = (
        <ModelPicker availableModels={availableModels} selectedModel={currentModel} setSelectedModel={setCurrentModel} />
    )

    return (
        <div className='input-gpt'>
            <TextField 
                suffix={
                    <Button plain onClick={() => handleSubmit()} onKeyPress={handleKeyPress} icon={SendMajor} loading={loading}/>
                } 
                placeholder={"Chat with the agent..."}
                onChange={(value) => setCurrentPrompt(value)}
                value={currentPrompt}
                prefix={modelPicker}
            />
        </div>
    )
}

export default ChatInput
