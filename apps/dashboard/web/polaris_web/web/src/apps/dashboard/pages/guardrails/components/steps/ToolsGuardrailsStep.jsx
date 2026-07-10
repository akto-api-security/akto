import { VerticalStack, Text, Checkbox, Box, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import { TOOLS_GUARDRAILS_DESCRIPTIONS } from "../../guardrailDescriptions";

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
    onTryPrompt,
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
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Tool Misuse" threats={RULE_OWASP_THREATS.toolMisuse} />
                                <ControlInfoIcon
                                    description={TOOLS_GUARDRAILS_DESCRIPTIONS.toolMisuse.description}
                                    examples={TOOLS_GUARDRAILS_DESCRIPTIONS.toolMisuse.examples}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableToolMisuse}
                        onChange={setEnableToolMisuse}
                        helpText="Detect and block unauthorized or malicious use of tools by agents."
                    />
                </Box>
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Detect Malicious Tools" threats={RULE_OWASP_THREATS.maliciousTools} />
                                <ControlInfoIcon
                                    description={TOOLS_GUARDRAILS_DESCRIPTIONS.maliciousTools.description}
                                    examples={TOOLS_GUARDRAILS_DESCRIPTIONS.maliciousTools.examples}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableMaliciousTools}
                        onChange={setEnableMaliciousTools}
                        helpText="Detect and block tools that exhibit malicious behavior or intent."
                    />
                </Box>
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <RuleLabelWithTag name="Detect Tool name and description mismatch" threats={RULE_OWASP_THREATS.toolNameDescriptionMismatch} />
                                <ControlInfoIcon
                                    description={TOOLS_GUARDRAILS_DESCRIPTIONS.toolNameDescriptionMismatch.description}
                                    examples={TOOLS_GUARDRAILS_DESCRIPTIONS.toolNameDescriptionMismatch.examples}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableToolNameDescriptionMismatch}
                        onChange={setEnableToolNameDescriptionMismatch}
                        helpText="Detect when tool usage does not match the declared name or description."
                    />
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default ToolsGuardrailsStep;
