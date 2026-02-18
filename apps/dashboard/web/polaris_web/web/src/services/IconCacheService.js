import api from "@/apps/dashboard/pages/observe/api"

/**
 * Icon Cache Service
 * On-demand icon loading with intelligent batching and caching
 * Replaces bulk loading with efficient per-component loading
 */
class IconCacheService {
    constructor() {
        // Local cache storage for loaded icons
        this.iconCache = new Map(); // hostname -> iconData
        
        // Batching system for efficient API calls
        this.pendingRequests = new Set(); // hostnames waiting to be fetched
        this.batchTimeout = null;
        this.BATCH_DELAY = 50; // ms - debounce multiple requests
        this.BATCH_SIZE = 20; // max hostnames per API call
    }

    /**
     * Process pending batch requests
     */
    async processBatch() {
        if (this.pendingRequests.size === 0) {
            return;
        }

        const hostnamesToFetch = Array.from(this.pendingRequests).slice(0, this.BATCH_SIZE);
        this.pendingRequests.clear();

        try {
            const response = await api.getIconsForHostnames(hostnamesToFetch);
            
            if (response && response.icons) {
                // Cache the results
                Object.entries(response.icons).forEach(([hostname, iconData]) => {
                    this.iconCache.set(hostname, iconData.imageData);
                });
            }

            // Mark hostnames as processed (even if no icon found)
            hostnamesToFetch.forEach(hostname => {
                if (!this.iconCache.has(hostname)) {
                    this.iconCache.set(hostname, null); // Cache miss result
                }
            });

        } catch (error) {
            console.error('Error fetching icons:', error);
            // Cache error result to avoid repeated failed requests
            hostnamesToFetch.forEach(hostname => {
                this.iconCache.set(hostname, null);
            });
        }
    }

    /**
     * Get icon data for a specific hostname with on-demand loading
     * @param {string} hostname - The hostname to get icon for
     * @returns {string|null} Base64 icon data or null if not found
     */
    async getIconData(hostname) {
        if (!hostname || hostname.trim() === '') {
            return null;
        }

        const cleanHostname = hostname.trim();
        
        // Check if already cached (including null results)
        if (this.iconCache.has(cleanHostname)) {
            return this.iconCache.get(cleanHostname);
        }

        // Add to pending batch
        this.pendingRequests.add(cleanHostname);

        // Debounce batch processing
        if (this.batchTimeout) {
            clearTimeout(this.batchTimeout);
        }

        // Set up batch processing with promise
        return new Promise((resolve) => {
            this.batchTimeout = setTimeout(async () => {
                await this.processBatch();
                resolve(this.iconCache.get(cleanHostname) || null);
            }, this.BATCH_DELAY);
        });
    }

    /**
     * Preload icons for multiple hostnames - more efficient than individual getIconData calls
     * @param {Array<string>} hostnames - Array of hostnames to preload
     * @returns {Promise<Map>} Promise that resolves to Map of hostname -> iconData
     */
    async preloadIcons(hostnames) {
        if (!hostnames || hostnames.length === 0) {
            return new Map();
        }

        // Filter to only uncached hostnames
        const uncachedHostnames = hostnames.filter(hostname => 
            hostname && hostname.trim() && !this.iconCache.has(hostname.trim())
        );

        if (uncachedHostnames.length === 0) {
            // All requested icons are already cached
            const result = new Map();
            hostnames.forEach(hostname => {
                const cleanHostname = hostname?.trim();
                if (cleanHostname && this.iconCache.has(cleanHostname)) {
                    const iconData = this.iconCache.get(cleanHostname);
                    if (iconData) {
                        result.set(cleanHostname, iconData);
                    }
                }
            });
            return result;
        }

        // Batch fetch uncached hostnames
        try {
            const response = await api.getIconsForHostnames(uncachedHostnames);
            
            if (response && response.icons) {
                // Cache the results
                Object.entries(response.icons).forEach(([hostname, iconData]) => {
                    this.iconCache.set(hostname, iconData.imageData);
                });
            }

            // Mark all requested hostnames as processed
            uncachedHostnames.forEach(hostname => {
                const cleanHostname = hostname.trim();
                if (!this.iconCache.has(cleanHostname)) {
                    this.iconCache.set(cleanHostname, null); // Cache miss result
                }
            });

        } catch (error) {
            console.error('Error preloading icons:', error);
            // Cache error results to avoid repeated failed requests
            uncachedHostnames.forEach(hostname => {
                const cleanHostname = hostname.trim();
                this.iconCache.set(cleanHostname, null);
            });
        }

        // Return results for all requested hostnames
        const result = new Map();
        hostnames.forEach(hostname => {
            const cleanHostname = hostname?.trim();
            if (cleanHostname && this.iconCache.has(cleanHostname)) {
                const iconData = this.iconCache.get(cleanHostname);
                if (iconData) {
                    result.set(cleanHostname, iconData);
                }
            }
        });
        return result;
    }

    /**
     * Search for icon by keyword - currently returns null as bulk search is not supported in on-demand mode
     * This method is kept for backward compatibility but keyword search requires loading all data
     * which goes against the on-demand principle. Components should pass specific hostnames instead.
     * @param {string} keyword - The keyword to search for in hostnames
     * @returns {string|null} Always returns null in on-demand mode
     */
    async getIconByKeyword(keyword) {
        if (!keyword || keyword.trim() === '') {
            return null;
        }

        // For on-demand mode, keyword search is not supported as it would require
        // loading all icons which defeats the purpose of on-demand loading
        console.warn('getIconByKeyword is not supported in on-demand mode. Use specific hostnames instead.');
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