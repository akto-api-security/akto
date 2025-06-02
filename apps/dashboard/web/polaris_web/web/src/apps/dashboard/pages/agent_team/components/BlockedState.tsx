import { Button, HorizontalStack, Icon, Text } from '@shopify/polaris';
import { PauseMajor, StopMajor } from '@shopify/polaris-icons';
import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAgentsStore } from '../agents.store';

import './BlockedState.css';
import transform from '../transform';

interface BlockedStateProps {
    onResume: () => void;
    onDiscard: () => void;
}

export const BlockedState = ({ onResume, onDiscard }: BlockedStateProps) => {
    const { agentState, setAttemptedInBlockedState, attemptedInBlockedState, setAgentState, currentAgent, PRstate } = useAgentsStore();

    const handleResume = () => {
        transform.updateAgentState("idle", currentAgent?.id??"", setAgentState);
        onResume();
    }

    const isPaused = agentState === 'paused';
    const isThinking = agentState === 'thinking';
    const isError = agentState === 'error';

    const show = isPaused || isThinking || isError;

    const handleDiscard = () => {
        transform.updateAgentState("idle", currentAgent?.id??"", setAgentState);
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
                    className="absolute min-h-[40px] -top-[40px] py-2 px-3 w-[90%] left-1/2 -translate-x-1/2 bg-[var(--agent-grey-background)] border border-[var(--borderShadow-box-shadow)] rounded-t-sm flex justify-between items-center z-[100]"
                >
                    {
                        isPaused && (
                            <>
                                <div className="flex items-center">
                                    <Icon source={PauseMajor} color="subdued" />
                                    <Text as="span" variant="bodySm" color="subdued">
                                        Paused (Member is waiting for your response)
                                    </Text>
                                </div>
                                <HorizontalStack gap="2">
                                    <Button size="micro" onClick={handleDiscard}>Discard</Button>
                                    <Button disabled={PRstate !== "-1"} size="micro" primary onClick={handleResume}>Approve</Button>
                                </HorizontalStack>
                            </>
                    )}
                    {
                        isThinking && (
                            <>
                                <Text as="span" variant="bodySm" color="subdued">
                                    Thinking
                                    <span className="inline-block animate-[ellipsis_1s_steps(4,end)_infinite]">...</span>
                                </Text>
                            </>
                        )
                    }
                    {
                        isError && (
                            <>
                                <div className="flex items-center">
                                    <Icon source={StopMajor} color="critical" />
                                    <Text as="span" variant="bodySm" color="subdued">
                                    Agent has stopped unexpectedly. Waiting for the Agent to come back online
                                    <span className="inline-block animate-[ellipsis_1s_steps(4,end)_infinite]">...</span>
                                    </Text>
                                </div>
                            </>
                        )
                    }
                </motion.div>
            )}
        </AnimatePresence>
    );
};