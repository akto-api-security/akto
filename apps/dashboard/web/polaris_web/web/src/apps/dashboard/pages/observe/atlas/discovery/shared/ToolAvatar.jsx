import { Box, HorizontalStack, Text } from "@shopify/polaris";
import AssetIcon from "@/apps/dashboard/pages/observe/agentic/AssetIcon";
import { getDomainForFavicon } from "@/apps/dashboard/pages/observe/agentic/mcpClientHelper";

const SIZE_PX = { large: 40, medium: 32, small: 28 };
const ICON_PX = { large: 24, medium: 20, small: 18 };
const ICON_TYPES = ["MCP Server", "Skill", "Plugin"];

// Letters that start each word of the name, so "postgres-mcp (community)" becomes "PM".
function initialsOf(name) {
    const words = (name || "").replace(/[^A-Za-z0-9]+/g, " ").trim().split(" ").filter(Boolean);
    const letters = words.length > 1 ? words.map((word) => word[0]).join("") : (words[0] || "?");
    return letters.slice(0, 2).toUpperCase();
}

// Same icons as the agentic assets table: type glyphs and a vendor favicon.
const hasIcon = (tool) => ICON_TYPES.includes(tool.clientType) || !!getDomainForFavicon(tool.groupKey);

// Grey rounded tile for a tool: its icon when known, otherwise its initials.
export default function ToolAvatar({ tool, size = "medium" }) {
    return (
        <Box background="bg-subdued" borderRadius="2" minWidth={`${SIZE_PX[size]}px`} minHeight={`${SIZE_PX[size]}px`}>
            <HorizontalStack align="center" blockAlign="center">
                {hasIcon(tool) ? (
                    <Box paddingBlockStart={size === "small" ? "1" : "2"}>
                        <AssetIcon type={tool.clientType} assetTagValue={tool.groupKey} size={ICON_PX[size]} />
                    </Box>
                ) : (
                    <Box paddingBlockStart={size === "small" ? "1" : size === "large" ? "3" : "2"}>
                        <Text as="span" variant="bodySm" fontWeight="bold" color="subdued">{initialsOf(tool.name)}</Text>
                    </Box>
                )}
            </HorizontalStack>
        </Box>
    );
}
