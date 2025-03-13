import { create } from "zustand";
import { Model, Agent, PromptContent, AgentState } from "./types";
import { devtools, persist, createJSONStorage } from "zustand/middleware";

// Define the blocking states
const BLOCKING_STATES: AgentState[] = ["thinking", "paused"];
export const isBlockingState = (state: AgentState) => BLOCKING_STATES.includes(state);

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
    selectedRepository: string | null;
    setSelectedRepository: (repo: string) => void;
    currentProcessId: string | null;
    setCurrentProcessId: (currentProcessId: string) => void;
    currentSubprocess: string | null;
    setCurrentSubprocess: (subprocess: string) => void;
    currentAttempt: number 
    setCurrentAttempt: (subprocess: number) => void;
    resetStore: () => void;
}

// Zustand Store with Middleware
export const useAgentsStore = create<AgentsStore>()(
    devtools(
        persist(
            (set, get) => ({
                availableModels: [],
                selectedModel: null,
                setSelectedModel: (model: Model) => set({ selectedModel: model }),
                setAvailableModels: (models: Model[]) => set({ availableModels: models }),
                currentPrompt: { html: "", markdown: "" },
                setCurrentPrompt: (prompt: PromptContent) => set({ currentPrompt: prompt }),
                currentAgent: null,
                setCurrentAgent: (agent: Agent | null) => set({ currentAgent: agent }),
                attemptedInBlockedState: false,
                setAttemptedInBlockedState: (attempted: boolean) =>
                    set({ attemptedInBlockedState: attempted }),
                agentState: "idle",
                setAgentState: (state: AgentState) => set({ agentState: state }),
                selectedRepository: null,
                setSelectedRepository: (repo: string) => set({ selectedRepository: repo }),
                currentSubprocess: '0',
                setCurrentSubprocess: (subprocess: string) => set({ currentSubprocess: subprocess }),
                currentProcessId:"",
                setCurrentProcessId: (currentProcessId: string) => set({ currentProcessId: currentProcessId }),
                currentAttempt: 0,
                setCurrentAttempt: (attempt: number) => set({ currentAttempt: attempt }),

                resetStore: () => set({
                    attemptedInBlockedState: false,
                    agentState: "idle",
                    selectedRepository: null,
                    currentSubprocess: '0',
                    currentProcessId: "",
                    currentAttempt: 0,
                }),
            }),
            { name: "agentsStore", storage: createJSONStorage(() => localStorage) }
        )
    )
);
