import { Box, Icon, Link, Text, Tooltip, VerticalStack } from "@shopify/polaris"
import { CircleTickMajor } from "@shopify/polaris-icons"

const RegistryBadge = () => {
    const tooltipContent = (
        <Box maxWidth="280px">
            <VerticalStack gap="2">
                <Text as="span" fontWeight="bold">
                    Listed on the MCP Registry
                </Text>
                <Text as="span" variant="bodySm" tone="subdued">
                    This MCP server appears in the official Model Context Protocol Registry.
                </Text>
                <Text as="span" variant="bodySm" tone="subdued">
                    The registry is a community-maintained directory for MCP-compatible servers.
                </Text>
                <Box as="span">
                    <Link 
                        url="https://registry.modelcontextprotocol.io/docs#/operations/list-servers-v0.1"
                        target="_blank"
                        onClick={(e) => { e.stopPropagation(); }}
                    >
                        View on MCP Registry →
                    </Link>
                </Box>
            </VerticalStack>
        </Box>
    );
    
    return (
        <Tooltip content={tooltipContent} preferredPosition="above" width="wide">
            <Box as="span" display="inlineFlex">
                <Icon source={CircleTickMajor} tone="success" />
            </Box>
        </Tooltip>
    );
};

export default RegistryBadge;

