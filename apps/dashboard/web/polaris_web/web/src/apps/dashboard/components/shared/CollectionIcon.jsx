import React from "react";
import { Avatar, Icon } from "@shopify/polaris";
import { AutomationMajor, MagicMajor } from '@shopify/polaris-icons';
import IconCacheService from "@/services/IconCacheService";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import HardDrivesIcon from "@/assets/hard-drives-duotone.svg";
import { getDomainForFavicon } from "../../pages/observe/agentic/mcpClientHelper";
import { isApiSecurityCategory, isDastCategory } from "../../../main/labelHelper";

export const sharedIconCacheService = new IconCacheService();
export { MCPIcon, LaptopIcon };

const CollectionIcon = React.memo(({ hostName, assetTagValue, displayName, tagsList }) => {
    const [iconData, setIconData] = React.useState(null);
    const [faviconUrl, setFaviconUrl] = React.useState(null);
    React.useEffect(() => {
        let mounted = true;
        (async () => {
            if (assetTagValue) {
                const domain = getDomainForFavicon(assetTagValue);
                if (domain && mounted) { setFaviconUrl(sharedIconCacheService.getFaviconUrl(domain)); return; }
            }
            let data = hostName?.trim() ? await sharedIconCacheService.getIconData(hostName) : null;
            if (!data && assetTagValue) {
                for (const part of assetTagValue.toLowerCase().split(/[-_\s]+/)) {
                    if (part.length > 2) { data = await sharedIconCacheService.getIconByKeyword(part); if (data) break; }
                }
            }
            if (data && mounted) { setIconData(data); return; }
            // Fallback for API Security/DAST: probe favicon.ico directly
            // Unlike Google's favicon service, this will fail for domains without a real favicon
            if (!data && hostName?.trim() && mounted && (isApiSecurityCategory() || isDastCategory())) {
                const full = hostName.replace(/^(https?:\/\/)/, '').split('/')[0];
                const parts = full.split('.');
                const root = parts.length > 2 ? parts.slice(-2).join('.') : full;
                if (root) {
                    const img = new Image();
                    img.onload = () => { if (mounted) setFaviconUrl(sharedIconCacheService.getFaviconUrl(root)); };
                    img.onerror = () => { /* no favicon — falls through to HardDrivesIcon */ };
                    img.src = `https://${root}/favicon.ico`;
                }
            }
        })();
        return () => { mounted = false; };
    }, [hostName, assetTagValue]);

    if (iconData) return <Avatar source={`data:image/png;base64,${iconData}`} shape="square" size="extraSmall" />;
    if (faviconUrl) return <Avatar source={faviconUrl} shape="square" size="extraSmall" />;

    // API Security and DAST fallback
    if (isApiSecurityCategory() || isDastCategory()) return <Avatar source={HardDrivesIcon} shape="square" size="extraSmall" />;

    // Argus / Atlas fallbacks
    if (tagsList?.some(t => t.name === "gen-ai")) return <Icon source={tagsList.some(t => t.name === "AI Agent") ? AutomationMajor : MagicMajor} color="base" />;
    if (tagsList?.some(t => t.name === "mcp-server")) return <Avatar source={MCPIcon} shape="square" size="extraSmall" />;
    if (tagsList?.some(t => t.name === "browser-llm")) return <Avatar source={LaptopIcon} shape="square" size="extraSmall" />;
    return <Avatar source={displayName?.toLowerCase().startsWith('mcp') ? MCPIcon : LaptopIcon} shape="square" size="extraSmall" />;
});

export { CollectionIcon };
export default CollectionIcon;
