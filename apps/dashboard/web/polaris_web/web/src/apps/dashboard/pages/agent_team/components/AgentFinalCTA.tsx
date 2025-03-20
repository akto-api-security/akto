import React from 'react';
import SensitiveDataTypeCTA from './finalctas/SensitiveDataTypeCTA';
import { useAgentsStore } from '../agents.store';
import AgentGroupCTA from './finalctas/AgentGroupCTA';
import APISRequiredCTA from './finalctas/APISRequiredCTA';
import TestFalsePositiveAgentCTA from './finalctas/TestFalsePositiveAgentCTA';

function AgentFinalCTA() {

    const { PRstate, currentAgent } = useAgentsStore()
    return (() => {
        switch (currentAgent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (PRstate === "4" ? <APISRequiredCTA /> : <></>)
            case 'FIND_SENSITIVE_DATA_TYPES':
                return (<SensitiveDataTypeCTA />)
            case 'GROUP_APIS':
                return (<AgentGroupCTA />)
            case 'FIND_FALSE_POSITIVE':
                return (<TestFalsePositiveAgentCTA />)
            default:
                return (<></>)
        }
    }
    )()
}

export default AgentFinalCTA