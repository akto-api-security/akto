// Mock data for development and testing purposes
// This file contains all hardcoded data used in the Agentic AI interface

/**
 * Returns mock AI thinking/processing items
 * In production, this will be replaced by streaming data from the API
 */
export const getMockThinkingItems = () => {
    return [
        'Assessing agent permissions, tool usage, API access, and guardrail coverage to identify unsafe or high impact execution paths.',
        'Identifying prompt injection attempts, anomalous agent behavior, and unprotected access to sensitive data.',
        'Prioritizing risks by business impact and producing a concise, board ready summary with recommended actions.'
    ];
};

/**
 * Returns mock AI response content
 * In production, this will be replaced by streaming data from the API
 */
export const getMockResponseContent = () => {
    return {
        title: 'Weekly Agentic Risk Summary',
        sections: [
            {
                header: 'Scope analyzed',
                items: [
                    '14 active agent workflows',
                    '7 day observation window'
                ]
            },
            {
                header: 'Key risks identified',
                items: [
                    '2 agents (14%) executing tools or APIs without execution guardrails',
                    '1 agent (7%) accessing sensitive customer data without output redaction',
                    '1 newly discovered agent with elevated execution privileges',
                    '1 shadow agent without an assigned owner'
                ]
            },
            {
                header: 'Threat activity observed',
                items: [
                    '3 prompt injection attempts detected',
                    '100% of attempts blocked before execution'
                ]
            },
            {
                header: 'Posture change',
                items: [
                    'Overall agentic risk increased week over week',
                    'Primary drivers: new agents and expanded execution scope'
                ]
            },
            {
                header: 'Top priorities',
                items: [
                    'Apply execution guardrails to the 2 highest risk agents',
                    'Enforce output redaction on agents handling sensitive data',
                    'Assign ownership and review permissions for shadow agents'
                ]
            }
        ]
    };
};

/**
 * Returns mock conversation suggestions
 * In production, this will be replaced by data from the API
 */
export const getMockSuggestions = () => {
    return [
        'Which agents should I secure first and why?',
        'What actions can these high risk agents perform if compromised?',
        'Apply recommended guardrails to the highest risk agents'
    ];
};

/**
 * Simulates API delay for realistic testing
 * @param {number} ms - Delay in milliseconds
 */
export const mockDelay = (ms) => {
    return new Promise(resolve => setTimeout(resolve, ms));
};

/**
 * Returns a random delay between min and max milliseconds
 * @param {number} min - Minimum delay in ms
 * @param {number} max - Maximum delay in ms
 */
export const randomDelay = (min, max) => {
    const delay = Math.floor(Math.random() * (max - min + 1)) + min;
    return mockDelay(delay);
};

/**
 * Mock function to simulate streaming thinking items
 * @param {Function} onItem - Callback for each thinking item
 * @param {number} delayBetweenItems - Delay in ms between items
 */
export const mockStreamThinkingItems = async (onItem, delayBetweenItems = 300) => {
    const items = getMockThinkingItems();
    for (const item of items) {
        await mockDelay(delayBetweenItems);
        onItem(item);
    }
};

/**
 * Mock function to simulate streaming response content
 * @param {Function} onChunk - Callback for each content chunk
 * @param {number} delayBetweenChunks - Delay in ms between chunks
 */
export const mockStreamResponse = async (onChunk, delayBetweenChunks = 200) => {
    // Random delay before starting response (5-10 seconds)
    await randomDelay(5000, 10000);

    const content = getMockResponseContent();

    // Stream title
    await mockDelay(delayBetweenChunks);
    onChunk({ type: 'title', content: content.title });

    // Stream sections and items
    for (const section of content.sections) {
        await mockDelay(delayBetweenChunks);
        onChunk({ type: 'header', content: section.header });

        for (const item of section.items) {
            await mockDelay(delayBetweenChunks);
            onChunk({ type: 'item', content: item });
        }
    }
};
