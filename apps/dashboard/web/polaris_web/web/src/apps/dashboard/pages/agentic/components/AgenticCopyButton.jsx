import { useState } from 'react';
import { Box, Button, HorizontalStack, Icon, Tooltip } from '@shopify/polaris';
import { TickMinor } from '@shopify/polaris-icons';

function AgenticCopyButton({ content }) {
    const [isCopied, setIsCopied] = useState(false);

    const handleCopy = () => {
        navigator.clipboard.writeText(content);
        setIsCopied(true);
        setTimeout(() => setIsCopied(false), 2000);
    };

    return (
        <HorizontalStack align="start" blockAlign="center" gap="0">
            <Box paddingInlineStart="4">
                <Tooltip content={isCopied ? "Copied!" : "Copy"}>
                    <Button
                        plain
                        onClick={handleCopy}
                        size="slim"
                        style={{
                            background: isCopied ? 'rgba(0, 128, 96, 0.1)' : 'transparent'
                        }}
                    >
                        <Box width="16px" height="16px" display="flex" alignItems="center" justifyContent="center">
                            {isCopied ? (
                                <Icon source={TickMinor} />
                            ) : (
                                <img
                                    src="/public/clipboard.svg"
                                    alt="Copy"
                                    style={{ width: '100%', height: '100%', display: 'block' }}
                                />
                            )}
                        </Box>
                    </Button>
                </Tooltip>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticCopyButton;
