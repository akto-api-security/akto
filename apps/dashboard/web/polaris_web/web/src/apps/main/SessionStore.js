import {create} from "zustand"
import { devtools, persist, createJSONStorage } from "zustand/middleware"

const initialState = {
    threatFiltersMap: {},
    accessToken: null,
    lastEndpointEpoch: 0
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
    setLastEndpointEpoch: (lastEndpointEpoch) => {
        try {
            set({ lastEndpointEpoch });
        } catch (error) {
            console.error("Error setting lastEndpointEpoch:", error);
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

