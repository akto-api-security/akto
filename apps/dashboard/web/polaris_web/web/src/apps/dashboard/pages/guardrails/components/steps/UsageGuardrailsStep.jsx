import { VerticalStack, Text } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import ConfidenceDropdown from "../ConfidenceDropdown";

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
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitConfidenceScore,
    setTokenLimitConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure usage-based guardrails to control token limits and prevent abuse.
            </Text>
            <OwaspTag stepNumber={7} />

            <VerticalStack gap="4">
                <ConfidenceDropdown
                    id="token-limit-detection"
                    title={<RuleLabelWithTag name="Token limit detection" threats={RULE_OWASP_THREATS.tokenLimit} />}
                    helpText="Detect when user inputs exceed token limits and block overly long inputs."
                    enabled={enableTokenLimit}
                    score={tokenLimitConfidenceScore}
                    onChange={({ enabled, confidenceScore }) => {
                        setEnableTokenLimit(enabled);
                        if (confidenceScore != null) setTokenLimitConfidenceScore(confidenceScore);
                    }}
                />
            </VerticalStack>
        </VerticalStack>
    );
};

export default UsageGuardrailsStep;

