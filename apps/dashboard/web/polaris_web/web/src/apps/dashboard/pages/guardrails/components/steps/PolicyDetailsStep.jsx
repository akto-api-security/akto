import { VerticalStack, Text, FormLayout, TextField, Checkbox } from "@shopify/polaris";

// Step metadata
export const PolicyDetailsConfig = {
    number: 1,
    title: "Provide guardrail policy details",

    // Validation
    validate: ({ name, blockedMessage }) => {
        const isValid = name.trim() && blockedMessage.trim();
        return {
            isValid,
            errorMessage: !isValid ? "Required fields missing" : null
        };
    },

    // Summary
    getSummary: ({ name, description }) => {
        return name ? `${name}${description ? ` - ${description.substring(0, 30)}${description.length > 30 ? '...' : ''}` : ''}` : null;
    }
};

// Step content component
const PolicyDetailsStep = ({
    name,
    setName,
    description,
    setDescription,
    blockedMessage,
    setBlockedMessage,
    applyToResponses,
    setApplyToResponses
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Guardrail policy details</Text>
            <FormLayout>
                <TextField
                    label="Name"
                    value={name}
                    onChange={setName}
                    placeholder="chatbot-guardrail"
                    helpText="Valid characters are a-z, A-Z, 0-9, _ (underscore) and - (hyphen). The name can have up to 50 characters."
                    requiredIndicator
                />
                <TextField
                    label="Description"
                    value={description}
                    onChange={setDescription}
                    multiline={3}
                    placeholder="This guardrail blocks toxic content, assistance related to - investment, insurance, medical and programming."
                    helpText="The description can have up to 200 characters."
                />
                <TextField
                    label="Messaging for blocked prompts"
                    value={blockedMessage}
                    onChange={setBlockedMessage}
                    multiline={3}
                    placeholder="Sorry, the model cannot answer this question. This has been blocked by chatbot-guardrail."
                    helpText="Enter a message to display if your guardrail blocks the user prompt."
                    requiredIndicator
                />
                <Checkbox
                    label="Apply the same blocked message for responses"
                    checked={applyToResponses}
                    onChange={setApplyToResponses}
                />
            </FormLayout>
        </VerticalStack>
    );
};

export default PolicyDetailsStep;
