import React, { useState } from 'react';
import { PromptComposer } from './PromptComposer';
import { Agent } from '../types';
import { AgentHeader } from './AgentHeader';
import { FindVulnerabilitiesAgent } from '../agents/FindVulnerabilities';
import { Box, Divider, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import RepositoryInitializer from './RepositoryInitializer';
import SensitiveDataAgentInitializer from './SensitiveDataAgentInitializer';
import FlyLayout from '../../../components/layouts/FlyLayout';
import SensitiveDataTypeCTA from './finalctas/SensitiveDataTypeCTA';
import ApiGroupAgentInitializer from './ApiGroupAgentInitializer';
import { useAgentsStore } from '../agents.store';
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs';


interface AgentWindowProps {
    agent: Agent | null;
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ agent, onClose, open }: AgentWindowProps) {

    const [finalCTAShow, setFinalCTAShow] = useState(false)
    const { currentProcessId } = useAgentsStore()

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
                return(
                    <VerticalStack gap={"4"}>
                        {(currentProcessId === null || currentProcessId.length === 0) ? <ApiGroupAgentInitializer agentType={agent.id}/> : <></> }
                        <FindVulnerabilitiesAgent agentId={agent.id} finalCTAShow={finalCTAShow} setFinalCTAShow={setFinalCTAShow}/>
                    </VerticalStack>
                )
            case 'FIND_FALSE_POSITIVE':
                return (<></>)
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
            case 'GROUP_APIS':
                return (<></>)
            case 'FIND_FALSE_POSITIVE':
                return (<></>)
            default:
                return (<></>)
        }
    }

    const titleComp = (
        <VerticalStack gap={"5"}>
            <Text variant="headingMd" as="p">
                {"Agent Details"}
            </Text>
            <AgentHeader agent={agent} />
        </VerticalStack>
        
    )

    const chatTab = {
        id: 'chat',
        content: 'Chat',
        component:<div className="h-[calc(100vh-172px)] flex flex-col px-4 pb-5">
        <div className="flex-1 min-h-0">
                <div className="pt-2 flex flex-col gap-2">
                    <Box paddingBlockEnd={"8"}>
                            {renderAgentWindow()}
                            <AgentFinalCTA />
                    </Box>
                        </div>
            </div>
            <br/><br/><br/>
        <PromptComposer agentId ={agent?.id} onSend={console.log} />
    </div>
    }
    const activityTab = {
        id: 'activity',
        content: 'Activity',
        component: <div>Activity</div>
    }

    const components = [
        <LayoutWithTabs
            key="tabs"
            tabs={[chatTab, activityTab]}
            currTab={() => { }}
            disabledTabs={[]}
        />,<Box paddingBlockEnd={"4"}></Box>]

    return (
        <FlyLayout
            show={open}
            setShow={() => { }}
            isHandleClose={true}
            handleClose={onClose}
            // title={"Agent Details"}
            titleComp={titleComp}
            components={components}
            newComp={true}
            variant={"agentFootterVariant"}
        />
    )
}

export default AgentWindow;