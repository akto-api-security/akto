import { VerticalStack, Text, Checkbox, HorizontalStack, Box } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import ControlInfoIcon from "../ControlInfoIcon";
import { LANGUAGE_SAFETY_DESCRIPTIONS } from "../../guardrailDescriptions";
import Store from "../../../../store";

// nginx-demo and ironheart accounts
const LANGUAGE_SAFETY_BETA_ACCOUNT_IDS = ['1726615470', '1000000', '1669322524'];

export const LanguageSafetyConfig = {
    number: 3,
    title: "Language Safety & Abuse Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableGibberishDetection, enableSentiment, enableMultiLingualBlock }) => {
        const filters = [];
        if (enableGibberishDetection) filters.push('Gibberish detection');
        if (enableSentiment) filters.push('Sentiment detection');
        if (enableMultiLingualBlock) filters.push('Multi-lingual prompts blocked');
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
    // Multi-lingual prompts
    enableMultiLingualBlock,
    setEnableMultiLingualBlock,
}) => {
    const activeAccount = Store(state => state.activeAccount);
    const isBetaEnabled = LANGUAGE_SAFETY_BETA_ACCOUNT_IDS.includes(String(activeAccount));

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
                                    onEnable={() => setEnableGibberishDetection(true)}
                                />
                            </HorizontalStack>
                        }
                        checked={enableGibberishDetection}
                        onChange={setEnableGibberishDetection}
                        helpText="Detect and block gibberish or nonsensical text in user inputs. This helps prevent meaningless prompts that could confuse the AI or be used as attack vectors."
                    />
                </Box>

                {isBetaEnabled && (
                    <>
                    {/* Sentiment Detection */}
                    <Box>
                        <Checkbox
                            label={
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text as="span">Enable sentiment detection</Text>
                                    <ControlInfoIcon
                                        {...LANGUAGE_SAFETY_DESCRIPTIONS.sentimentDetection}
                                        onTryPrompt={onTryPrompt}
                                        onEnable={() => setEnableSentiment(true)}
                                    />
                                </HorizontalStack>
                            }
                            checked={enableSentiment}
                            onChange={setEnableSentiment}
                            helpText="Analyze user input to detect negative, toxic, or inappropriate content."
                        />
                    </Box>

                    {/* Multi-lingual Prompts */}
                    <Box>
                        <Checkbox
                            label={
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text as="span">Multi-lingual prompts are not allowed</Text>
                                    <ControlInfoIcon
                                        {...LANGUAGE_SAFETY_DESCRIPTIONS.multiLingualBlock}
                                        onTryPrompt={onTryPrompt}
                                        onEnable={() => setEnableMultiLingualBlock(true)}
                                    />
                                </HorizontalStack>
                            }
                            checked={enableMultiLingualBlock}
                            onChange={setEnableMultiLingualBlock}
                            helpText="Block prompts written in languages other than English to help prevent guardrail bypass."
                        />
                    </Box>
                    </>
                )}
            </VerticalStack>
        </VerticalStack>
    );
};

export default LanguageSafetyStep;
