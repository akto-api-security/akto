import React, { useEffect } from 'react';
import { useAgentsStore } from './agents.store';
import { Agent, Model } from './types';
import { PromptComposer } from './components/PromptComposer';
import AgentWindow from './components/AgentWindow';
import { Button } from '@shopify/polaris';

const MODELS: Model[] = [
    { id: 'claude-3-sonnet', name: 'Claude-3.5-sonnet' },
    { id: 'gpt-4o', name: 'GPT-4o' },
    { id: 'gpt-4o-mini', name: 'GPT-4o-mini' },
    { id: 'gpt-3.5-turbo', name: 'GPT-3.5-turbo' },
    { id: 'gemini-1.5-flash', name: 'Gemini-1.5-flash' },
];

const AGENTS: Agent[] = [
    {
        id: 'source-code-security-scanner',
        name: 'Source Code Security Scanner',
        description: 'An intelligent member that analyzes your source code for security vulnerabilities by examining authentication mechanisms, API endpoints, and data flow patterns.',
        image: 'https://cdn.hashnode.com/res/hashnode/image/upload/v1740397673511/EYD9JqM0Y.png?auto=format',
    }
]

function AgentTeam() {
    const { setAvailableModels, currentAgent, setCurrentAgent } = useAgentsStore();

    useEffect(() => {
        // Todo: get available models from backend?
        setAvailableModels(MODELS);
    }, []);

    return (
        <div>
            {AGENTS.map((agent) => <Button key={agent.id} onClick={() => setCurrentAgent(agent)}>{agent.name}</Button>)}
            <AgentWindow agent={currentAgent} onClose={() => setCurrentAgent(null)} open={currentAgent !== null} />
        </div>
    )
}

export default AgentTeam;