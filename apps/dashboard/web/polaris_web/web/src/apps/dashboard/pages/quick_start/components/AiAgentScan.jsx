import { Box, Button, ButtonGroup, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import { useState } from 'react'
import InformationBannerComponent from './shared/InformationBannerComponent';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import api from '../api';
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown';

const AiAgentScan = () => {
    const [loading, setLoading] = useState(false)
    
    const [openaiUrl, setOpenaiUrl] = useState('https://api.openai.com/v1')
    const [openaiApiKey, setOpenaiApiKey] = useState('')
    const [model, setModel] = useState('gpt-4o-mini')
    const [maxRequests, setMaxRequests] = useState('100')

    const goToDocs = () => {
        window.open("https://docs.akto.io/ai-agent-scan")
    }

    const primaryAction = () => {
        if(openaiUrl?.length == 0 || openaiUrl == undefined) {
            func.setToast(true, true, "Please enter a valid OpenAI URL.")
            return
        }

        if(openaiApiKey?.length == 0 || openaiApiKey == undefined) {
            func.setToast(true, true, "Please enter a valid OpenAI API Key.")
            return
        }

        const maxRequestsNum = parseInt(maxRequests)
        if(isNaN(maxRequestsNum) || maxRequestsNum < 1 || maxRequestsNum > 10000) {
            func.setToast(true, true, "Max requests must be between 1 and 10000.")
            return
        }

        setLoading(true)
        api.initiateAiAgentImport(openaiUrl, openaiApiKey, model, maxRequestsNum, window.location.origin).then(() => {
            func.setToast(true, false, "AI Agent Scan initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating AI agent scan:", err)
            func.setToast(true, true, "Failed to initiate AI Agent Scan. Please check your configuration.")
        }).finally(() => {
            setLoading(false)
            setOpenaiUrl('https://api.openai.com/v1')
            setOpenaiApiKey('')
            setModel('gpt-4o-mini')
            setMaxRequests('100')
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use AI agents to automatically explore and scan your application. The AI agent will intelligently navigate your site, 
                discover APIs, and send the captured traffic to your dashboard for real-time analysis.
            </Text>

            <InformationBannerComponent docsUrl="https://docs.akto.io/ai-agent-scan"
                    content="Configure your OpenAI API key and model to enable AI-powered scanning " 
            />

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField 
                    label="OpenAI API URL" 
                    value={openaiUrl} 
                    type='url' 
                    onChange={(value) => setOpenaiUrl(value)} 
                    placeholder='https://api.openai.com/v1'
                    helpText="Enter your OpenAI API endpoint URL. For OpenAI, use the default. For local models, enter your local endpoint."
                />
                
                <PasswordTextField 
                    label="OpenAI API Key" 
                    setField={setOpenaiApiKey} 
                    onFunc={true} 
                    field={openaiApiKey}
                    placeholder='sk-...'
                />
                
                <Dropdown
                    label="Model"
                    menuItems={[
                        { value: "gpt-4o", label: "GPT-4o", id: "gpt-4o" },
                        { value: "gpt-4o-mini", label: "GPT-4o Mini", id: "gpt-4o-mini" },
                        { value: "gpt-4-turbo", label: "GPT-4 Turbo", id: "gpt-4-turbo" },
                        { value: "gpt-3.5-turbo", label: "GPT-3.5 Turbo", id: "gpt-3.5-turbo" },
                        { value: "ollama/llama3.2", label: "Ollama - Llama 3.2", id: "ollama-llama3.2" },
                        { value: "ollama/qwen2.5-coder", label: "Ollama - Qwen 2.5 Coder", id: "ollama-qwen2.5-coder" },
                    ]}
                    initial={model}
                    selected={(val) => setModel(val)}
                />

                <TextField 
                    label="Max Requests" 
                    value={maxRequests} 
                    type='number' 
                    onChange={(value) => setMaxRequests(value)} 
                    placeholder='100'
                    helpText="Maximum number of requests the AI agent will make (1-10000)"
                />
                
                <div style={{height:"20px"}}></div>

                <ButtonGroup>
                    <Button 
                        onClick={primaryAction} 
                        primary 
                        disabled={openaiUrl?.length == 0 || openaiApiKey?.length == 0} 
                        loading={loading}
                    >
                        Start AI Scan
                    </Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default AiAgentScan