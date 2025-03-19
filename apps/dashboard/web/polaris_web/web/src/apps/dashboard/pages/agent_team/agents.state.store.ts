import { create } from "zustand";
import { devtools, persist, createJSONStorage } from "zustand/middleware";
import { AgentState } from "./types";

interface AgentStatusData {
    currentAgentState: AgentState;
    currentAgentProcessId: string | null;
    currentAgentSubprocess: string | null;
    currentSubprocessAttempt: number;
}

const defaultAgentData = (): AgentStatusData => ({
    currentAgentState: "idle",
    currentAgentProcessId: null,
    currentAgentSubprocess: null,
    currentSubprocessAttempt: 0,
});

interface AgentsStateStore {
    agentsStore: Record<string, AgentStatusData>;

    setCurrentAgentState: (agentId: string, agentState: AgentState) => void;
    setCurrentAgentProcessId: (agentId: string, processId: string | null) => void;
    setCurrentAgentSubprocess: (agentId: string, subprocess: string | null) => void;
    setCurrentSubprocessAttempt: (agentId: string, attempt: number) => void;

    getCurrentAgentState: (agentId: string) => AgentState | undefined;
    getCurrentAgentProcessId: (agentId: string) => string | null;
    getCurrentAgentSubprocess: (agentId: string) => string | null;
    getCurrentSubprocessAttempt: (agentId: string) => number;

    resetAllAgents: () => void;
}

export const useAgentsStateStore = create<AgentsStateStore>()(
    devtools(
        persist(
            (set, get) => ({
                agentsStore: {},

                setCurrentAgentState: (agentId, agentState) =>
                    set((state) => ({
                        agentsStore: {
                            ...state.agentsStore,
                            [agentId]: {
                                ...state.agentsStore[agentId] || defaultAgentData(),
                                currentAgentState: agentState,
                            },
                        },
                    })),

                setCurrentAgentProcessId: (agentId, processId) =>
                    set((state) => ({
                        agentsStore: {
                            ...state.agentsStore,
                            [agentId]: {
                                ...state.agentsStore[agentId] || defaultAgentData(),
                                currentAgentProcessId: processId,
                            },
                        },
                    })),

                setCurrentAgentSubprocess: (agentId, subprocess) =>
                    set((state) => ({
                        agentsStore: {
                            ...state.agentsStore,
                            [agentId]: {
                                ...state.agentsStore[agentId] || defaultAgentData(),
                                currentAgentSubprocess: subprocess,
                            },
                        },
                    })),

                setCurrentSubprocessAttempt: (agentId, attempt) =>
                    set((state) => ({
                        agentsStore: {
                            ...state.agentsStore,
                            [agentId]: {
                                ...state.agentsStore[agentId] || defaultAgentData(),
                                currentSubprocessAttempt: attempt,
                            },
                        },
                    })),

                // Getters
                getCurrentAgentState: (agentId) => get().agentsStore[agentId]?.currentAgentState,
                getCurrentAgentProcessId: (agentId) => get().agentsStore[agentId]?.currentAgentProcessId ?? null,
                getCurrentAgentSubprocess: (agentId) => get().agentsStore[agentId]?.currentAgentSubprocess ?? null,
                getCurrentSubprocessAttempt: (agentId) => get().agentsStore[agentId]?.currentSubprocessAttempt ?? 0,


                resetAllAgents: () => set({ agentsStore: {} }),
            }),
            { name: "allAgentsStore", storage: createJSONStorage(() => localStorage) }
        )
    )
);
