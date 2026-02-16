import { useState, useEffect, useRef } from 'react';
import { Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import '../AgenticConversationPage.css';

const THINKING_MESSAGES = [
    'thinking', 'pondering', 'brainstorming', 'contemplating', 'mulling over',
    'processing thoughts', 'analyzing', 'considering', 'reflecting', 'wondering',
    'figuring it out', 'wrapping my head around', 'chewing on', 'digesting', 'absorbing',
    'taking it in', 'getting my head around', 'wrapping my brain around', 'breaking it down', 'dissecting',
    'unpacking', 'decoding', 'cracking the code', 'solving the puzzle', 'connecting dots',
    'vibing with the data', 'getting the tea', 'no cap analyzing', 'fr processing', 'lowkey thinking'
];

const PROCESSING_MESSAGES = [
    'processing', 'crunching numbers', 'running calculations', 'computing', 'analyzing data',
    'sorting through', 'filtering', 'organizing', 'structuring', 'arranging',
    'sifting through', 'parsing', 'evaluating', 'assessing', 'reviewing',
    'examining', 'inspecting', 'scanning', 'checking', 'verifying',
    'validating', 'cross-referencing', 'correlating', 'matching', 'aligning',
    'fr processing', 'actually working', 'lowkey computing', 'vibing with algorithms', 'no cap sorting'
];

const DOING_MAGIC_MESSAGES = [
    'doing magic', 'working my magic', 'cooking up something', 'brewing', 'crafting',
    'weaving spells', 'conjuring', 'manifesting', 'creating', 'building',
    'assembling', 'constructing', 'fabricating', 'generating', 'producing',
    'synthesizing', 'combining', 'merging', 'blending', 'fusing',
    'transforming', 'morphing', 'evolving', 'upgrading', 'enhancing',
    'optimizing', 'refining', 'polishing', 'perfecting', 'fine-tuning',
    'tweaking', 'adjusting', 'calibrating', 'tuning', 'harmonizing',
    'slaying', 'hitting different', 'on another level', 'fr creating', 'lowkey manifesting',
    'vibing with code', 'no cap building', 'actually cooking', 'spitting facts', 'making it pop'
];

const FINALIZING_MESSAGES = [
    'finalizing', 'wrapping up', 'putting finishing touches', 'completing', 'finishing',
    'concluding', 'sealing the deal', 'closing out', 'tying up loose ends', 'polishing off',
    'putting the bow on', 'dotting the i\'s', 'crossing the t\'s', 'making it perfect', 'perfecting',
    'adding the cherry on top', 'putting it all together', 'bringing it home', 'landing it', 'nailing it',
    'sealing it', 'locking it in', 'making it official', 'confirming', 'validating final output',
    'double-checking', 'giving it one last look', 'making sure it\'s perfect', 'ensuring quality', 'quality checking',
    'fr finishing', 'actually done', 'lowkey perfecting', 'vibing with completion', 'no cap finalizing',
    'slaying the finish', 'hitting the final note', 'on point', 'fr wrapping up', 'making it fire'
];

function AgenticThinkingBox() {
    const [currentMessage, setCurrentMessage] = useState('');
    const [stageIndex, setStageIndex] = useState(0);
    const [messagesInStage, setMessagesInStage] = useState(0);
    const animationRef = useRef(null);
    const timeoutRef = useRef(null);

    const stages = [
        THINKING_MESSAGES,
        PROCESSING_MESSAGES,
        DOING_MAGIC_MESSAGES,
        FINALIZING_MESSAGES
    ];

    useEffect(() => {
        const currentStage = stages[stageIndex];
        const randomMessageIndex = Math.floor(Math.random() * currentStage.length);
        const selectedMessage = currentStage[randomMessageIndex];
        
        let currentCharIndex = 0;
        setCurrentMessage('');

        const animateMessage = () => {
            if (currentCharIndex < selectedMessage.length) {
                setCurrentMessage(selectedMessage.substring(0, currentCharIndex + 1));
                currentCharIndex++;
                animationRef.current = setTimeout(() => {
                    animateMessage();
                }, 150); // 100-200ms per character (using 150ms average)
            } else {
                // Message fully displayed, wait before moving to next
                timeoutRef.current = setTimeout(() => {
                    // Show 2-3 messages per stage before moving to next
                    if (messagesInStage >= 2) {
                        if (stageIndex < stages.length - 1) {
                            // Move to next stage
                            setStageIndex(prev => prev + 1);
                            setMessagesInStage(0);
                        } else {
                            // All stages done, loop back to start
                            setStageIndex(0);
                            setMessagesInStage(0);
                        }
                    } else {
                        // Show another message in current stage
                        setMessagesInStage(prev => prev + 1);
                    }
                }, 1000); // Wait 1s before next message
            }
        };

        animateMessage();

        return () => {
            if (animationRef.current) clearTimeout(animationRef.current);
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
        };
    }, [stageIndex, messagesInStage]);

    return (
        <HorizontalStack align="start" blockAlign="start">
            <Box width="100%">
                <Box
                    padding="3"
                    paddingInlineStart="4"
                    paddingInlineEnd="4"
                    background="bg-transparent-active-experimental"
                    borderRadius='3'
                    borderRadiusEndStart='0'
                >
                    <VerticalStack gap="2">
                        <Text variant="bodyMd" as="p" color="subdued">
                            {currentMessage}
                            <span className="thinking-cursor">...</span>
                        </Text>
                    </VerticalStack>
                </Box>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticThinkingBox;
