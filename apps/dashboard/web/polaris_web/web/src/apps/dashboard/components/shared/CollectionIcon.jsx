import React from "react";
import { Icon } from "@shopify/polaris";
import { AutomationMajor, MagicMajor } from '@shopify/polaris-icons';
import IconCacheService from "@/services/IconCacheService";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import { getDomainForFavicon } from "../../pages/observe/agentic/mcpClientHelper";

export const ICON_STYLE = { width: '20px', height: '20px', borderRadius: '2px' };
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
            if (data && mounted) setIconData(data);
        })();
        return () => { mounted = false; };
    }, [hostName, assetTagValue]);

    if (iconData) return <img src={`data:image/png;base64,${iconData}`} alt={`${displayName} icon`} style={ICON_STYLE} onError={() => setIconData(null)} />;
    if (faviconUrl) return <img src={faviconUrl} alt={`${displayName} icon`} style={ICON_STYLE} onError={() => setFaviconUrl(null)} />;
    
    if (tagsList?.some(t => t.name === "gen-ai")) return <Icon source={tagsList.some(t => t.name === "AI Agent") ? AutomationMajor : MagicMajor} color="base" />;
    if (tagsList?.some(t => t.name === "mcp-server")) return <img src={MCPIcon} alt="MCP" style={ICON_STYLE} />;
    return <img src={displayName?.toLowerCase().startsWith('mcp') ? MCPIcon : LaptopIcon} alt="icon" style={ICON_STYLE} />;
});

export { CollectionIcon };
export default CollectionIcon;
