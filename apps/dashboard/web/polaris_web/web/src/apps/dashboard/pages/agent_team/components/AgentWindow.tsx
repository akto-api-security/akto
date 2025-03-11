import React from 'react';
import { PromptComposer } from './PromptComposer';
import { Agent } from '../types';
import { AgentHeader } from './AgentHeader';
import { FindVulnerabilitiesAgent } from '../agents/FindVulnerabilities';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import RepositoryInitializer from './RepositoryInitializer';
import SensitiveDataAgentInitializer from './SensitiveDataAgentInitializer';
import FlyLayout from '../../../components/layouts/FlyLayout';

interface AgentWindowProps {
    agent: Agent | null;
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ agent, onClose, open }: AgentWindowProps) {
    const renderAgentWindow = () => {
        switch (agent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (
                    <VerticalStack gap={"4"}>
                        <RepositoryInitializer />
                        <FindVulnerabilitiesAgent />
                    </VerticalStack>
                )
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (
                    <VerticalStack gap={"4"}>
                        <SensitiveDataAgentInitializer />
                        <FindVulnerabilitiesAgent />
                    </VerticalStack>
                )
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
                        {renderAgentWindow()}
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