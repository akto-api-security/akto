/**
 * Constants for new chat components
 * Only includes constants for ChatMessage, ConversationHistory, ChatInputBar, AiAnalysisCard
 */

// Asset paths for chat components
export const CHAT_ASSETS = {
    AKTO_LOGO: '/public/akto.svg',
    FRAME_LOGO: '/public/Frame.svg',
    MAGIC_ICON: '/public/magic_icon.svg',
    DIVIDER: '/public/Divider.svg',
    DIVIDER_ALERT: '/public/Divider_alert.svg',
};

// Message labels used in ConversationHistory
export const MESSAGE_LABELS = {
    TESTED_INTERACTION: 'Tested interaction',
    HR_AGENT_RESPONSE: 'HR agent response',
};

// Placeholder text for ChatInputBar
export const PLACEHOLDER_TEXT = {
    INPUT_DEFAULT: 'Ask a follow up...',
    INPUT_LOADING: 'AI is analyzing...',
};

// Analysis card text
export const ANALYSIS_TEXT = {
    LOADING: 'Analyzing interaction...',
    EMPTY: 'No analysis available',
    HEADER: 'Akto AI Overview',
};

// Vulnerability badge text
export const VULNERABILITY_BADGE = {
    SYSTEM_PROMPT_LEAK: 'System Prompt Leak',
};

// Message types for ChatMessage
export const MESSAGE_TYPES = {
    REQUEST: 'request',
    RESPONSE: 'response',
};
