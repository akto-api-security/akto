import React from "react";
import { Badge, Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import DetailGrid from "./DetailGrid";

// Plugins are discovery-only: no traffic, no endpoints, no components of their own. Everything shown
// here comes off the plugin collection's own tags, already on the asset row — so no extra fetch.
export default function PluginComponentsView({ asset }) {
    // "unknown" reads as disabled — an unreported status isn't proof a plugin is active.
    const enabled = String(asset?.pluginStatus).toLowerCase() === "enabled";
    const items = [
        { label: "Version", value: asset?.pluginVersion },
        { label: "Scope", value: asset?.pluginScope },
        { label: "Marketplace", value: asset?.pluginMarketplace },
    ];
    const hasAny = asset?.pluginStatus || items.some((i) => i.value);

    if (!hasAny) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No plugin details available</Text>
                    <Text variant="bodySm" color="subdued">No metadata was reported for this plugin yet.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <Box overflowY="scroll" className="agentic-flex-fill">
            <Box paddingBlockStart="5" paddingBlockEnd="5" paddingInlineStart="5" paddingInlineEnd="5">
                <VerticalStack gap="4">
                    {asset?.pluginStatus && (
                        <HorizontalStack gap="2" blockAlign="center">
                            <Text variant="bodySm" color="subdued">Status</Text>
                            <Badge size="small" status={enabled ? "success" : "warning"}>
                                {enabled ? "enabled" : "disabled"}
                            </Badge>
                        </HorizontalStack>
                    )}
                    <DetailGrid heading="Plugin details" items={items} />
                </VerticalStack>
            </Box>
        </Box>
    );
}
