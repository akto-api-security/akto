import { Icon, Text } from '@shopify/polaris';
import { PauseMajor, StopMajor } from '@shopify/polaris-icons';
import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAgentsStore } from '../agents.store';

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
    const isError = agentState === 'error';

    const show = isPaused || isThinking || isError;

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
                    className="absolute min-h-[38px] -top-[38px] py-2 px-3 w-[90%] left-1/2 -translate-x-1/2 bg-[var(--agent-grey-background)] border border-[var(--borderShadow-box-shadow)] rounded-t-sm flex justify-between items-center z-[100]"
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
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={handleDiscard}
                                        id="discard-button"
                                    >
                                        Discard
                                    </button>
                                    <button
                                        onClick={handleResume}
                                        id="approve-button"
                                        className="hover:bg-[var(--akto-primary)/80] cursor-pointer"
                                    >
                                        Approve
                                    </button>
                                </div>
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