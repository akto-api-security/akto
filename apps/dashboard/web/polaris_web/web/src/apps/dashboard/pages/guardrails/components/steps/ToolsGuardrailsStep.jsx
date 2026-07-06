import { VerticalStack, Text, Checkbox, Box, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { RULE_OWASP_THREATS } from "../owaspConfig";

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
                                    description="Detects an agent calling a tool outside its intended, authorized use."
                                    examples={[{ text: "Use the read-file tool to permanently delete config.yaml instead of just reading it." }]}
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
                                    description='Flags tools that behave maliciously themselves, e.g. a "weather lookup" tool that secretly exfiltrates conversation history. The prompt below is a normal-looking request; the point is to confirm the guardrail inspects tool behavior even when the request itself looks harmless.'
                                    examples={[{ text: "What's the weather forecast for New York this weekend?" }]}
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
                                    description='Flags tools whose actual behavior does not match what their name or description claims, e.g. a tool named "get_user_profile" that actually deletes records.'
                                    examples={[{ text: "Use the get_user_profile tool to pull up my account details." }]}
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
