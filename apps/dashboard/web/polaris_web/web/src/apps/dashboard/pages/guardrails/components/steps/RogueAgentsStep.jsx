import { VerticalStack, Text, TextField, Box, Checkbox, HorizontalStack } from "@shopify/polaris";
import ControlInfoIcon from "../ControlInfoIcon";
import { PREDEFINED_CUSTOM_PROMPTS } from "../predefinedCustomPrompts";

export const RogueAgentsConfig = {
    number: 14,
    title: "Rogue agents",

    validate: () => ({ isValid: true, errorMessage: null }),

    getSummary: ({ selectedTemplateIds }) => {
        const selected = PREDEFINED_CUSTOM_PROMPTS.filter((t) => (selectedTemplateIds || []).includes(t.id));
        if (selected.length === 1) return selected[0].label;
        return selected.length > 1 ? `${selected.length} presets selected` : null;
    }
};

const RogueAgentsStep = ({
    selectedTemplateIds = [],
    onToggleTemplate,
    presetPrompts = {},
    onChangePresetPrompt
}) => (
    <VerticalStack gap="4">
        <Text variant="bodyMd" tone="subdued">
            Stop agents from acting outside their remit. Selected rules are evaluated as LLM prompt based detection.
        </Text>

        <VerticalStack gap="4">
            {PREDEFINED_CUSTOM_PROMPTS.map((template) => (
                <Box key={template.id}>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">{template.label}</Text>
                                <ControlInfoIcon description={template.info} examples={[]} />
                            </HorizontalStack>
                        }
                        checked={selectedTemplateIds.includes(template.id)}
                        onChange={() => onToggleTemplate?.(template.id)}
                        helpText={template.description}
                    />
                    {selectedTemplateIds.includes(template.id) && (
                        <Box paddingBlockStart="8" paddingBlockEnd="3" style={{ paddingLeft: '28px' }}>
                            <TextField
                                label={`${template.label} instruction`}
                                labelHidden
                                value={presetPrompts[template.id] || ""}
                                onChange={(value) => onChangePresetPrompt?.(template.id, value)}
                                multiline={6}
                                maxHeight={140}
                                helpText="Edit this instruction to fit your environment."
                            />
                        </Box>
                    )}
                </Box>
            ))}
        </VerticalStack>
    </VerticalStack>
);

export default RogueAgentsStep;
