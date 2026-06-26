import { Icon, Tooltip } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";

export default function InfoTooltipIcon({ content }) {
    if (!content) return null;
    return (
        <Tooltip content={content} dismissOnMouseOut>
            <Icon source={InfoMinor} tone="subdued" />
        </Tooltip>
    );
}
