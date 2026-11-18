import React from "react";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import SkillIcon from "@/assets/Skill.svg";
import PluginIcon from "@/assets/Plugin.svg";
import { getDomainForFavicon } from "./mcpClientHelper";

const faviconSrc = (assetTagValue) => {
    const domain = getDomainForFavicon(assetTagValue);
    return domain ? `https://www.google.com/s2/favicons?domain=${domain}&sz=64` : null;
};

// Image src for an asset where only a src can be passed (e.g. topology graph nodes) — same choice
// as <AssetIcon>; null when there's no specific icon.
export function assetIconSrc(type, assetTagValue) {
    if (type === "MCP Server") return MCPIcon;
    if (type === "Skill") return SkillIcon;
    if (type === "Plugin") return PluginIcon;
    return faviconSrc(assetTagValue);
}

export default function AssetIcon({ type, assetTagValue, size = 20 }) {
    if (type === "MCP Server")
        return <img src={MCPIcon} width={size} height={size} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    if (type === "Skill")
        return <img src={SkillIcon} width={size} height={size} alt="" style={{ flexShrink: 0 }} />;
    // Explicit branch also stops a plugin name being fed to the favicon lookup below.
    if (type === "Plugin")
        return <img src={PluginIcon} width={size} height={size} alt="" style={{ flexShrink: 0 }} />;
    if (type === "OS") {
        if (assetTagValue === "mac")     return <img src="/public/os-mac.svg"     width={size} height={size} alt="macOS"   style={{ flexShrink: 0 }} />;
        if (assetTagValue === "windows") return <img src="/public/os-windows.svg" width={size} height={size} alt="Windows" style={{ flexShrink: 0 }} />;
        if (assetTagValue === "linux")   return <img src="/public/os-linux.svg"   width={size} height={size} alt="Linux"   style={{ flexShrink: 0 }} />;
        return <img src={LaptopIcon} width={size} height={size} alt="Device" style={{ flexShrink: 0, opacity: 0.7 }} />;
    }
    const favicon = faviconSrc(assetTagValue);
    if (favicon)
        return <img src={favicon} width={size} height={size} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    return <img src={LaptopIcon} width={size} height={size} alt="" style={{ flexShrink: 0, opacity: 0.7 }} />;
}
