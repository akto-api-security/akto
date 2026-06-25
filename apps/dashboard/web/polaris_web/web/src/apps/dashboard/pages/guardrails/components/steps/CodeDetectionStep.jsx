import { VerticalStack, Text } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import ConfidenceDropdown, { LevelDropdown } from "../ConfidenceDropdown";

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
                <LevelDropdown
                    id="code-filter"
                    title={<RuleLabelWithTag name="Code detection filter" threats={RULE_OWASP_THREATS.codeFilter} />}
                    helpText="Enable language-specific code detection that identifies and blocks code in specific programming languages (Python, Java, JavaScript, etc.). Provides granular control over which programming languages to allow or block."
                    enabled={enableCodeFilter}
                    level={codeFilterLevel}
                    onChange={({ enabled, level }) => {
                        setEnableCodeFilter(enabled);
                        if (level != null) setCodeFilterLevel(level);
                    }}
                />

                <ConfidenceDropdown
                    id="ban-code-detection"
                    title={<RuleLabelWithTag name="Ban code detection" threats={RULE_OWASP_THREATS.banCode} />}
                    helpText="Enable binary code detection that blocks all code regardless of programming language. This is a simple, strict filter that treats any code as a violation without language-specific filtering."
                    enabled={enableBanCode}
                    score={banCodeConfidenceScore}
                    onChange={({ enabled, confidenceScore }) => {
                        setEnableBanCode(enabled);
                        if (confidenceScore != null) setBanCodeConfidenceScore(confidenceScore);
                    }}
                />
            </VerticalStack>
        </VerticalStack>
    );
};

export default CodeDetectionStep;
