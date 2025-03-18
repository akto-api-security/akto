import { Box, Button, HorizontalStack, Icon, Text } from '@shopify/polaris';
import { PauseMajor } from '@shopify/polaris-icons';
import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { isBlockingState, useAgentsStore } from '../agents.store';

import './BlockedState.css';

interface BlockedStateProps {
    onResume: () => void;
    onDiscard: () => void;
}

export const BlockedState = ({ onResume, onDiscard }: BlockedStateProps) => {
    const { agentState, setAttemptedInBlockedState, attemptedInBlockedState, setAgentState } = useAgentsStore();

    const handleResume = () => {
        setAgentState('idle');
        onResume();
    }

    const isPaused = agentState === 'paused';
    const isThinking = agentState === 'thinking';

    const show = isPaused || isThinking;

    const handleDiscard = () => {
        setAgentState('idle');
        onDiscard();
    }

    return (
        <AnimatePresence>
            {show && (
                <motion.div
                    initial="initial"
                    animate={attemptedInBlockedState ? "shake" : "visible"}
                    exit="exit"
                    variants={{
                        initial: { opacity: 1, scaleY: 0, transformOrigin: 'bottom' },
                        visible: { opacity: 1, scaleY: 1, transformOrigin: 'bottom' },
                        exit: { opacity: 1, scaleY: 0, transformOrigin: 'bottom' },
                        shake: {
                            x: [0, -10, 10, -10, 10, 0],
                            opacity: 1,
                            scaleY: 1,
                            transition: {
                                duration: 0.4,
                                ease: "easeInOut",
                                onComplete: () => {
                                    setAttemptedInBlockedState(false);
                                }
                            }
                        }
                    }}
                    id="blocked-state-container"
                >
                    <Box
                        background="bg"
                        borderColor="border"
                        paddingBlockStart="2"
                        paddingBlockEnd="2"
                        paddingInlineStart="3"
                        paddingInlineEnd="3"
                        borderRadiusStartEnd="1"
                        borderRadiusStartStart="1"
                        borderWidth="1"
                        id="blocked-state-inner-container"
                    >
                        <HorizontalStack align="space-between">
                        {
                            isPaused && (
                            <>
                                <HorizontalStack align="center">
                                    <Icon source={PauseMajor} color="subdued" />
                                    <Text as="span" variant="bodySm" color="subdued">
                                        Paused (Member is waiting for your response)
                                    </Text>
                                </ HorizontalStack>
                                <HorizontalStack align="center" gap="2">
                                    <Button
                                        onClick={handleDiscard}
                                        plain
                                        monochrome
                                        removeUnderline
                                        size="micro"
                                    >
                                        Discard
                                    </Button>
                                    <Button
                                        onClick={handleResume}
                                        primary
                                        size="micro"
                                    >
                                        Approve
                                    </Button>
                                </HorizontalStack>
                            </>
                        )}
                        {
                            isThinking && (
                                <>
                                    <Text as="span" color="subdued">
                                        Thinking
                                        <span id="thinking-ellipsis">...</span>
                                    </Text>
                                </>
                            )
                        }
                        </HorizontalStack>
                    </Box>
                </motion.div>
            )}
        </AnimatePresence>
    );
};