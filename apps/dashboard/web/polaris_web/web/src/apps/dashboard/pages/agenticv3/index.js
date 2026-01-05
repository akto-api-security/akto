/**
 * AgenticV3 - Pure Polaris Implementation
 *
 * A clean rewrite of the agentic AI interface with:
 * - Only Polaris components (no HTML tags)
 * - Mock AI service (no real API calls)
 * - Simulated streaming responses
 * - Clean component architecture
 */

export { default as MainPage } from './MainPage';
export { default as ConversationPage } from './ConversationPage';

// Re-export components for custom usage
export { default as UserMessage } from './components/UserMessage';
export { default as ResponseContent } from './components/ResponseContent';
export { default as SearchInput } from './components/SearchInput';
export { default as SuggestionsList } from './components/SuggestionsList';
export { default as CopyButton } from './components/CopyButton';

// Re-export hooks
export { useAgenticChatV3 } from './hooks/useAgenticChatV3';

// Re-export services
export * from './services/mockAIService';
