import { useState } from "react";
import { VerticalStack, Text, FormLayout, TextField, RangeSlider } from "@shopify/polaris";

// URL validation function (same pattern as McpRegistry.jsx)
const validateUrl = (url) => {
    const urlPattern = /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/;
    return urlPattern.test(url);
};

export const ExternalModelConfig = {
    number: 7,
    title: "External model based evaluation",

    validate: ({ url }) => {
        const hasUrl = url && url.trim().length > 0;
        const urlError = hasUrl && !validateUrl(url.trim()) ? "Invalid URL format. Must be a valid http or https URL" : null;

        return {
            isValid: !urlError,
            errorMessage: urlError
        };
    },

    getSummary: ({ url, confidenceScore }) => {
        return url ? `URL: ${url.substring(0, 30)}${url.length > 30 ? '...' : ''}, Confidence: ${confidenceScore}` : null;
    }
};

const ExternalModelStep = ({
    url,
    setUrl,
    confidenceScore,
    setConfidenceScore
}) => {
    const [urlError, setUrlError] = useState("");

    // Handle URL input with validation
    const handleUrlChange = (value) => {
        setUrl(value);
        if (value && value.trim() && !validateUrl(value.trim())) {
            setUrlError("Invalid URL format. Must be a valid http or https URL");
        } else {
            setUrlError("");
        }
    };

    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">External model based evaluation</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure an external model endpoint to evaluate content against custom criteria.
            </Text>

            <FormLayout>
                <TextField
                    label="URL"
                    value={url}
                    onChange={handleUrlChange}
                    placeholder="https://api.example.com/evaluate"
                    helpText="The endpoint URL for your external evaluation model"
                    error={urlError}
                />

                <RangeSlider
                    label="Confidence score threshold"
                    value={confidenceScore}
                    onChange={setConfidenceScore}
                    min={0}
                    max={100}
                    step={25}
                    output
                    helpText="Content will be blocked if the model's confidence score exceeds this threshold (0-100)"
                />
            </FormLayout>
        </VerticalStack>
    );
};

export default ExternalModelStep;
