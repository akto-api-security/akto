import React from "react";
import { Icon } from "@shopify/polaris";
import { AutomationMajor, MagicMajor } from '@shopify/polaris-icons';
import IconCacheService from "@/services/IconCacheService";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import { getDomainForFavicon } from "../../pages/observe/agentic/mcpClientHelper";

// Shared icon style constant
export const ICON_STYLE = { width: '20px', height: '20px', borderRadius: '2px' };

// Singleton IconCacheService instance - shared across all components
export const sharedIconCacheService = new IconCacheService();

// Re-export icon assets for shared access
export { MCPIcon, LaptopIcon };

/**
 * Unified icon component for displaying collection/group icons
 * Consolidates logic from GroupIconRenderer and CollectionIconRenderer
 * 
 * @param {string} hostName - The hostname for backend cache lookup
 * @param {string} assetTagValue - Tag value for favicon lookup (e.g., "claude", "cursor")
 * @param {string} displayName - Display name for alt text
 * @param {Array} tagsList - List of tags for type-based fallback icons
 */
const CollectionIcon = React.memo(({ hostName, assetTagValue, displayName, tagsList }) => {
    const [iconData, setIconData] = React.useState(null);
    const [faviconUrl, setFaviconUrl] = React.useState(null);

    React.useEffect(() => {
        let isMounted = true;

        const fetchIcon = async () => {
            // Priority 1: Known client tag value - use favicon service
            if (assetTagValue) {
                const domain = getDomainForFavicon(assetTagValue);
                if (domain && isMounted) {
                    setFaviconUrl(sharedIconCacheService.getFaviconUrl(domain));
                    return;
                }
            }

            // Priority 2: Hostname from backend cache
            let imageData = null;
            if (hostName?.trim()) {
                imageData = await sharedIconCacheService.getIconData(hostName);
            }

            // Priority 3: Keyword search in backend cache
            if (!imageData && assetTagValue) {
                const parts = assetTagValue.toLowerCase().split(/[-_\s]+/);
                for (const part of parts) {
                    if (part.length > 2) {
                        imageData = await sharedIconCacheService.getIconByKeyword(part);
                        if (imageData) break;
                    }
                }
            }

            if (isMounted) {
                if (imageData) {
                    setIconData(imageData);
                }
            }
        };

        fetchIcon();
        return () => { isMounted = false; };
    }, [hostName, assetTagValue]);

    // Render backend cached icon (base64)
    if (iconData) {
        return (
            <img
                src={`data:image/png;base64,${iconData}`}
                alt={`${displayName || hostName} icon`}
                style={ICON_STYLE}
                onError={() => {
                    setIconData(null);
                }}
            />
        );
    }

    // Render favicon from external service
    if (faviconUrl) {
        return (
            <img
                src={faviconUrl}
                alt={`${displayName || assetTagValue} icon`}
                style={ICON_STYLE}
                onError={() => {
                    setFaviconUrl(null);
                }}
            />
        );
    }

    // Fallback icons based on tags or display name
    return <CollectionFallbackIcon tagsList={tagsList} displayName={displayName} />;
});

/**
 * Fallback icon component based on collection tags/type
 */
const CollectionFallbackIcon = React.memo(({ tagsList, displayName }) => {
    // GenAI collections - use Polaris icons
    if (tagsList?.some(tag => tag.name === "gen-ai")) {
        const iconSource = tagsList.some(tag => tag.name === "AI Agent") ? AutomationMajor : MagicMajor;
        return <Icon source={iconSource} color="base" />;
    }

    // MCP collections
    if (tagsList?.some(tag => tag.name === "mcp-server")) {
        return <img src={MCPIcon} alt="MCP icon" style={ICON_STYLE} />;
    }

    // Default fallback based on displayName
    const defaultIcon = displayName?.toLowerCase().startsWith('mcp') ? MCPIcon : LaptopIcon;
    return <img src={defaultIcon} alt="default icon" style={ICON_STYLE} />;
});

export { CollectionIcon, CollectionFallbackIcon };
export default CollectionIcon;
