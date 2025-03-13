import React, { useEffect, useState } from 'react';
import { useAgentsStore } from './agents.store';
import { Agent, Model } from './types';
import AgentWindow from './components/AgentWindow';
import { Button } from '@shopify/polaris';
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards';
import GridRows from '../../components/shared/GridRows';
import AgentRowCard from './AgentRowCard';
import TitleWithInfo from "../../../../apps/dashboard/components/shared/TitleWithInfo"


// TODO: get these models from backend
const MODELS: Model[] = [
    { id: 'claude-3-sonnet', name: 'Claude-3.5-sonnet' },
    { id: 'gpt-4o', name: 'GPT-4o' },
    { id: 'gpt-4o-mini', name: 'GPT-4o-mini' },
    { id: 'gpt-3.5-turbo', name: 'GPT-3.5-turbo' },
    { id: 'gemini-1.5-flash', name: 'Gemini-1.5-flash' },
];

// TODO: get these agents from backend
const AGENTS: Agent[] = [
    {
        id: 'source-code-security-scanner',
        name: 'Source Code Security Scanner',
        description: 'An intelligent member that analyzes your source code for security vulnerabilities by examining authentication mechanisms, API endpoints, and data flow patterns.',
        image: '/public/agents/secret-agent-1.svg',
    },
    {
        id: 'source-code-security-scanner-2',
        name: 'Source Code Security Scanner 2',
        description: 'An intelligent member that analyzes your source code for security vulnerabilities by examining authentication mechanisms, API endpoints, and data flow patterns.',
        image: '/public/agents/secret-agent-2.svg',
    },
    {
        id: 'FIND_SENSITIVE_DATA_TYPES',
        name: 'Sensitive data type scanner',
        description: 'An intelligent member that analyzes your APIs for sensitive data types.',
        image: '/public/agents/secret-agent-3.svg',
    },
    {
        id: 'FIND_VULNERABILITIES_FROM_SOURCE_CODE',
        name: 'Find Vulnerabilities from Source Code',
        description: 'An intelligent member that analyzes your source code for security vulnerabilities by examining authentication mechanisms, API endpoints, and data flow patterns.',
        image: '/public/agents/secret-agent-4.svg',
    },
    {
        id: 'FIND_APIS_FROM_SOURCE_CODE',
        name: 'Find APIs from Source Code',
        description: 'An intelligent member that analyzes your source code for API endpoints and request response schema.',
        image: '/public/agents/secret-agent-5.svg',
    },
]

function AgentTeam() {
    const { setAvailableModels, currentAgent, setCurrentAgent } = useAgentsStore();

    useEffect(() => {
        // Todo: get available models from backend?

        // TODO: implement these API calls
        // api.getMemberAgents().then((res: { agents: any; }) => {
        //     if(res && res.agents){
        //         console.log(res?.agents)

        //         let agents = res.agents
        //     }
        // })

        // api.getAgentModels().then((res: { models: any; }) => {
        //     if(res && res.models){
        //         console.log(res?.models)
        //     }
        // })

        setAvailableModels(MODELS);

    }, []);

    const [newCol, setNewCol] = useState(0)

    const closeAction = () => {
        setCurrentAgent(null)
        setNewCol(0)
        setShowAgentWindow(false)
    }

    const onButtonClick = (agent: Agent | null ) => {
        setNewCol(1)
        setCurrentAgent(agent)
        setShowAgentWindow(true)
    }

    const agents = (
        <GridRows CardComponent={AgentRowCard} columns="3"
            items={AGENTS}
            onButtonClick={onButtonClick}
            cardType="AGENT"
            changedColumns={newCol}
        />
    )

    const pageComponents = [agents]

    const [showAgentWindow, setShowAgentWindow] = useState(false)

    return (
        <>
            <PageWithMultipleCards
                components={pageComponents}
                isFirstPage={true}
                divider={true}
                title={
                    <TitleWithInfo
                        tooltipContent={"These are AI agents that can be used to provide insights"}
                        titleText={"Members"}
                        // TODO: implement docsUrl functionality
                        docsUrl={"https://docs.akto.io"}
                    />
                }
                secondaryActions={[
                    // TODO: implement Knowledge base functionality
                    <Button id={"Knowledge-base"} onClick={() => {}}>Knowledge base</Button>
                ]}
            />
            <AgentWindow agent={currentAgent} onClose={closeAction} open={showAgentWindow} />
        </>
    )
}

export default AgentTeam;