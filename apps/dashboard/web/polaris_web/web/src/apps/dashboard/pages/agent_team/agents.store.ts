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
    agentSteps: Map<string, Record<string, string>>;
    setAgentSteps: (key: string, value: Record<string, string>) => void;
    selectedRepository: string | null;
    setSelectedRepository: (repo: string) => void;
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
                agentSteps: new Map([
                    [
                        "FIND_VULNERABILITIES_FROM_SOURCE_CODE",
                        {
                            "1": "Find backend directory",
                            "2": "Find language and framework",
                            "3": "Detect auth mechanism type",
                        },
                    ],
                ]),
                setAgentSteps: (key: string, value: Record<string, string>) =>
                    set((state) => {
                        const updatedSteps = new Map(state.agentSteps);
                        updatedSteps.set(key, value);
                        return { agentSteps: updatedSteps };
                }),
                selectedRepository: null,
                setSelectedRepository: (repo: string) => set({ selectedRepository: repo }),

            }),
            { name: "agentsStore", storage: createJSONStorage(() => localStorage) }
        )
    )
);
