import api from "@/apps/dashboard/pages/observe/api"

/**
 * Icon Cache Service
 * Singleton service for efficient icon loading and caching
 */
class IconCacheService {
    constructor() {
        // Cache storage - optimized for fast lookups
        this.hostnameToObjectIdCache = {};
        this.objectIdToIconDataCache = {};
        this.hostnameDirectLookup = new Map(); // O(1) hostname lookup optimization
        
        // Single load promise to prevent multiple API calls
        this.loadPromise = null;
        this.isLoaded = false;
    }

    /**
     * Load icon caches from backend once with optimizations
     * @returns {Promise} Promise that resolves when caches are loaded
     */
    async loadAllIcons() {
        // If already loading, return the existing promise
        if (this.loadPromise) {
            return this.loadPromise;
        }
        
        // If already loaded, return immediately
        if (this.isLoaded) {
            return Promise.resolve();
        }
        
        // Load data from backend with timeout and performance tracking
        this.loadPromise = (async () => {
            try {
                // Add timeout to prevent hanging
                const timeoutPromise = new Promise((_, reject) => 
                    setTimeout(() => reject(new Error('Icon cache load timeout')), 10000)
                );
                
                const response = await Promise.race([
                    api.getAllIconsCache(),
                    timeoutPromise
                ]);
                
                if (response && response.hostnameToObjectIdCache && response.objectIdToIconDataCache) {
                    this.hostnameToObjectIdCache = response.hostnameToObjectIdCache;
                    this.objectIdToIconDataCache = response.objectIdToIconDataCache;
                    
                    // Build optimized lookup map for O(1) hostname searches
                    this.buildHostnameLookupMap();
                    this.isLoaded = true;
                    

                }
            } catch (error) {

                // Don't throw to allow graceful fallback
                this.isLoaded = false;
            }
        })();
        
        return this.loadPromise;
    }
    
    /**
     * Build optimized hostname lookup map for O(1) access
     */
    buildHostnameLookupMap() {
        this.hostnameDirectLookup.clear();
        
        for (const [compositeKey, objectId] of Object.entries(this.hostnameToObjectIdCache)) {
            // Parse composite key "hostname,domain" to extract hostname
            const parts = compositeKey.split(',');
            if (parts.length >= 1) {
                const hostname = parts[0].trim();
                if (hostname) {
                    this.hostnameDirectLookup.set(hostname, objectId);
                }
            }
        }
    }

    /**
     * Get icon data for a specific hostname with optimized O(1) lookup
     * @param {string} hostname - The hostname to get icon for
     * @returns {string|null} Base64 icon data or null if not found
     */
    async getIconData(hostname) {
        if (!hostname || hostname.trim() === '') {
            return null;
        }

        try {
            // Ensure caches are loaded with timeout
            await this.loadAllIcons();
            
            if (!this.isLoaded) {
                return null; // Graceful fallback if cache failed to load
            }
            
            const trimmedHostName = hostname.trim();

            // O(1) lookup using optimized Map
            const objectId = this.hostnameDirectLookup.get(trimmedHostName);
            
            if (objectId && this.objectIdToIconDataCache[objectId]) {
                const iconDataObj = this.objectIdToIconDataCache[objectId];
                
                // Return imageData only if it's not empty
                if (iconDataObj.imageData && iconDataObj.imageData.trim() !== '') {
                    return iconDataObj.imageData;
                }
            }
            
            return null;
        } catch (error) {
            console.warn(`Icon lookup failed for hostname: ${hostname}`, error);
            return null; // Graceful fallback
        }
    }

    /**
     * Search for icon by keyword in cached hostnames
     * Useful for finding icons by client name (e.g., "claude", "copilot")
     * @param {string} keyword - The keyword to search for in hostnames
     * @returns {string|null} Base64 icon data or null if not found
     */
    async getIconByKeyword(keyword) {
        if (!keyword || keyword.trim() === '') {
            return null;
        }

        // Ensure caches are loaded
        await this.loadAllIcons();
        
        const lowerKeyword = keyword.toLowerCase().trim();
        
        // Search for keyword in cached hostnames
        let objectId = null;
        for (const [key, value] of Object.entries(this.hostnameToObjectIdCache)) {
            // key is a string like "mcp.twilio.com,twilio.com"
            const lowerKey = key.toLowerCase();
            
            // Check if keyword is found in any part of the hostname key
            if (lowerKey.includes(lowerKeyword)) {
                objectId = value;
                break;
            }
        }
        
        if (objectId && this.objectIdToIconDataCache[objectId]) {
            const iconDataObj = this.objectIdToIconDataCache[objectId];
            if (iconDataObj.imageData && iconDataObj.imageData.trim() !== '') {
                return iconDataObj.imageData;
            }
        }
        return null;
    }

    /**
     * Get favicon URL for a domain using external service
     * Returns a URL that can be used directly in img src
     * @param {string} domain - The domain to get favicon for
     * @returns {string} Favicon URL
     */
    getFaviconUrl(domain) {
        if (!domain) return null;
        // Use Google's favicon service - reliable and fast
        return `https://www.google.com/s2/favicons?domain=${domain}&sz=64`;
    }
}

// Export singleton instance to prevent multiple API calls across components
let iconCacheServiceInstance = null;

export const getIconCacheService = () => {
    if (!iconCacheServiceInstance) {
        iconCacheServiceInstance = new IconCacheService();
    }
    return iconCacheServiceInstance;
};

// Export the class for testing
export { IconCacheService };

// Export singleton instance as default
export default getIconCacheService();