import React from 'react';
import { PromptComposer } from './PromptComposer';
import { AgentHeader } from './AgentHeader';
import { Box, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import FlyLayout from '../../../components/layouts/FlyLayout';
import AgentWindowCore from './AgentWindowCore';
import AgentFinalCTA from './finalctas/AgentFinalCTA';
import ActivityTable from './ActivityTable';
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs';
import { useAgentsStore } from '../agents.store';

interface AgentWindowProps {
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ onClose, open }: AgentWindowProps) {
    const {currentAgent} = useAgentsStore();
    

    const titleComp = (
        <VerticalStack gap={"5"}>
            <Text variant="headingMd" as="p">
                {"Agent Details"}
            </Text>
            <AgentHeader />
        </VerticalStack>
        
    )

    const chatTab = {
        id: 'chat',
        content: 'Chat',
        component:<div>
        <div className="h-[calc(100vh-172px)] flex flex-col overflow-y-auto px-4 pb-5">
            <div className="flex-1 min-h-0">
                <Scrollable className="h-full">
                    <div className="pt-2 flex flex-col gap-2">
                        <Box paddingBlockEnd={"8"}>
                            <AgentWindowCore />
                            <AgentFinalCTA />
                        </Box>
                    </div>
                </Scrollable>
            </div>
            <br/>
            <PromptComposer onSend={console.log} />
        </div>
    </div >
    }
    const activityTab = {
        id: 'activity',
        content: 'Activity',
        component: <ActivityTable agentId={currentAgent?.id}/>
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
            titleComp={titleComp}
            components={components}
            newComp={true}
            variant={"agentVariant"}
        />
    )
}

export default AgentWindow;