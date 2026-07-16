import { HorizontalStack, VerticalStack, Text, Box, Banner } from "@shopify/polaris";
import Dropdown from "../../../components/layouts/Dropdown";
import GuardrailEnforcementInfoIcon from "./GuardrailEnforcementInfoIcon";
import { GUARDRAIL_BEHAVIOUR, GUARDRAIL_BEHAVIOUR_OPTIONS, normalizeBehaviourValue } from "../utils";
import { isEndpointSecurityCategory } from "@/apps/main/labelHelper";

export default function RuleEnforcementDropdown({
    id,
    value,
    onChange,
    label = "Rule behaviour",
    disabled = false,
}) {
    const initial = normalizeBehaviourValue(value);
    // "Approval" behaviour is Endpoint (Atlas) only — hide the option for Agentic/other categories.
    const menuItems = isEndpointSecurityCategory()
        ? GUARDRAIL_BEHAVIOUR_OPTIONS
        : GUARDRAIL_BEHAVIOUR_OPTIONS.filter((o) => o.value !== GUARDRAIL_BEHAVIOUR.APPROVAL);
    const showLabelRow = typeof label === "string" && label.trim().length > 0;
    const showEndpointOnlyNote = initial === GUARDRAIL_BEHAVIOUR.ALERT
        || initial === GUARDRAIL_BEHAVIOUR.APPROVAL;

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
                    menuItems={menuItems}
                    initial={initial}
                    disabled={disabled}
                    selected={onChange}
                />
            </Box>
            {showEndpointOnlyNote && (
                <Banner tone="info">
                    <Text variant="bodyMd">
                        In the browser extension, <Text as="span" fontWeight="bold">Alert</Text> and <Text as="span" fontWeight="bold">Human Approval</Text> behave the same as <Text as="span" fontWeight="bold">Block</Text>.
                    </Text>
                </Banner>
            )}
        </VerticalStack>
    );
}
