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
    setAgentState: (state: AgentState | ((prev: AgentState) => AgentState)) => void;
    currentProcessId: string | null;
    setCurrentProcessId: (currentProcessId: string) => void;
    currentSubprocess: string | null;
    setCurrentSubprocess: (subprocess: string) => void;
    currentAttempt: number
    setCurrentAttempt: (subprocess: number) => void;
    PRstate: string;
    setPRState: (state: string) => void;
    finalCTAShow: boolean;
    setFinalCTAShow: (state: boolean) => void;
    chosenBackendDirectory: string;
    setChosenBackendDirectory: (directory: string) => void;
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
                setAgentState: (stateOrUpdater) => {
                    const prev = get().agentState;
                    const nextState = typeof stateOrUpdater === "function"
                      ? (stateOrUpdater as (prev: AgentState) => AgentState)(prev)
                      : stateOrUpdater;
                    set({ agentState: nextState });
                  },                
                currentSubprocess: '0',
                setCurrentSubprocess: (subprocess: string) => set({ currentSubprocess: subprocess }),
                currentProcessId:"",
                setCurrentProcessId: (currentProcessId: string) => set({ currentProcessId: currentProcessId }),
                currentAttempt: 0,
                setCurrentAttempt: (attempt: number) => set({ currentAttempt: attempt }),
                PRstate: "-1",
                setPRState: (state: string) => set({ PRstate: state }),
                finalCTAShow: false,
                setFinalCTAShow: (state: boolean) => set({ finalCTAShow: state }),
                chosenBackendDirectory: "",
                setChosenBackendDirectory: (directory: string) => set({ chosenBackendDirectory: directory }),
                resetStore: () => set({
                    currentPrompt: { html: "", markdown: "" },
                    attemptedInBlockedState: false,
                    agentState: "idle",
                    currentSubprocess: "0",
                    currentProcessId: "",
                    currentAttempt: 0,
                    PRstate: "-1",
                    finalCTAShow: false,
                    chosenBackendDirectory: "",
                }),
            }),
            { name: "agentsStore", storage: createJSONStorage(() => localStorage) }
        )
    )
);
