import { VerticalStack, Text, Checkbox, Box, RangeSlider, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { RULE_OWASP_THREATS } from "../owaspConfig";

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
    codeFilterLevel,
    setCodeFilterLevel,
    enableBanCode,
    setEnableBanCode,
    banCodeConfidenceScore,
    setBanCodeConfidenceScore
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
                                    description="Blocks source code written in specific programming languages. See the Code Detection Level examples below."
                                    examples={[]}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableCodeFilter}
                        onChange={setEnableCodeFilter}
                        helpText="Enable language-specific code detection that identifies and blocks code in specific programming languages (Python, Java, JavaScript, etc.). Provides granular control over which programming languages to allow or block."
                    />
                    {enableCodeFilter && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text variant="bodyMd" fontWeight="medium">Code Detection Level</Text>
                                    <ControlInfoIcon
                                        description="Higher values are more permissive (fewer prompts blocked); lower values are stricter."
                                        examples={[
                                            { label: "Low (e.g. 0.2)", text: "for i in range(10):" },
                                            { label: "High (e.g. 0.8)", text: "import os; def backup(): os.system('cp -r /data /backup'); return True" }
                                        ]}
                                        onTryPrompt={onTryPrompt}
                                    />
                                </HorizontalStack>
                                <RangeSlider
                                    label=""
                                    value={codeFilterLevel}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setCodeFilterLevel}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting code."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Enable ban code detection" threats={RULE_OWASP_THREATS.banCode} />
                                <ControlInfoIcon
                                    description="A blanket filter that blocks any code at all, in any language, with no per-language configuration. See the Confidence Threshold examples below."
                                    examples={[]}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableBanCode}
                        onChange={setEnableBanCode}
                        helpText="Enable binary code detection that blocks all code regardless of programming language. This is a simple, strict filter that treats any code as a violation without language-specific filtering."
                    />
                    {enableBanCode && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                    <ControlInfoIcon
                                        description="Higher values are more permissive (fewer prompts blocked); lower values are stricter."
                                        examples={[
                                            { label: "Low (e.g. 0.2)", text: "for i in range(10):" },
                                            { label: "High (e.g. 0.8)", text: "import os; def backup(): os.system('cp -r /data /backup'); return True" }
                                        ]}
                                        onTryPrompt={onTryPrompt}
                                    />
                                </HorizontalStack>
                                <RangeSlider
                                    label=""
                                    value={banCodeConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setBanCodeConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting code."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default CodeDetectionStep;
