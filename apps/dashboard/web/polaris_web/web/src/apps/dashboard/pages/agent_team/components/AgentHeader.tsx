import React from 'react';
import { Text } from '@shopify/polaris';
import { AgentImage } from './AgentImage';
import { useAgentsStore } from '../agents.store';

export const AgentHeader = () => {
    const { currentAgent } = useAgentsStore();
    if (!currentAgent) return <></>;

    return (
        <div>
            <div className="flex gap-3">
                <AgentImage src={currentAgent.image} alt={currentAgent.name} />
                <div>
                    <Text variant="headingMd" as="h2">{currentAgent.name}</Text>
                    <Text variant="bodySm" as="p" color="subdued">
                        {currentAgent.description}
                    </Text>
                </div>
            </div>
            
        </div>
    );
}