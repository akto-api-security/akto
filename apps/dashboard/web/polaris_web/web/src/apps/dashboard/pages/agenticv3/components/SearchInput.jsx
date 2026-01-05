import { Box, TextField, Button, HorizontalStack } from '@shopify/polaris';
import { SearchMinor } from '@shopify/polaris-icons';

/**
 * SearchInput - Input field for user queries
 * Uses only Polaris components, no HTML tags
 * Supports both fixed (bottom) and inline positioning
 */
function SearchInput({
    input,
    handleInputChange,
    handleSubmit,
    isLoading,
    placeholder = 'Ask anything...',
    isFixed = false
}) {
    const handleFormSubmit = (e) => {
        e.preventDefault();
        if (!isLoading && input.trim()) {
            handleSubmit(e);
        }
    };

    const inputField = (
        <Box
            background="bg-surface"
            padding="400"
            borderRadius="300"
            borderWidth="025"
            borderColor="border"
            shadow="100"
        >
            <form onSubmit={handleFormSubmit}>
                <HorizontalStack gap="300" align="space-between">
                    <Box width="100%">
                        <TextField
                            value={input}
                            onChange={handleInputChange}
                            placeholder={placeholder}
                            autoComplete="off"
                            disabled={isLoading}
                            connectedRight={
                                <Button
                                    submit
                                    icon={SearchMinor}
                                    loading={isLoading}
                                    disabled={!input.trim() || isLoading}
                                >
                                    Send
                                </Button>
                            }
                        />
                    </Box>
                </HorizontalStack>
            </form>
        </Box>
    );

    // If fixed, wrap in a fixed position container using Polaris Box
    if (isFixed) {
        return (
            <Box
                position="fixed"
                insetBlockEnd="400"
                insetInlineStart="50%"
                width="520px"
                style={{
                    transform: 'translateX(-50%)',
                    zIndex: 100
                }}
            >
                {inputField}
            </Box>
        );
    }

    // Otherwise, return inline
    return inputField;
}

export default SearchInput;
