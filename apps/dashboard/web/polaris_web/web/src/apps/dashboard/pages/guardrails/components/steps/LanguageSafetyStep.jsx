import { VerticalStack, Text, Checkbox, HorizontalStack, Box } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { LANGUAGE_SAFETY_DESCRIPTIONS } from "../../guardrailDescriptions";

export const LanguageSafetyConfig = {
    number: 3,
    title: "Language Safety & Abuse Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableGibberishDetection, enableSentiment }) => {
        const filters = [];
        if (enableGibberishDetection) filters.push('Gibberish detection');
        if (enableSentiment) filters.push('Sentiment');
        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const LanguageSafetyStep = ({
    onTryPrompt,
    // Gibberish detection
    enableGibberishDetection,
    setEnableGibberishDetection,
    // Sentiment detection
    enableSentiment,
    setEnableSentiment,
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure language safety filters to detect gibberish and inappropriate sentiment.
            </Text>
            <OwaspTag stepNumber={3} />

            <VerticalStack gap="4">
                {/* Gibberish Detection */}
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">Enable gibberish detection</Text>
                                <ControlInfoIcon
                                    {...LANGUAGE_SAFETY_DESCRIPTIONS.gibberishDetection}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableGibberishDetection}
                        onChange={setEnableGibberishDetection}
                        helpText="Detect and block gibberish or nonsensical text in user inputs. This helps prevent meaningless prompts that could confuse the AI or be used as attack vectors."
                    />
                </Box>

                {/* Sentiment Detection */}
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">Enable sentiment detection</Text>
                                <ControlInfoIcon
                                    {...LANGUAGE_SAFETY_DESCRIPTIONS.sentimentDetection}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={enableSentiment}
                        onChange={setEnableSentiment}
                        helpText="Analyze sentiment in user inputs to detect negative, toxic, or inappropriate emotional content."
                    />
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default LanguageSafetyStep;
