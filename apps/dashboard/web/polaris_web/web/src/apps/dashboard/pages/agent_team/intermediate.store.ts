import { create } from "zustand";
import { devtools, persist, createJSONStorage } from "zustand/middleware";

interface AgentsStore {
    filteredUserInput: any | null;
    setFilteredUserInput: (filteredUserInput: any) => void;
    outputOptions: any | null;
    setOutputOptions: (outputOptions: any) => void;
    resetIntermediateStore: () => void;
    sourceCodeCollections: any[];
    setSourceCodeCollections: (sourceCodeCollections: any[]) => void;
    userSelectedCollections: string[];
    setUserSelectedCollections: (userSelectedCollections: string[]) => void;
    agentInitDocument: Record<string, any>| null;
    setAgentInitDocument: (agentInitDocument: Record<string, any>) => void;
}

// Zustand Store with Middleware
export const intermediateStore = create<AgentsStore>()(
    devtools(
        persist(
            (set, get) => ({
                agentInitDocument: null,
                setAgentInitDocument: (agentInitDocument: Record<string, any>) => set({ agentInitDocument: agentInitDocument }),
                filteredUserInput: null,
                setFilteredUserInput: (filteredUserInput: any) => set({ filteredUserInput: filteredUserInput }),
                outputOptions: null,
                setOutputOptions: (outputOptions: any) => set({ outputOptions: outputOptions }),
                sourceCodeCollections: [],
                setSourceCodeCollections: (sourceCodeCollections: any[]) => set({ sourceCodeCollections: sourceCodeCollections }),
                userSelectedCollections: [],
                setUserSelectedCollections: (userSelectedCollections: string[]) => set({ userSelectedCollections: userSelectedCollections }),
                resetIntermediateStore: () => set({
                    filteredUserInput: null,
                    outputOptions: null
                }),

            }),
            { name: "intermediateStore", storage: createJSONStorage(() => sessionStorage) }
        )
    )
);
