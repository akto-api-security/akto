import { create } from "zustand";
import { devtools, persist, createJSONStorage } from "zustand/middleware";

interface AgentsStore {
    filteredUserInput: any | null;
    setFilteredUserInput: (filteredUserInput: any) => void;
    outputOptions: any | null;
    setOutputOptions: (outputOptions: any) => void;
    resetIntermediateStore: () => void;

}

// Zustand Store with Middleware
export const intermediateStore = create<AgentsStore>()(
    devtools(
        persist(
            (set, get) => ({
                filteredUserInput: null,
                setFilteredUserInput: (filteredUserInput: any) => set({ filteredUserInput: filteredUserInput }),
                outputOptions: null,
                setOutputOptions: (outputOptions: any) => set({ outputOptions: outputOptions }),

                resetIntermediateStore: () => set({
                    filteredUserInput: null,
                    outputOptions: null
                }),

            }),
            { name: "intermediateStore", storage: createJSONStorage(() => sessionStorage) }
        )
    )
);
