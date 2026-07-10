import { VerticalStack, Text, Checkbox, Box, TextField, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import { USAGE_GUARDRAILS_DESCRIPTIONS } from "../../guardrailDescriptions";

export const UsageGuardrailsConfig = {
    number: 7,
    title: "Usage based Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableTokenLimit }) => {
        const scanners = [];
        if (enableTokenLimit) {
            scanners.push('Token Limit');
        }
        return scanners.length > 0 ? scanners.join(', ') : null;
    }
};

const UsageGuardrailsStep = ({
    onTryPrompt,
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitThreshold,
    setTokenLimitThreshold
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure usage-based guardrails to control token limits and prevent abuse.
            </Text>
            <OwaspTag stepNumber={7} />

            <VerticalStack gap="4">
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Enable token limit detection" threats={RULE_OWASP_THREATS.tokenLimit} />
                                <ControlInfoIcon
                                    description={USAGE_GUARDRAILS_DESCRIPTIONS.tokenLimit.description}
                                    examples={USAGE_GUARDRAILS_DESCRIPTIONS.tokenLimit.examples}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableTokenLimit}
                        onChange={setEnableTokenLimit}
                        helpText="Block or alert when a prompt exceeds the specified token count per message."
                    />
                    {enableTokenLimit && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <TextField
                                    label="Max tokens per prompt"
                                    type="number"
                                    value={String(tokenLimitThreshold)}
                                    onChange={(val) => setTokenLimitThreshold(Math.min(10000000, Math.max(1, parseInt(val, 10) || 1)))}
                                    min={1}
                                    max={10000000}
                                    helpText="Prompts exceeding this token count will be blocked (or alerted, depending on policy behaviour). Tokens are estimated as characters ÷ 4."
                                    autoComplete="off"
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default UsageGuardrailsStep;

