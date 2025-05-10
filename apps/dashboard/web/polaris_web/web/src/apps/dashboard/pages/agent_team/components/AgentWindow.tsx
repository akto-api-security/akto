import React from 'react';
import { PromptComposer } from './PromptComposer';
import { AgentHeader } from './AgentHeader';
import { Box, Button, Scrollable, VerticalStack } from '@shopify/polaris';
import FlyLayout from '../../../components/layouts/FlyLayout';
import AgentWindowCore from './AgentWindowCore';
import AgentFinalCTA from './finalctas/AgentFinalCTA';

interface AgentWindowProps {
    onClose: () => void;
    open: boolean;
    showConfigCTA: boolean;
}

function ConfigureAgentCTA() {
    return (
        <Box padding={"32"}>
            <VerticalStack gap={"4"}>
                To configure agents, please click on the button below.
                <Button onClick={() => window.open("/dashboard/settings/integrations/agents", "_self")} primary >Configure</Button>
            </VerticalStack>
        </Box>
    )
}

function AgentWindow({ onClose, open, showConfigCTA }: AgentWindowProps) {

    return (
        <FlyLayout
            show={open}
            setShow={() => { }}
            isHandleClose={true}
            handleClose={onClose}
            title={"Agent Details"}
            components={[
                <div>
                    <AgentHeader />
                    {
                        showConfigCTA ? <ConfigureAgentCTA /> :
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
                                <br />
                                <div style={{ height: '24px' }} />
                                <PromptComposer onSend={console.log} />
                            </div>
                    }
                </div >
            ]}
        />
    )
}

export default AgentWindow;