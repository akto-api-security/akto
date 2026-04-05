import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";
import { createGzipStorage } from "./PersistStore";

/**
 * Simple Icon Cache Store
 * Persists icon data to localStorage with 24-hour TTL
 */

const CACHE_TTL = 24 * 60 * 60 * 1000; // 1 day in milliseconds

const iconPersistStore = (set, get) => ({
    iconCache: {}, // hostname -> {imageData, timestamp}

    /**
     * Store icons with timestamp
     */
    setIcons: (icons) => {
        const timestamp = Date.now();
        const newEntries = {};
        
        Object.entries(icons).forEach(([hostname, iconData]) => {
            newEntries[hostname] = {
                imageData: iconData.imageData,
                timestamp: timestamp
            };
        });
        
        set(state => ({
            iconCache: { ...state.iconCache, ...newEntries }
        }));
    },

    /**
     * Get icon data with TTL check
     */
    getIcon: (hostname) => {
        const cached = get().iconCache[hostname];
        if (!cached) return null;
        
        const isExpired = (Date.now() - cached.timestamp) > CACHE_TTL;
        if (isExpired) {
            get().removeIcon(hostname);
            return null;
        }
        
        return cached.imageData;
    },

    /**
     * Get multiple icons at once
     */
    getIcons: (hostnames) => {
        const result = {};
        const now = Date.now();
        
        hostnames.forEach(hostname => {
            const cached = get().iconCache[hostname];
            if (cached && (now - cached.timestamp) <= CACHE_TTL) {
                result[hostname] = cached.imageData;
            }
        });
        
        return result;
    },

    /**
     * Remove specific icon
     */
    removeIcon: (hostname) => {
        set(state => {
            const newCache = { ...state.iconCache };
            delete newCache[hostname];
            return { iconCache: newCache };
        });
    },

    /**
     * Clear expired entries
     */
    clearExpired: () => {
        const now = Date.now();
        
        set(state => {
            const validEntries = {};
            
            Object.entries(state.iconCache).forEach(([hostname, data]) => {
                if ((now - data.timestamp) <= CACHE_TTL) {
                    validEntries[hostname] = data;
                }
            });
            
            return { iconCache: validEntries };
        });
    },

    /**
     * Clear all icons - follows PersistStore pattern
     */
    clearAll: () => {
        try {
            set({ iconCache: {} });
        } catch (error) {
            console.error("Error clearing icon cache:", error);
        }
    }
});

// Add persistence with gzip compression
const IconPersistStore = create(
    devtools(
        persist(iconPersistStore, {
            name: "akto-icon-cache",
            storage: createGzipStorage(localStorage),
            partialize: (state) => ({ iconCache: state.iconCache }),
            onRehydrateStorage: () => (state) => {
                if (state) {
                    // Clean expired entries on app start
                    setTimeout(() => state.clearExpired(), 100);
                }
            }
        }),
        { name: "IconStore" }
    )
);

setInterval(() => {
    try {
        IconPersistStore.getState().clearExpired();
    } catch (error) {
        console.error("Error in icon cleanup:", error);
    }
}, 2 * 60 * 60 * 1000);

export default IconPersistStore;