import React from 'react';
import { FindVulnerabilitiesAgent } from '../agents/FindVulnerabilities';
import { VerticalStack } from '@shopify/polaris';
import RepositoryInitializer from './RepositoryInitializer';
import SensitiveDataAgentInitializer from './SensitiveDataAgentInitializer';
import ApiGroupAgentInitializer from './ApiGroupAgentInitializer';
import { useAgentsStore } from '../agents.store';
import TestFalsePositiveInitializer from './TestFalsePositiveInitializer';
import SpinnerCentered from "../../../components/progress/SpinnerCentered";

function AgentWindowCore() {
    const { currentProcessId, currentAgent } = useAgentsStore()

    return (() => {
        switch (currentAgent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <RepositoryInitializer agentType={currentAgent.id} /> : <></>}
                        <FindVulnerabilitiesAgent/>
                    </VerticalStack>
                )
            case 'FIND_APIS_FROM_SOURCE_CODE':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <RepositoryInitializer agentType={currentAgent.id} /> : <></>}
                        <FindVulnerabilitiesAgent/>
                    </VerticalStack>
                )
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <SensitiveDataAgentInitializer agentType={currentAgent.id} /> : <></>}
                        <FindVulnerabilitiesAgent/>
                    </VerticalStack>
                )
            case 'GROUP_APIS':
                return (
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <ApiGroupAgentInitializer agentType={currentAgent.id} /> : <></>}
                        <FindVulnerabilitiesAgent/>
                    </VerticalStack>
                )
            case 'FIND_FALSE_POSITIVE':
                return (<VerticalStack gap={"4"}>
                    {(currentProcessId === null || currentProcessId.length === 0) ? <TestFalsePositiveInitializer agentType={currentAgent.id} /> : <></>}
                    <FindVulnerabilitiesAgent/>
                </VerticalStack>)
            default:
                return (<SpinnerCentered />)
        }
    })()
}

export default AgentWindowCore