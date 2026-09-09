import {create} from "zustand"
import { devtools, persist } from "zustand/middleware"

import pako from "pako"; // Gzip Compression
import { createGzipStorage, base64ToBytes } from "./PersistStore";
import { devtoolsOptions } from "./devtoolsConfig";

// Custom Storage with Gzip Compression for localStorage
const gzipLocalStorage = createGzipStorage(localStorage);

const initialState = {
    subCategoryMap: {},
    categoryMap: {},
    sendEventOnLogin: false,
    defaultIgnoreSummaryTime: 2 * 60 * 60,
    lastEndpointEpoch: 0,
    agenticNewLayout: false,
    guardrailViolationsNewLayout: false,
};

let localStore = (set) => ({
    ...initialState,

    setSubCategoryMap: (subCategoryMap) => {
        try {
            set({ subCategoryMap });
        } catch (error) {
            console.error("Error setting subCategoryMap:", error);
        }
    },

    setCategoryMap: (categoryMap) => {
        try {
            set({ categoryMap });
        } catch (error) {
            console.error("Error setting categoryMap:", error);
        }
    },

    setSendEventOnLogin: (sendEventOnLogin) => {
        try {
            set({ sendEventOnLogin });
        } catch (error) {
            console.error("Error setting sendEventOnLogin:", error);
        }
    },

    setDefaultIgnoreSummaryTime: (val) => {
        try {
            set({ defaultIgnoreSummaryTime: val });
        } catch (error) {
            console.error("Error setting defaultIgnoreSummaryTime:", error);
        }
    },

    setLastEndpointEpoch: (lastEndpointEpoch) => {
        try {
            set({ lastEndpointEpoch });
        } catch (error) {
            console.error("Error setting lastEndpointEpoch:", error);
        }
    },

    setAgenticNewLayout: (agenticNewLayout) => {
        try {
            set({ agenticNewLayout });
        } catch (error) {
            console.error("Error setting agenticNewLayout:", error);
        }
    },

    setGuardrailViolationsNewLayout: (guardrailViolationsNewLayout) => {
        try {
            set({ guardrailViolationsNewLayout });
        } catch (error) {
            console.error("Error setting guardrailViolationsNewLayout:", error);
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

localStore = devtools(localStore, devtoolsOptions("LocalStore", (state) => ({
    ...state,
    subCategoryMap: `<<${Object.keys(state.subCategoryMap || {}).length} entries>>`,
    categoryMap: `<<${Object.keys(state.categoryMap || {}).length} entries>>`,
})))
localStore = persist(localStore, {
    name: 'Akto-tests-store',
    storage: gzipLocalStorage
})

const LocalStore = create(localStore);

export const localStorePersistSync = (store) => {
  const storageEventCallback = (event) => {
   const hasNonEmptySubCategoryMap = (newValue) => {
        // Rehydration should only occur if the persisted LocalStore has been updated with a non-empty subCategoryMap
        try {
            // Decompress the gzip data first
            const decompressed = pako.inflate(base64ToBytes(newValue), { to: "string" });
            const parsedNewValue = JSON.parse(decompressed);

            const state = parsedNewValue?.state; // zustand persists state under 'state' key
            const subCategoryMap = state?.subCategoryMap;

            return subCategoryMap && typeof subCategoryMap === 'object' && Object.keys(subCategoryMap).length !== 0;
        } catch (err) {
            // do nothing
        }

        return false; // default to false if any error or unexpected structure
    }

    if ((event.storageArea === localStorage) && (event.key === store.persist.getOptions().name) && hasNonEmptySubCategoryMap(event.newValue)) {
        /*
        * Rehydrate LocalStore from localStorage in the current tab when the persisted zustand store in localStorage is updated by a different tab.
        * https://zustand.docs.pmnd.rs/integrations/persisting-store-data#rehydrate
        * https://zustand.docs.pmnd.rs/integrations/persisting-store-data#how-can-i-rehydrate-on-storage-event
        */
        store.persist.rehydrate();
    }
  }

  window.addEventListener('storage', storageEventCallback)

  return () => {
    window.removeEventListener('storage', storageEventCallback)
  }
}

export default LocalStore

