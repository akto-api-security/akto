import { VerticalStack, Text, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

export const TokenLimitConfig = {
    number: 9,
    title: "Token Limit",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableTokenLimit, tokenLimitThreshold }) => {
        if (!enableTokenLimit) return null;
        return `Token limit enforcement, Max tokens: ${tokenLimitThreshold}`;
    }
};

const TokenLimitStep = ({
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitThreshold,
    setTokenLimitThreshold
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Enforce token limits on user inputs to prevent excessive resource usage.
                This helps control costs and prevents potential abuse through extremely long prompts.
            </Text>

            <FormLayout>
                <Checkbox
                    label="Enable token limit"
                    checked={enableTokenLimit}
                    onChange={setEnableTokenLimit}
                />

                {enableTokenLimit && (
                    <Box>
                        <Box paddingBlockStart="2">
                            <RangeSlider
                                label="Token Limit Threshold"
                                value={tokenLimitThreshold}
                                min={100}
                                max={10000}
                                step={100}
                                output
                                onChange={setTokenLimitThreshold}
                                helpText="Set the threshold of tokens allowed. Inputs exceeding this limit will be blocked."
                            />
                        </Box>
                    </Box>
                )}
            </FormLayout>
        </VerticalStack>
    );
};

export default TokenLimitStep;