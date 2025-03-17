import {create} from "zustand"
import { devtools, persist } from "zustand/middleware"

import pako from "pako"; // Gzip Compression

// Custom Storage with Gzip Compression
const gzipStorage = {
    getItem: (name) => {
        const compressedData = localStorage.getItem(name);
        if (!compressedData) return null;

        try {
            // Decode base64 & Gunzip (decompress)
            const binaryData = atob(compressedData);
            const uint8Array = new Uint8Array(binaryData.length);
            for (let i = 0; i < binaryData.length; i++) {
                uint8Array[i] = binaryData.charCodeAt(i);
            }
            const decompressed = pako.inflate(uint8Array, { to: "string" });
            return JSON.parse(decompressed);
        } catch (error) {
            console.error("Error decompressing state:", error);
            return null;
        }
    },
    setItem: (name, value) => {
        try {
            // Stringify, Gzip compress, then convert to Base64
            const jsonString = JSON.stringify(value);
            const compressed = pako.deflate(jsonString, { level: 9 });
            const binaryString = Array.from(compressed)
                .map((byte) => String.fromCharCode(byte))
                .join("");
            const base64Encoded = btoa(binaryString);
            localStorage.setItem(name, base64Encoded);
        } catch (error) {
            console.error("Error compressing state:", error);
        }
    },
    removeItem: (name) => localStorage.removeItem(name),
};

const initialState = {
    subCategoryMap: {},
    categoryMap: {},
    sendEventOnLogin: false,
    defaultIgnoreSummaryTime: 2 * 60 * 60
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

    resetStore: () => {
        try {
            set(initialState);
        } catch (error) {
            console.error("Error resetting store:", error);
        }
    },
});

localStore = devtools(localStore)
localStore = persist(localStore,{name: 'Akto-tests-store', storage:gzipStorage})

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

