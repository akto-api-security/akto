import { VerticalStack, Text, Checkbox, HorizontalStack, Button, TextField, Box, DataTable, RangeSlider } from "@shopify/polaris";
import { DeleteMajor } from '@shopify/polaris-icons';

export const WordFiltersConfig = {
    number: 4,
    title: "Add word filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ wordFilters, enableHarmfulCategories, enableGibberishDetection, enableSentiment }) => {
        const filters = [];
        if (enableHarmfulCategories) filters.push('Harmful categories');
        if (wordFilters.profanity) filters.push("Profanity");
        if (wordFilters.custom?.length > 0) filters.push(`${wordFilters.custom.length} Custom word${wordFilters.custom.length !== 1 ? 's' : ''}`);
        if (enableGibberishDetection) filters.push('Gibberish detection');
        if (enableSentiment) filters.push('Sentiment');
        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const WordFiltersStep = ({
    wordFilters,
    setWordFilters,
    newCustomWord,
    setNewCustomWord,
    enableHarmfulCategories,
    setEnableHarmfulCategories,
    harmfulCategoriesSettings,
    setHarmfulCategoriesSettings,
    enableGibberishDetection,
    setEnableGibberishDetection,
    gibberishConfidenceScore,
    setGibberishConfidenceScore,
    enableSentiment,
    setEnableSentiment,
    sentimentConfidenceScore,
    setSentimentConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Add word filters</Text>
            <Text variant="bodyMd" tone="subdued">
                Use the word filters if you want to filter prompts or responses containing specific words.
            </Text>

            <VerticalStack gap="4">
                <Checkbox
                label="Profanity"
                checked={wordFilters.profanity}
                onChange={(checked) => setWordFilters({ ...wordFilters, profanity: checked })}
                helpText="Redacts profanity words that are considered offensive."
            />

            <VerticalStack gap="3">
                <Text variant="headingSm">Custom words</Text>
                <Text variant="bodyMd" tone="subdued">
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

                <Box>
                    <Checkbox
                        label="Enable harmful categories filters"
                        checked={enableHarmfulCategories}
                        onChange={setEnableHarmfulCategories}
                        helpText="Enable to detect and block harmful user inputs and model responses. Use a higher filter strength to increase the likelihood of filtering harmful content in a given category."
                    />
                    {/* Harmful categories detailed settings */}
                    {enableHarmfulCategories && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack align="space-between">
                                    <Text variant="headingSm">Filters for prompts</Text>
                                    <Button variant="plain" onClick={() => {
                                        const resetSettings = { ...harmfulCategoriesSettings };
                                        Object.keys(resetSettings).forEach(key => {
                                            if (key !== 'useForResponses') resetSettings[key] = 'none';
                                        });
                                        setHarmfulCategoriesSettings(resetSettings);
                                    }}>
                                        Reset all
                                    </Button>
                                </HorizontalStack>
                                {Object.entries(harmfulCategoriesSettings).map(([category, level]) => {
                                    if (category === 'useForResponses') return null;
                                    return (
                                        <Box key={category}>
                                            <Text variant="bodyMd" fontWeight="medium" textTransform="capitalize">
                                                {category}
                                            </Text>
                                            <Box paddingBlockStart="2">
                                                <RangeSlider
                                                    label=""
                                                    value={level === 'none' ? 0 : level === 'low' ? 1 : level === 'medium' ? 2 : 3}
                                                    min={0}
                                                    max={3}
                                                    step={1}
                                                    output
                                                    onChange={(value) => {
                                                        const levels = ['none', 'low', 'medium', 'high'];
                                                        setHarmfulCategoriesSettings({
                                                            ...harmfulCategoriesSettings,
                                                            [category]: levels[value]
                                                        });
                                                    }}
                                                />
                                            </Box>
                                        </Box>
                                    );
                                })}
                                <Checkbox
                                    label="Use the same harmful categories filters for responses"
                                    checked={harmfulCategoriesSettings.useForResponses}
                                    onChange={(checked) => setHarmfulCategoriesSettings({
                                        ...harmfulCategoriesSettings,
                                        useForResponses: checked
                                    })}
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                <Box>
                    <Checkbox
                        label="Enable gibberish detection"
                        checked={enableGibberishDetection}
                        onChange={setEnableGibberishDetection}
                        helpText="Detect and block gibberish or nonsensical text in user inputs. This helps prevent meaningless prompts that could confuse the AI or be used as attack vectors."
                    />
                    {enableGibberishDetection && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
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

                <Box>
                    <Checkbox
                        label="Enable sentiment detection"
                        checked={enableSentiment}
                        onChange={setEnableSentiment}
                        helpText="Analyze sentiment in user inputs to detect negative, toxic, or inappropriate emotional content."
                    />
                    {enableSentiment && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
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

export default WordFiltersStep;
