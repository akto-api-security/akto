import { HorizontalStack, VerticalStack, Text, Box } from "@shopify/polaris";
import Dropdown from "../../../components/layouts/Dropdown";
import GuardrailEnforcementInfoIcon from "./GuardrailEnforcementInfoIcon";
import { GUARDRAIL_BEHAVIOUR_OPTIONS, normalizeBehaviourValue } from "../utils";

export default function RuleEnforcementDropdown({
    id,
    value,
    onChange,
    label = "Rule behaviour",
    disabled = false,
}) {
    const initial = normalizeBehaviourValue(value);
    const showLabelRow = typeof label === "string" && label.trim().length > 0;

    return (
        <VerticalStack gap="2">
            {showLabelRow && (
                <HorizontalStack gap="1" blockAlign="center">
                    <Text as="span" variant="bodyMd" fontWeight="medium">
                        {label}
                    </Text>
                    <GuardrailEnforcementInfoIcon />
                </HorizontalStack>
            )}
            <Box minWidth="200px">
                <Dropdown
                    id={id}
                    menuItems={GUARDRAIL_BEHAVIOUR_OPTIONS}
                    initial={initial}
                    disabled={disabled}
                    selected={onChange}
                />
            </Box>
        </VerticalStack>
    );
}
