import React from 'react';
import { Box, Text } from '@shopify/polaris';
import { AgentImage } from './AgentImage';
import { Agent } from '../types';

export const AgentHeader = ({ agent }: { agent: Agent | null }) => {
    if (!agent) return null;

    return (
        <div>
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