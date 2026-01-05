import { useState } from 'react';
import { Box, Icon, Tooltip } from '@shopify/polaris';
import { TickMinor } from '@shopify/polaris-icons';

function AgenticCopyButton({ content }) {
    const [isCopied, setIsCopied] = useState(false);

    const handleCopy = () => {
        let textToCopy = `${content.title}\n\n`;
        content.sections.forEach(section => {
            textToCopy += `${section.header}\n`;
            section.items.forEach(item => {
                textToCopy += `• ${item}\n`;
            });
            textToCopy += '\n';
        });
        navigator.clipboard.writeText(textToCopy);
        setIsCopied(true);
        setTimeout(() => setIsCopied(false), 2000);
    };

    return (
        <Box
            style={{
                width: '100%',
                display: 'flex',
                justifyContent: 'flex-start',
                paddingLeft: '16px'
            }}
        >
            <Tooltip content={isCopied ? "Copied!" : "Copy"}>
                <Box
                    onClick={handleCopy}
                    style={{
                        padding: '8px',
                        borderRadius: '8px',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        transition: 'background 0.2s ease',
                        background: isCopied ? 'rgba(0, 128, 96, 0.1)' : 'transparent'
                    }}
                    onMouseEnter={(e) => {
                        if (!isCopied) {
                            e.currentTarget.style.background = 'rgba(0, 0, 0, 0.05)';
                        }
                    }}
                    onMouseLeave={(e) => {
                        if (!isCopied) {
                            e.currentTarget.style.background = 'transparent';
                        }
                    }}
                >
                    <Box style={{ width: '16px', height: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
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
                </Box>
            </Tooltip>
        </Box>
    );
}

export default AgenticCopyButton;
