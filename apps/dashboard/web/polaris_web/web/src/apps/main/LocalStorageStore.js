import {create} from "zustand"
import { devtools, persist, createJSONStorage } from "zustand/middleware"

const initialState = {
    subCategoryMap: {},
    categoryMap: {},
    sendEventOnLogin: false,
    defaultIgnoreSummaryTime: 2 * 60 * 60,
    lastEndpointEpoch: 0
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
            set({ val });
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

    resetStore: () => {
        try {
            set(initialState);
        } catch (error) {
            console.error("Error resetting store:", error);
        }
    },
});

localStore = devtools(localStore)
localStore = persist(localStore,{name: 'Akto-tests-store',storage: createJSONStorage(() => localStorage)})

const LocalStore = create(localStore);

// window.addEventListener('storage', (event) => {
//   const isFromAkto = (window.IS_SAAS === 'true' && event.url.includes("akto") || event.url.includes("dashboard"))
//   if(event.key === 'undefined' && isFromAkto) {
//     const newStorageValue = JSON.parse(event.newValue)
//     LocalStore.setState({
//       subCategoryMap: newStorageValue.state.subCategoryMap
//     });
//   }
// });

export default LocalStore

