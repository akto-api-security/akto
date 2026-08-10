import { VerticalStack, Text, Checkbox, HorizontalStack, Box, RangeSlider } from "@shopify/polaris";
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
    gibberishConfidenceScore,
    setGibberishConfidenceScore,
    // Sentiment detection
    enableSentiment,
    setEnableSentiment,
    sentimentConfidenceScore,
    setSentimentConfidenceScore,
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
                    {enableGibberishDetection && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                    <ControlInfoIcon
                                        {...LANGUAGE_SAFETY_DESCRIPTIONS.gibberishConfidenceThreshold}
                                        onTryPrompt={onTryPrompt}
                                    />
                                </HorizontalStack>
                                <RangeSlider
                                    label=""
                                    value={gibberishConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setGibberishConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting gibberish."
                                />
                            </VerticalStack>
                        </Box>
                    )}
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
                    {enableSentiment && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                    <ControlInfoIcon
                                        {...LANGUAGE_SAFETY_DESCRIPTIONS.sentimentConfidenceThreshold}
                                        onTryPrompt={onTryPrompt}
                                    />
                                </HorizontalStack>
                                <RangeSlider
                                    label=""
                                    value={sentimentConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setSentimentConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting negative sentiment."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default LanguageSafetyStep;
