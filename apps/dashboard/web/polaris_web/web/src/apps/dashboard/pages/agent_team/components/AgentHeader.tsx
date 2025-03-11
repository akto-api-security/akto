import React from 'react';
import { Text } from '@shopify/polaris';
import { AgentImage } from './AgentImage';
import { Agent } from '../types';

export const AgentHeader = ({ agent }: { agent: Agent | null }) => {
    if (!agent) return null;

    return (
        <div className="py-2 px-4 flex flex-col gap-5 shadow-[0px_-1px_0px_0px_#E1E3E5_inset]">
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