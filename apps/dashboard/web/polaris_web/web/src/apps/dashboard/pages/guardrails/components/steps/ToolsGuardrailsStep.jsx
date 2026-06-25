import { VerticalStack, Text } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import { EnableDropdown } from "../ConfidenceDropdown";

export const ToolsGuardrailsConfig = {
    number: 9,
    title: "Tools Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableToolMisuse, enableMaliciousTools, enableToolNameDescriptionMismatch }) => {
        const options = [];
        if (enableToolMisuse) options.push("Tool Misuse");
        if (enableMaliciousTools) options.push("Malicious Tools");
        if (enableToolNameDescriptionMismatch) options.push("Tool name/description mismatch");
        return options.length > 0 ? options.join(", ") : null;
    }
};

const ToolsGuardrailsStep = ({
    enableToolMisuse,
    setEnableToolMisuse,
    enableMaliciousTools,
    setEnableMaliciousTools,
    enableToolNameDescriptionMismatch,
    setEnableToolNameDescriptionMismatch
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure guardrails to detect and prevent tool misuse and exploitation in agentic applications.
            </Text>
            <OwaspTag stepNumber={9} />
            <VerticalStack gap="4">
                <EnableDropdown
                    id="tool-misuse"
                    title={<RuleLabelWithTag name="Tool Misuse" threats={RULE_OWASP_THREATS.toolMisuse} />}
                    helpText="Detect and block unauthorized or malicious use of tools by agents."
                    enabled={enableToolMisuse}
                    onChange={setEnableToolMisuse}
                />
                <EnableDropdown
                    id="malicious-tools"
                    title={<RuleLabelWithTag name="Detect Malicious Tools" threats={RULE_OWASP_THREATS.maliciousTools} />}
                    helpText="Detect and block tools that exhibit malicious behavior or intent."
                    enabled={enableMaliciousTools}
                    onChange={setEnableMaliciousTools}
                />
                <EnableDropdown
                    id="tool-name-mismatch"
                    title={<RuleLabelWithTag name="Detect Tool name and description mismatch" threats={RULE_OWASP_THREATS.toolNameDescriptionMismatch} />}
                    helpText="Detect when tool usage does not match the declared name or description."
                    enabled={enableToolNameDescriptionMismatch}
                    onChange={setEnableToolNameDescriptionMismatch}
                />
            </VerticalStack>
        </VerticalStack>
    );
};

export default ToolsGuardrailsStep;
