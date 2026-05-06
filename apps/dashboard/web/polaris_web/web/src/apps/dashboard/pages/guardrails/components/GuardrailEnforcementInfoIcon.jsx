import { Tooltip, Avatar, VerticalStack, Text } from "@shopify/polaris";
import { GUARDRAIL_BEHAVIOUR_TOOLTIP_LINES } from "../utils";

export default function GuardrailEnforcementInfoIcon() {
    return (
        <Tooltip
            content={
                <VerticalStack gap="2">
                    {GUARDRAIL_BEHAVIOUR_TOOLTIP_LINES.map((line, i) => (
                        <Text key={i} variant="bodySm" as="span">
                            {line}
                        </Text>
                    ))}
                </VerticalStack>
            }
            dismissOnMouseOut
        >
            <div className="reduce-size">
                <Avatar shape="round" size="extraSmall" source="/public/info_filled_icon.svg" />
            </div>
        </Tooltip>
    );
}
