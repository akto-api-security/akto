import { Icon, Tooltip, Text } from "@shopify/polaris"
import { CircleTickMajor } from "@shopify/polaris-icons"

const AllowlistBadge = () => {
    return (
        <Tooltip content={<Text variant="bodySm">Verified — present in your MCP allowlist</Text>} preferredPosition="above">
            <span style={{ display: 'inline-flex', alignItems: 'center', fill: '#008060' }}>
                <Icon source={CircleTickMajor} tone="success" />
            </span>
        </Tooltip>
    );
};

export default AllowlistBadge;
