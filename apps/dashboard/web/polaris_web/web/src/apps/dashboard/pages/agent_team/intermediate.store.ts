import { create } from "zustand";
import { devtools, persist, createJSONStorage } from "zustand/middleware";

interface AgentsStore {
    filteredUserInput: any | null;
    setFilteredUserInput: (filteredUserInput: any) => void;
}

// Zustand Store with Middleware
export const intermediateStore = create<AgentsStore>()(
    devtools(
        persist(
            (set, get) => ({
                filteredUserInput: null,
                setFilteredUserInput:  (filteredUserInput: any) => set({ filteredUserInput: filteredUserInput }),
               
            }),
            { name: "intermediateStore", storage: createJSONStorage(() => sessionStorage) }
        )
    )
);
