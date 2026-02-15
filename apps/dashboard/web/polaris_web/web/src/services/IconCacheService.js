import api from "@/apps/dashboard/pages/observe/api"

/**
 * Icon Cache Service
 * Loads collection icons from backend once per page load
 */
class IconCacheService {
    constructor() {
        // Cache storage - fresh on each instance
        this.hostnameToObjectIdCache = {};
        this.objectIdToIconDataCache = {};
        
        // Single load promise to prevent multiple API calls
        this.loadPromise = null;
    }

    /**
     * Load icon caches from backend once
     * @returns {Promise} Promise that resolves when caches are loaded
     */
    async loadAllIcons() {
        // If already loading, return the existing promise
        if (this.loadPromise) {
            return this.loadPromise;
        }
        
        // If already loaded, return immediately
        if (Object.keys(this.hostnameToObjectIdCache).length > 0) {
            return Promise.resolve();
        }
        
        // Load data from backend
        this.loadPromise = (async () => {
            try {
                const response = await api.getAllIconsCache();
                if (response && response.hostnameToObjectIdCache && response.objectIdToIconDataCache) {
                    this.hostnameToObjectIdCache = response.hostnameToObjectIdCache;
                    this.objectIdToIconDataCache = response.objectIdToIconDataCache;
                }
            } catch (error) {

                throw error;
            }
        })();
        
        return this.loadPromise;
    }

    /**
     * Get icon data for a specific hostname
     * @param {string} hostname - The hostname to get icon for
     * @returns {string|null} Base64 icon data or null if not found
     */
    async getIconData(hostname) {
        if (!hostname || hostname.trim() === '') {
            return null;
        }

        // Ensure caches are loaded
        await this.loadAllIcons();
        
        const trimmedHostName = hostname.trim();

        
        // Find matching hostname in cache
        let objectId = null;
        for (const [key, value] of Object.entries(this.hostnameToObjectIdCache)) {
            // key is a string like "mcp.twilio.com,twilio.com"
            // Parse it to extract the hostname (first part)
            const parts = key.split(',');

            if (parts.length >= 1 && parts[0].trim() === trimmedHostName) {
                objectId = value;
                break;
            }
        }
        
        if (objectId && this.objectIdToIconDataCache[objectId]) {
            const iconDataObj = this.objectIdToIconDataCache[objectId];
            // Return imageData only if it's not empty
            if (iconDataObj.imageData && iconDataObj.imageData.trim() !== '') {
                return iconDataObj.imageData;
            } else {
                return null;
            }
        }
        return null;
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
     * @param {string} domain - The domain to get favicon for
     * @returns {string} Favicon URL or null if domain is null
     */
    getFaviconUrl(domain) {
        if (!domain) return null;
        return `https://www.google.com/s2/favicons?domain=${domain}&sz=64`;
    }
}

// Export the class, not an instance
export default IconCacheService;