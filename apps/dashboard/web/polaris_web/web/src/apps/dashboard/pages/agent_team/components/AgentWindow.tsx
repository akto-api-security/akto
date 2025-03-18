import React, { useState } from 'react';
import { PromptComposer } from './PromptComposer';
import { Agent } from '../types';
import { AgentHeader } from './AgentHeader';
import { FindVulnerabilitiesAgent } from '../agents/FindVulnerabilities';
import { Box, Scrollable, VerticalStack } from '@shopify/polaris';
import RepositoryInitializer from './RepositoryInitializer';
import SensitiveDataAgentInitializer from './SensitiveDataAgentInitializer';
import FlyLayout from '../../../components/layouts/FlyLayout';
import SensitiveDataTypeCTA from './finalctas/SensitiveDataTypeCTA';
import ApiGroupAgentInitializer from './ApiGroupAgentInitializer';
import { useAgentsStore } from '../agents.store';
import AgentGroupCTA from './finalctas/AgentGroupCTA';
import APISRequiredCTA from './finalctas/APISRequiredCTA';
import TestFalsePositiveInitializer from './TestFalsePositiveInitializer';
import TestFalsePositiveAgentCTA from './finalctas/TestFalsePositiveAgentCTA';

interface AgentWindowProps {
    agent: Agent | null;
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ agent, onClose, open }: AgentWindowProps) {

    const [finalCTAShow, setFinalCTAShow] = useState(false)
    const { currentProcessId , PRstate} = useAgentsStore()

    const renderAgentWindow = () => {
        switch (agent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <RepositoryInitializer agentType={agent.id}/> : <></>}
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow}/>
                    </VerticalStack>
                )
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <SensitiveDataAgentInitializer agentType={agent.id}/> : <></> }
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow}/>
                    </VerticalStack>
                )
            case 'GROUP_APIS':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <ApiGroupAgentInitializer agentType={agent.id} /> : <></>}
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow} />
                    </VerticalStack>
                )
            case 'FIND_FALSE_POSITIVE':
                return (<VerticalStack gap={"4"}>
                    {(currentProcessId === null || currentProcessId.length === 0) ? <TestFalsePositiveInitializer agentType={agent.id} /> : <></>}
                    <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow} />
                </VerticalStack>)
            default:
                return (<></>)
        }
    }

    function AgentFinalCTA() {
        switch (agent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (PRstate === "4" ? <APISRequiredCTA /> : <></>)
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (<SensitiveDataTypeCTA show={finalCTAShow} setShow={setFinalCTAShow}/>)
            case 'GROUP_APIS':
                return (<AgentGroupCTA show={finalCTAShow} setShow={setFinalCTAShow}/>)
            case 'FIND_FALSE_POSITIVE':
                return (<TestFalsePositiveAgentCTA show={finalCTAShow} setShow={setFinalCTAShow} />)
            default:
                return (<></>)
        }
    }

    const components = [<div>
        <AgentHeader agent={agent} />
        <div className="h-[calc(100vh-172px)] flex flex-col overflow-y-auto px-4 pb-5">
            <div className="flex-1 min-h-0">
                <Scrollable className="h-full">
                    <div className="pt-2 flex flex-col gap-2">
                        <Box paddingBlockEnd={"8"}>
                                {renderAgentWindow()}
                                <AgentFinalCTA />
                        </Box>
                            </div>
                        </Scrollable>
                </div>
            <PromptComposer agentId ={agent?.id} onSend={console.log} />
        </div>
    </div >]

    return (
        <FlyLayout
            show={open}
            setShow={() => { }}
            isHandleClose={true}
            handleClose={onClose}
            title={"Agent Details"}
            components={components}
        />
    )
}

export default AgentWindow;