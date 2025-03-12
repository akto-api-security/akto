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

interface AgentWindowProps {
    agent: Agent | null;
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ agent, onClose, open }: AgentWindowProps) {

    const [finalCTAShow, setFinalCTAShow] = useState(false)

    const renderAgentWindow = () => {
        switch (agent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (
                    <VerticalStack gap={"4"}>
                        <RepositoryInitializer agentType={agent.id}/>
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow}/>
                    </VerticalStack>
                )
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (
                    <VerticalStack gap={"4"}>
                        <SensitiveDataAgentInitializer agentType={agent.id}/>
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow}/>
                    </VerticalStack>
                )
            default:
                return (<></>)
        }
    }

    function AgentFinalCTA() {
        switch (agent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (<></>)
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (<SensitiveDataTypeCTA show={finalCTAShow} setShow={setFinalCTAShow}/>)
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
            <PromptComposer onSend={console.log} />
        </div>
    </div >]

    return (
        <FlyLayout
            show={open}
            setShow={() => { }}
            isHandleClose={true}
            handleClose={onClose}
            title={"Member Details"}
            components={components}
        />
    )
}

export default AgentWindow;