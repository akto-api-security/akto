import { create } from 'zustand';
import { Model, Agent, PromptContent } from './types';

interface AgentsStore {
    availableModels: Model[];
    selectedModel: Model | null;
    setSelectedModel: (model: Model) => void;
    setAvailableModels: (models: Model[]) => void;
    currentPrompt: PromptContent;
    setCurrentPrompt: (prompt: PromptContent) => void;
    currentAgent: Agent | null;
    setCurrentAgent: (agent: Agent | null) => void;
    pauseAgent: () => void;
    resumeAgent: () => void;
    discardPausedState: () => void;
    isPaused: boolean;
    attemptedOnPause: boolean;
    setAttemptedOnPause: (attempted: boolean) => void;
}

export const useAgentsStore = create<AgentsStore>((set) => ({
    availableModels: [],
    selectedModel: null,
    isPaused: false,
    setSelectedModel: (model) => set({ selectedModel: model }),
    setAvailableModels: (models) => set({ availableModels: models }),
    currentPrompt: { html: '', markdown: '' },
    setCurrentPrompt: (prompt) => set({ currentPrompt: prompt }),
    currentAgent: null,
    setCurrentAgent: (agent) => set({ currentAgent: agent }),
    pauseAgent: () => set({ isPaused: true }),
    resumeAgent: () => set({ isPaused: false }),
    discardPausedState: () => set({ isPaused: false }),
    attemptedOnPause: false,
    setAttemptedOnPause: (attempted) => set({ attemptedOnPause: attempted }),
}));
