import { Button, Icon, Text } from '@shopify/polaris';
import { PauseMajor } from '@shopify/polaris-icons';
import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAgentsStore } from '../agents.store';

interface PausedStateProps {
    onResume: () => void;
    onDiscard: () => void;
}

export const PausedState = ({ onResume, onDiscard }: PausedStateProps) => {
    const { isPaused, resumeAgent, discardPausedState, setAttemptedOnPause, attemptedOnPause } = useAgentsStore();

    const handleResume = () => {
        resumeAgent();
        onResume();
    }

    const handleDiscard = () => {
        discardPausedState();
        onDiscard();
    }

    return (
        <AnimatePresence>
            {isPaused && (
                <motion.div
                    initial="initial"
                    animate={attemptedOnPause ? "shake" : "visible"}
                    exit="exit"
                    variants={{
                        initial: { opacity: 0, y: 10 },
                        visible: { opacity: 1, y: 0 },
                        exit: { opacity: 0, y: 10 },
                        shake: {
                            x: [0, -10, 10, -10, 10, 0],
                            opacity: 1,
                            y: 0,
                            transition: {
                                duration: 0.4,
                                ease: "easeInOut",
                                onComplete: () => {
                                    setAttemptedOnPause(false);
                                }
                            }
                        }
                    }}
                    className="absolute -top-[38px] py-2 px-3 w-[90%] left-1/2 -translate-x-1/2 bg-[var(--agent-grey-background)] border border-[var(--borderShadow-box-shadow)] rounded-t-sm flex justify-between items-center z-[100]"
                >
                    <div className="flex items-center">
                        <Icon source={PauseMajor} color="subdued" />
                        <Text as="span" variant="bodySm" color="subdued">
                            Paused (Member is waiting for your response)
                        </Text>
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
                        <Button 
                            size="micro" 
                            primary 
                            onClick={handleResume}
                        >
                            Approve
                        </Button>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};