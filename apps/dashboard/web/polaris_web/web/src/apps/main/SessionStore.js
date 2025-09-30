import {create} from "zustand"
import { devtools, persist, createJSONStorage } from "zustand/middleware"

const initialState = {
    threatFiltersMap: {},
    accessToken: null,
    agentConversationId: '',
    agentConversation: [],
};

let sessionStore = (set) => ({
    ...initialState,
    storeAccessToken: (accessToken) => {
        try {
            set({ accessToken });
        } catch (error) {
            console.error("Error setting accessToken:", error);
        }
    },
    setThreatFiltersMap: (threatFiltersMap) => {
        try {
            set({ threatFiltersMap });
        } catch (error) {
            console.error("Error setting threatFiltersMap:", error);
        }
    },
    setAgentConversationId: (agentConversationId) => {
        try {
            set({ agentConversationId });
        } catch (error) {
            console.error("Error setting agentConversationId:", error);
        }
    },
    setAgentConversation: (agentConversation) => {
        try {
            set({ agentConversation });
        } catch (error) {
            console.error("Error setting agentConversation:", error);
        }
    },
    resetStore: () => {
        try {
            set(initialState);
        } catch (error) {
            console.error("Error resetting store:", error);
        }
    },
});

sessionStore = devtools(sessionStore)
sessionStore = persist(sessionStore,{name: 'Akto-session-store',storage: createJSONStorage(() => sessionStorage)})

const SessionStore = create(sessionStore);

export default SessionStore

