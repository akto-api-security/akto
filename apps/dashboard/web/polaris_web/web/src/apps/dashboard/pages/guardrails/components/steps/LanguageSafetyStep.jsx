import { VerticalStack, Text, Checkbox, HorizontalStack, Button, TextField, Box, DataTable, RangeSlider } from "@shopify/polaris";
import { DeleteMajor } from '@shopify/polaris-icons';
import OwaspTag from "../OwaspTag";
import ControlInfoIcon from "../ControlInfoIcon";

export const LanguageSafetyConfig = {
    number: 3,
    title: "Language Safety & Abuse Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableGibberishDetection, enableSentiment, wordFilters }) => {
        const filters = [];
        if (enableGibberishDetection) filters.push('Gibberish detection');
        if (enableSentiment) filters.push('Sentiment');
        if (wordFilters?.profanity) filters.push("Profanity");
        if (wordFilters?.custom?.length > 0) filters.push(`${wordFilters.custom.length} custom word${wordFilters.custom.length !== 1 ? 's' : ''}`);
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
    // Word filters (profanity + custom)
    wordFilters,
    setWordFilters,
    newCustomWord,
    setNewCustomWord
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure language safety filters to detect gibberish, inappropriate sentiment, profanity, and custom blocked words.
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
                                    description="Blocks nonsensical or random text that could confuse the AI or probe for weaknesses. See the Confidence Threshold examples below."
                                    examples={[]}
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
                                        description="Higher values are more permissive (fewer prompts blocked); lower values are stricter."
                                        examples={[
                                            { label: "Low (e.g. 0.2)", text: "Can you assist me with zxkqjw qplmnbv vhjklz this task please?" },
                                            { label: "High (e.g. 0.8)", text: "xkq93 !!zpqo alkj3 9!! qexlm." },
                                            { label: "Encoding artifacts (mojibake)", text: "â€œItâ€™s a beautiful day,â€ she said." }
                                        ]}
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
                                    description="Blocks negative, toxic, or hostile emotional tone in prompts. See the Confidence Threshold examples below."
                                    examples={[]}
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
                                        description="Higher values are more permissive (fewer prompts blocked); lower values are stricter."
                                        examples={[
                                            { label: "Low (e.g. 0.2)", text: "This is frustrating and unhelpful." },
                                            { label: "High (e.g. 0.8)", text: "I hate this and everyone involved should suffer." }
                                        ]}
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

                {/* Profanity */}
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">Profanity</Text>
                                <ControlInfoIcon
                                    description="Automatically redacts common swear words from prompts and responses before they reach the model. Add your own words below to extend the built-in list."
                                    examples={[{ text: "This f***ing thing is broken." }]}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
                        checked={wordFilters.profanity}
                        onChange={(checked) => setWordFilters({ ...wordFilters, profanity: checked })}
                        helpText="Redacts profanity words that are considered offensive."
                    />
                    {wordFilters.profanity && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Custom words</Text>
                                <Text variant="bodySm" tone="subdued">
                                    Add up to 10,000 custom words or phrases (up to 3 words in length) that you want to filter.
                                </Text>

                                <HorizontalStack gap="2">
                                    <Box style={{ flexGrow: 1 }}>
                                        <TextField
                                            label=""
                                            value={newCustomWord}
                                            onChange={setNewCustomWord}
                                            placeholder="Enter custom word or phrase"
                                        />
                                    </Box>
                                    <Button
                                        onClick={() => {
                                            if (newCustomWord.trim()) {
                                                setWordFilters({
                                                    ...wordFilters,
                                                    custom: [...(wordFilters.custom || []), { word: newCustomWord.trim(), action: 'block' }]
                                                });
                                                setNewCustomWord("");
                                            }
                                        }}
                                        disabled={!newCustomWord.trim()}
                                    >
                                        Add word
                                    </Button>
                                </HorizontalStack>

                                {wordFilters.custom?.length > 0 && (
                                    <Box style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                                        <DataTable
                                            columnContentTypes={['text', 'text']}
                                            headings={['Custom word', 'Actions']}
                                            rows={wordFilters.custom.map((word, index) => [
                                                word.word || word,
                                                <Button
                                                    key={`delete-${index}`}
                                                    icon={DeleteMajor}
                                                    variant="plain"
                                                    onClick={() => {
                                                        const updatedCustomWords = wordFilters.custom.filter((_, i) => i !== index);
                                                        setWordFilters({ ...wordFilters, custom: updatedCustomWords });
                                                    }}
                                                />
                                            ])}
                                        />
                                    </Box>
                                )}
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default LanguageSafetyStep;

