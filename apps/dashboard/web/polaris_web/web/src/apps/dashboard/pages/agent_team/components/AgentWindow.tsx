import React from 'react';
import { Drawer } from 'vaul';
import { PromptComposer } from './PromptComposer';
import { Agent } from '../types';
import { AgentHeader } from './AgentHeader';
import { useAgentsStore } from '../agents.store';
import { Button, Scrollable } from '@shopify/polaris';

interface AgentWindowProps {
    agent: Agent | null;
    onClose: () => void;
    open: boolean;
}

function AgentWindow({ agent, onClose, open }: AgentWindowProps) {
    const { agentState, setAgentState } = useAgentsStore();
    return (
        <Drawer.Root open={open} direction="right" dismissible={false}>
            <Drawer.Portal>
                <Drawer.Overlay className="fixed inset-0 bg-black/40" />
                <Drawer.Content className="fixed inset-y-0 right-0 w-[50vw] min-w-[600px] bg-white z-[400] h-[calc(100vh-56px)] top-[56px]">
                    <div className="flex flex-col h-full">
                        <AgentHeader onClose={onClose} agent={agent} />
                        <div className="h-[calc(100vh-172px)] flex flex-col overflow-y-auto px-4 pb-5">
                            <div className="flex-1 min-h-0">
                                <Scrollable className="h-full">
                                   {/* To implement */}
                                   <Button onClick={() => setAgentState('paused')}>{agentState === 'paused' ? 'Paused' : 'Test Pause'}</Button>
                                   <Button onClick={() => setAgentState(agentState === 'thinking' ? 'idle' : 'thinking')}>{agentState === 'thinking' ? 'Resume' : 'Test Thinking'}</Button>
                                </Scrollable>
                            </div>
                            <PromptComposer onSend={console.log} />
                        </div>
                    </div>
                </Drawer.Content>
            </Drawer.Portal>
        </Drawer.Root>
    )
}

export default AgentWindow;