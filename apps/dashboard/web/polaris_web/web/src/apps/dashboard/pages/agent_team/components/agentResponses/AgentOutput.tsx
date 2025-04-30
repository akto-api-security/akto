import React from "react"
import { useAgentsStore } from "../../agents.store";
import ApiGroupOutput from "./ApiGroupOutput";

function AgentOutput() {
    const { currentAgent } = useAgentsStore();

    return (() => {
        switch (currentAgent?.id) {
            case 'GROUP_APIS':
                return <ApiGroupOutput />
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
            case 'FIND_SENSITIVE_DATA_TYPES':
            case 'FIND_FALSE_POSITIVE':
            default:
                return <></>
        }
    })()
}

export default AgentOutput