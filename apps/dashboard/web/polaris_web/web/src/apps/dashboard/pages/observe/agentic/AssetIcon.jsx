import React from "react";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import SkillIcon from "@/assets/Skill.svg";
import { getDomainForFavicon } from "./mcpClientHelper";

export default function AssetIcon({ type, assetTagValue }) {
    const size = 20;
    if (type === "MCP Server")
        return <img src={MCPIcon} width={size} height={size} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    if (type === "Skill")
        return <img src={SkillIcon} width={size} height={size} alt="" style={{ flexShrink: 0 }} />;
    const domain = getDomainForFavicon(assetTagValue);
    if (domain)
        return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={size} height={size} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    return <img src={LaptopIcon} width={size} height={size} alt="" style={{ flexShrink: 0, opacity: 0.7 }} />;
}
