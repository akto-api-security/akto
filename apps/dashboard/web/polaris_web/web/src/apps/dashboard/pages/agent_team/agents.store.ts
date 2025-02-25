import { create } from 'zustand';
import { Model, Agent, PromptContent, AgentState } from './types';

interface AgentsStore {
    availableModels: Model[];
    selectedModel: Model | null;
    setSelectedModel: (model: Model) => void;
    setAvailableModels: (models: Model[]) => void;
    currentPrompt: PromptContent;
    setCurrentPrompt: (prompt: PromptContent) => void;
    currentAgent: Agent | null;
    setCurrentAgent: (agent: Agent | null) => void;
    attemptedInBlockedState: boolean;
    setAttemptedInBlockedState: (attempted: boolean) => void;
    agentState: AgentState;
    setAgentState: (state: AgentState) => void;
}

const BLOCKING_STATES: AgentState[] = ['thinking', 'paused'];
export const isBlockingState = (state: AgentState) => BLOCKING_STATES.includes(state);

export const useAgentsStore = create<AgentsStore>((set) => ({
    availableModels: [],
    selectedModel: null,
    setSelectedModel: (model) => set({ selectedModel: model }),
    setAvailableModels: (models) => set({ availableModels: models }),
    currentPrompt: { html: '', markdown: '' },
    setCurrentPrompt: (prompt) => set({ currentPrompt: prompt }),
    currentAgent: null,
    setCurrentAgent: (agent) => set({ currentAgent: agent }),
    attemptedInBlockedState: false,
    setAttemptedInBlockedState: (attempted) => set({ attemptedInBlockedState: attempted }),
    agentState: 'idle',
    setAgentState: (state) => set({ agentState: state }),
}));
