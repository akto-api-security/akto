import api from "@/apps/dashboard/pages/observe/api"
import IconPersistStore from "@/apps/main/IconPersistStore"

/**
 * Simple Icon Cache Service
 * Uses localStorage cache with intelligent batching
 */
class IconCacheService {
    constructor() {
        this.store = IconPersistStore;
        this.pendingRequests = new Set();
        this.activeRequests = new Map();
        this.batchTimeout = null;
        this.BATCH_DELAY = 50;   // ms - reduced for faster loading
        this.BATCH_SIZE = 100;    // hostnames per batch
    }

    /**
     * Get icon data for hostname
     */
    async getIconData(hostname) {
        if (!hostname?.trim()) return null;

        const cleanHostname = hostname.trim();
        
        // Check cache first
        const cached = this.store.getState().getIcon(cleanHostname);
        if (cached) return cached;
        
        // Queue for batch processing
        return this.queueForBatch(cleanHostname);
    }

    /**
     * Queue hostname for batch processing with immediate processing for small batches
     */
    queueForBatch(hostname) {
        if (this.activeRequests.has(hostname)) {
            return this.activeRequests.get(hostname);
        }

        const promise = new Promise((resolve) => {
            this.activeRequests.set(hostname, { resolve, hostname });
            this.pendingRequests.add(hostname);
            
            if (this.batchTimeout) {
                clearTimeout(this.batchTimeout);
            }
            
            // If we have only a few pending requests, process immediately for faster UX
            if (this.pendingRequests.size <= 3) {
                this.batchTimeout = setTimeout(() => this.processBatch(), 10); // Very short delay
            } else {
                this.batchTimeout = setTimeout(() => this.processBatch(), this.BATCH_DELAY);
            }
        });
        
        this.activeRequests.set(hostname, promise);
        return promise;
    }

    /**
     * Process batch requests
     */
    async processBatch() {
        if (this.pendingRequests.size === 0) return;

        const hostnamesToFetch = Array.from(this.pendingRequests).slice(0, this.BATCH_SIZE);
        this.pendingRequests.clear();

        try {
            const response = await api.fetchIconsForHostnames(hostnamesToFetch);
            
            if (response?.icons) {
                this.store.getState().setIcons(response.icons);
                
                hostnamesToFetch.forEach(hostname => {
                    const resolver = this.activeRequests.get(hostname);
                    if (resolver?.resolve) {
                        const iconData = response.icons[hostname]?.imageData || null;
                        resolver.resolve(iconData);
                    }
                    this.activeRequests.delete(hostname);
                });
            } else {
                // No icons - resolve with null
                hostnamesToFetch.forEach(hostname => {
                    const resolver = this.activeRequests.get(hostname);
                    if (resolver?.resolve) {
                        resolver.resolve(null);
                    }
                    this.activeRequests.delete(hostname);
                });
            }
        } catch (error) {

            // Resolve with null on error
            hostnamesToFetch.forEach(hostname => {
                const resolver = this.activeRequests.get(hostname);
                if (resolver?.resolve) {
                    resolver.resolve(null);
                }
                this.activeRequests.delete(hostname);
            });
        }
    }

    /**
     * Preload icons for multiple hostnames
     */
    async preloadIcons(hostnames) {
        if (!hostnames?.length) return {};

        const cleanHostnames = hostnames
            .filter(h => h?.trim())
            .map(h => h.trim());

        const cached = this.store.getState().getIcons(cleanHostnames);
        const uncachedHostnames = cleanHostnames.filter(h => !cached[h]);

        if (uncachedHostnames.length === 0) {
            return cached;
        }

        try {
            const response = await api.fetchIconsForHostnames(uncachedHostnames);
            
            if (response?.icons) {
                this.store.getState().setIcons(response.icons);
                
                return {
                    ...cached,
                    ...Object.entries(response.icons).reduce((acc, [hostname, data]) => {
                        acc[hostname] = data.imageData;
                        return acc;
                    }, {})
                };
            }
        } catch (error) {

        }

        return cached;
    }

    /**
     * Get favicon URL for external domains
     */
    getFaviconUrl(domain) {
        if (!domain) return null;
        return `https://www.google.com/s2/favicons?domain=${domain}&sz=64`;
    }

    /**
     * Search by keyword - not supported
     */
    async getIconByKeyword(keyword) {
        return null;
    }

    /**
     * Clear cache - follows existing codebase pattern
     */
    clearCache() {
        try {
            this.store.getState().clearAll();
            this.pendingRequests.clear();
            this.activeRequests.clear();
            
            if (this.batchTimeout) {
                clearTimeout(this.batchTimeout);
                this.batchTimeout = null;
            }
        } catch (error) {

        }
    }
}

export default IconCacheService;