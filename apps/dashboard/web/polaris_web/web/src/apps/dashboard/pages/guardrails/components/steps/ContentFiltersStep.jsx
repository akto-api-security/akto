import { VerticalStack, Text, Checkbox, Box, RangeSlider } from "@shopify/polaris";

export const ContentFiltersConfig = {
    number: 2,
    title: "Configure content filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enablePromptAttacks }) => {
        const filters = [];
        if (enablePromptAttacks) filters.push('Prompt attacks');
        return filters.length > 0 ? filters.join(', ') : null;
    }
};

const ContentFiltersStep = ({
    enablePromptAttacks,
    setEnablePromptAttacks,
    promptAttackLevel,
    setPromptAttackLevel
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Configure content filters</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure content filters by adjusting the degree of filtering to detect and block harmful user inputs and model responses that violate your usage policies.
            </Text>

            <VerticalStack gap="4">
                <Box>
                    <Checkbox
                        label="Enable prompt attacks filter"
                        checked={enablePromptAttacks}
                        onChange={setEnablePromptAttacks}
                        helpText="Enable to detect and block user inputs attempting to override system instructions. To avoid misclassifying system prompts as a prompt attack and ensure that the filters are selectively applied to user inputs, use input tagging."
                    />
                    {/* Prompt attacks level slider */}
                    {enablePromptAttacks && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Prompt Attack Level</Text>
                            <RangeSlider
                                label=""
                                value={promptAttackLevel === 'none' ? 0 : promptAttackLevel === 'low' ? 1 : promptAttackLevel === 'medium' ? 2 : 3}
                                min={0}
                                max={3}
                                step={1}
                                output
                                onChange={(value) => {
                                    const levels = ['none', 'low', 'medium', 'high'];
                                    setPromptAttackLevel(levels[value]);
                                }}
                            />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default ContentFiltersStep;
