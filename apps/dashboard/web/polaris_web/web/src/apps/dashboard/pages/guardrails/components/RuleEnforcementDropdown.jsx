import { HorizontalStack, VerticalStack, Text, Box, Banner } from "@shopify/polaris";
import Dropdown from "../../../components/layouts/Dropdown";
import GuardrailEnforcementInfoIcon from "./GuardrailEnforcementInfoIcon";
import { GUARDRAIL_BEHAVIOUR, GUARDRAIL_BEHAVIOUR_OPTIONS, normalizeBehaviourValue } from "../utils";
import { isEndpointSecurityCategory, isAgenticSecurityCategory } from "@/apps/main/labelHelper";
import Store from "../../../store";

const WARN_ENABLED_ACCOUNT_IDS = ['1726615470', '1000000'];

export default function RuleEnforcementDropdown({
    id,
    value,
    onChange,
    label = "Rule behaviour",
    disabled = false,
}) {
    const initial = normalizeBehaviourValue(value);
    const isAtlas = isEndpointSecurityCategory();
    const isArgus = isAgenticSecurityCategory();
    const activeAccount = Store(state => state.activeAccount);
    const isWarnEnabled = WARN_ENABLED_ACCOUNT_IDS.includes(String(activeAccount));
    // "Approval" (Atlas server-preapproval) is Endpoint-only; "Human Approval" (Argus per-call
    // pending/poll) is Agentic-only — each category only ever sees its own option.
    const menuItems = GUARDRAIL_BEHAVIOUR_OPTIONS.filter((o) => {
        if (o.value === GUARDRAIL_BEHAVIOUR.APPROVAL) return isAtlas;
        if (o.value === GUARDRAIL_BEHAVIOUR.HUMAN_APPROVAL) return isArgus;
        if (o.value === GUARDRAIL_BEHAVIOUR.WARN) return isAtlas && isWarnEnabled;
        return true;
    });
    const showLabelRow = typeof label === "string" && label.trim().length > 0;
    const showEndpointOnlyNote = isAtlas && initial === GUARDRAIL_BEHAVIOUR.APPROVAL;
    const showWarnNote = initial === GUARDRAIL_BEHAVIOUR.WARN;

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
                <Banner status="info">
                    <Text variant="bodyMd">
                        In the browser extension, <Text as="span" fontWeight="bold">{menuItems.find((o) => o.value === initial)?.label}</Text> behaves the same as <Text as="span" fontWeight="bold">Block</Text>.
                    </Text>
                </Banner>
            )}
            {showWarnNote && (
                <Banner tone="info">
                    <Text variant="bodyMd">
                        Warn currently applies only to input prompts submitted by the user - a flagged prompt is held, and resending it unchanged lets it through.
                        On Claude CLI, Copilot CLI, and VS Code, a flagged tool call prompts you to explicitly allow it before it runs.
                        Output messages and internal responses generated inside agent aren't supported by Warn yet - these are blocked instead.
                    </Text>
                </Banner>
            )}
        </VerticalStack>
    );
}
