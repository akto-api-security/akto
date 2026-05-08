import { VerticalStack, Text, Checkbox, Box, HorizontalStack, Button, RangeSlider } from "@shopify/polaris";

export const ContentFiltersConfig = {
    number: 2,
    title: "Configure content filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableHarmfulCategories, enablePromptAttacks }) => {
        if (enableHarmfulCategories || enablePromptAttacks) {
            return `${enableHarmfulCategories ? 'Harmful categories' : ''}${enableHarmfulCategories && enablePromptAttacks ? ', ' : ''}${enablePromptAttacks ? 'Prompt attacks' : ''}`;
        }
        return null;
    }
};

const ContentFiltersStep = ({
    enableHarmfulCategories,
    setEnableHarmfulCategories,
    harmfulCategoriesSettings,
    setHarmfulCategoriesSettings,
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

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <VerticalStack gap="4">
                    <Text variant="headingSm">Harmful categories</Text>
                    <Text variant="bodyMd" tone="subdued">
                        Enable to detect and block harmful user inputs and model responses. Use a higher filter strength to increase the likelihood of filtering harmful content in a given category.
                    </Text>
                    <Checkbox
                        label="Enable harmful categories filters"
                        checked={enableHarmfulCategories}
                        onChange={setEnableHarmfulCategories}
                    />
                    {enableHarmfulCategories && (
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
                    )}
                </VerticalStack>
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <VerticalStack gap="4">
                    <Text variant="headingSm">Prompt attacks</Text>
                    <Text variant="bodyMd" tone="subdued">
                        Enable to detect and block user inputs attempting to override system instructions. To avoid misclassifying system prompts as a prompt attack and ensure that the filters are selectively applied to user inputs, use input tagging.
                    </Text>
                    <Checkbox
                        label="Enable prompt attacks filter"
                        checked={enablePromptAttacks}
                        onChange={setEnablePromptAttacks}
                    />
                    {enablePromptAttacks && (
                        <Box>
                            <Text variant="bodyMd" fontWeight="medium">Prompt Attack</Text>
                            <Box paddingBlockStart="2">
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
                            </Box>
                        </Box>
                    )}
                </VerticalStack>
            </Box>
        </VerticalStack>
    );
};

export default ContentFiltersStep;
