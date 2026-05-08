import { Button, TextField } from '@shopify/polaris'
import { SendMajor } from '@shopify/polaris-icons'
import { useState } from 'react'
import { availableModels } from '../../pages/testing/sampleConversations'
import Dropdown from '../layouts/Dropdown'

function ChatInput({ 
    onSendMessage,
    isLoading
}) {
    const [currentPrompt, setCurrentPrompt] = useState('')

    const [currentModel, setCurrentModel] = useState(availableModels[0].id)
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

    const getValue = (val) => {
        return availableModels.filter(c => c.id === val)[0].name
    }

    const modelPicker = (
        <div className='input-dropdown'>
            <Dropdown
                menuItems={availableModels.map((val) => {return {label: val.name, value: val.id}})}
                initial={currentModel}
                selected={setCurrentModel}
                value={getValue(currentModel)}
            />
        </div>

    )

    return (
        <div className='input-gpt'>
            <TextField 
                suffix={
                    <Button plain onClick={() => handleSubmit()} onKeyPress={handleKeyPress} icon={SendMajor} loading={loading || isLoading}/>
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
