import { useState } from 'react';
import { Button } from '@shopify/polaris';
import { ClipboardMinor } from '@shopify/polaris-icons';

/**
 * CopyButton - Copies content to clipboard
 * Uses only Polaris components, no HTML tags
 */
function CopyButton({ content }) {
    const [copied, setCopied] = useState(false);

    const handleCopy = async () => {
        try {
            // Convert content to string for copying
            const textToCopy = typeof content === 'string'
                ? content
                : JSON.stringify(content, null, 2);

            await navigator.clipboard.writeText(textToCopy);
            setCopied(true);

            // Reset after 2 seconds
            setTimeout(() => {
                setCopied(false);
            }, 2000);
        } catch (error) {
            console.error('Failed to copy:', error);
        }
    };

    return (
        <Button
            size="slim"
            icon={ClipboardMinor}
            onClick={handleCopy}
            tone={copied ? 'success' : undefined}
        >
            {copied ? 'Copied!' : 'Copy'}
        </Button>
    );
}

export default CopyButton;
