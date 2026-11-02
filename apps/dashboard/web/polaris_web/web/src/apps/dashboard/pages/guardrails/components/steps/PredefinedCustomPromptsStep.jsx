import { VerticalStack, Text, Checkbox } from "@shopify/polaris";
import { PREDEFINED_CUSTOM_PROMPTS } from "../predefinedCustomPrompts";

export const PredefinedCustomPromptsConfig = {
    number: 14,
    title: "Predefined custom prompts",

    validate: () => ({
        isValid: true,
        errorMessage: null
    }),

    getSummary: ({ selectedTemplateIds }) => {
        const selected = PREDEFINED_CUSTOM_PROMPTS.filter((t) => (selectedTemplateIds || []).includes(t.id));
        if (selected.length === 0) return null;
        if (selected.length === 1) return selected[0].label;
        return `${selected.length} templates selected`;
    }
};

const PredefinedCustomPromptsStep = ({
    selectedTemplateIds,
    onToggleTemplate
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Select one or more predefined detection prompts. They are added to the Detection instruction
                under Custom Guardrails, where you can review or edit the full text.
            </Text>
            <VerticalStack gap="2">
                {PREDEFINED_CUSTOM_PROMPTS.map((template) => (
                    <Checkbox
                        key={template.id}
                        label={template.label}
                        checked={(selectedTemplateIds || []).includes(template.id)}
                        onChange={() => onToggleTemplate(template.id)}
                    />
                ))}
            </VerticalStack>
        </VerticalStack>
    );
};

export default PredefinedCustomPromptsStep;
