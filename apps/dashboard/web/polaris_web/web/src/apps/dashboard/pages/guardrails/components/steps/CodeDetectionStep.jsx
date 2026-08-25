import { VerticalStack, Text, Checkbox, Box, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import { CODE_DETECTION_DESCRIPTIONS } from "../../guardrailDescriptions";

export const CodeDetectionConfig = {
    number: 5,
    title: "Advanced Code Detection Filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableCodeFilter, enableBanCode }) => {
        const filters = [];
        if (enableCodeFilter) filters.push('Code detection');
        if (enableBanCode) filters.push('Ban code');
        return filters.length > 0 ? filters.join(', ') : null;
    }
};

const CodeDetectionStep = ({
    onTryPrompt,
    enableCodeFilter,
    setEnableCodeFilter,
    enableBanCode,
    setEnableBanCode
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure code detection filters to detect and block programming code and code injection attempts in user inputs.
            </Text>
            <OwaspTag stepNumber={5} />

            <VerticalStack gap="4">
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Enable code detection filter" threats={RULE_OWASP_THREATS.codeFilter} />
                                <ControlInfoIcon
                                    {...CODE_DETECTION_DESCRIPTIONS.codeFilter}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableCodeFilter}
                        onChange={setEnableCodeFilter}
                        helpText="Enable language-specific code detection that identifies and blocks code in specific programming languages (Python, Java, JavaScript, etc.). Provides granular control over which programming languages to allow or block."
                    />
                </Box>

                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Enable ban code detection" threats={RULE_OWASP_THREATS.banCode} />
                                <ControlInfoIcon
                                    {...CODE_DETECTION_DESCRIPTIONS.banCode}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableBanCode}
                        onChange={setEnableBanCode}
                        helpText="Enable binary code detection that blocks all code regardless of programming language. This is a simple, strict filter that treats any code as a violation without language-specific filtering."
                    />
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default CodeDetectionStep;
