import React from 'react';
import { Button, Text } from '@shopify/polaris';
import { CancelMajor } from '@shopify/polaris-icons';
import { AgentImage } from './AgentImage';
import { Agent } from '../types';

export const AgentHeader = ({ onClose, agent }: { onClose: () => void, agent: Agent | null }) => {
    if (!agent) return null;

    return (
        <div className="py-2 px-4 flex flex-col gap-5 shadow-[0px_-1px_0px_0px_#E1E3E5_inset]">
            <div className="flex justify-between items-center">
                <Text variant="headingMd" as="h1">Member Details</Text>
                <Button icon={CancelMajor} onClick={onClose} plain></Button>
            </div>
            <div className="flex gap-3">
                <AgentImage src={agent.image} alt={agent.name} />
                <div>
                    <Text variant="headingMd" as="h2">{agent.name}</Text>
                    <Text variant="bodySm" as="p" color="subdued">
                        {agent.description}
                    </Text>
                </div>
            </div>
        </div>
    );
}