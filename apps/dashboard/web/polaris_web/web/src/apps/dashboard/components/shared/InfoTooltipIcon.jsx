import { Box, Icon, Tooltip } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";

// Tooltip's own `width` prop is only 'default' | 'wide', so a fixed measure comes from constraining
// the content instead.
const TOOLTIP_WIDTH = "200px";

export default function InfoTooltipIcon({ content }) {
    if (!content) return null;
    return (
        <Tooltip content={<Box maxWidth={TOOLTIP_WIDTH}>{content}</Box>} dismissOnMouseOut>
            {/* color, not tone: `tone` is Polaris v12 and silently does nothing on v11's Icon. */}
            <Icon source={InfoMinor} color="base" />
        </Tooltip>
    );
}
