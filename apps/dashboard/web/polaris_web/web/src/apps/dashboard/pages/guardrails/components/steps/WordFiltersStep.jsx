import { VerticalStack, Text, Checkbox, HorizontalStack, Button, TextField, Box, DataTable } from "@shopify/polaris";
import { DeleteMajor } from '@shopify/polaris-icons';

export const WordFiltersConfig = {
    number: 4,
    title: "Add word filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ wordFilters }) => {
        const filters = [];
        if (wordFilters.profanity) filters.push("Profanity");
        if (wordFilters.custom?.length > 0) filters.push(`${wordFilters.custom.length} Custom word${wordFilters.custom.length !== 1 ? 's' : ''}`);
        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const WordFiltersStep = ({
    wordFilters,
    setWordFilters,
    newCustomWord,
    setNewCustomWord
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Add word filters</Text>
            <Text variant="bodyMd" tone="subdued">
                Use the word filters if you want to filter prompts or responses containing specific words.
            </Text>

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
        </VerticalStack>
    );
};

export default WordFiltersStep;
