import {create} from "zustand"
import { devtools, persist, createJSONStorage } from "zustand/middleware"

const initialState = {
    threatFiltersMap: {},
    accessToken: null,
    currentAgentConversationId: '',
    agentConversation: {},
    // NewRelic Integration State
    newrelic: {
        accountId: null,
        region: 'US',
        enabled: false,
        lastSyncTime: null,
    },
    // Linear Integration State
    linear: {
        isConfigured: false,
        workspaceUrl: null,
        defaultProjectId: null,
        defaultTeamId: null,
        issueTemplate: null,
        severityToPriorityMap: null,
        createdTs: null,
        updatedTs: null,
    },
};

let sessionStore = (set, get) => ({
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
    setCurrentAgentConversationId: (currentAgentConversationId) => {
        try {
            set({ currentAgentConversationId });
        } catch (error) {
            console.error("Error setting currentAgentConversationId:", error);
        }
    },
    setAgentConversation: (agentConversation) => {
        try {
            set({ agentConversation });
        } catch (error) {
            console.error("Error setting agentConversation:", error);
        }
    },
    // NewRelic Integration Actions
    setNewRelicConfig: (config) => {
        try {
            set({
                newrelic: {
                    accountId: config.accountId || null,
                    region: config.region || 'US',
                    enabled: config.enabled || false,
                    lastSyncTime: config.lastSyncTime || null,
                }
            });
        } catch (error) {
            console.error("Error setting NewRelic config:", error);
        }
    },
    clearNewRelicConfig: () => {
        try {
            set({
                newrelic: {
                    accountId: null,
                    region: 'US',
                    enabled: false,
                    lastSyncTime: null,
                }
            });
        } catch (error) {
            console.error("Error clearing NewRelic config:", error);
        }
    },
    // Linear Integration Actions
    setLinearConfig: (config) => {
        try {
            set({
                linear: {
                    isConfigured: config.isConfigured !== undefined ? config.isConfigured : true,
                    workspaceUrl: config.workspaceUrl || null,
                    defaultProjectId: config.defaultProjectId || null,
                    defaultTeamId: config.defaultTeamId || null,
                    issueTemplate: config.issueTemplate || null,
                    severityToPriorityMap: config.severityToPriorityMap || null,
                    createdTs: config.createdTs || null,
                    updatedTs: config.updatedTs || null,
                }
            });
        } catch (error) {
            console.error("Error setting Linear config:", error);
        }
    },
    clearLinearConfig: () => {
        try {
            set({
                linear: {
                    isConfigured: false,
                    workspaceUrl: null,
                    defaultProjectId: null,
                    defaultTeamId: null,
                    issueTemplate: null,
                    severityToPriorityMap: null,
                    createdTs: null,
                    updatedTs: null,
                }
            });
        } catch (error) {
            console.error("Error clearing Linear config:", error);
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

