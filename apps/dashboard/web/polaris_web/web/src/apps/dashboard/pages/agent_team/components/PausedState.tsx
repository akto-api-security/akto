import { Button, Icon, Text } from '@shopify/polaris';
import { PauseMajor } from '@shopify/polaris-icons';
import React from 'react';
import { useAgentsStore } from '../agents.store';
interface PausedStateProps {
    onResume: () => void;
    onDiscard: () => void;
}

export const PausedState = ({ onResume, onDiscard }: PausedStateProps) => {
    const { isPaused, resumeAgent, discardPausedState } = useAgentsStore();

    const handleResume = () => {
        resumeAgent();
        onResume();
    }

    const handleDiscard = () => {
        discardPausedState();
        onDiscard();
    }

    if (!isPaused) return null;

    return (
        <div className="absolute -top-[38px] py-2 px-3 w-[90%] left-1/2 -translate-x-1/2 bg-[#F6F6F7] border border-[#AEB4B9] rounded-t-sm flex justify-between items-center">
        <div className="flex items-center">
            <Icon source={PauseMajor} color="subdued" />
            <Text as="span" variant="bodySm" color="subdued">Paused (Member is waiting for your response)</Text>
        </div>
        <div className="flex items-center gap-2">
            <Button
                size="micro"
                plain
                destructive
                removeUnderline
                monochrome
                onClick={handleDiscard}
            >
                Discard
            </Button>
            <Button size="micro" primary onClick={handleResume}>Resume</Button>
        </div>
        </div>
    )
}