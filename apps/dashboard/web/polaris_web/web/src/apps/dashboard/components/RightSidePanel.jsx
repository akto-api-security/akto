import { Box, Text, Icon } from '@shopify/polaris';
import { CancelSmallMinor } from '@shopify/polaris-icons';

/**
 * Reusable right-side panel component
 * @param {boolean} isOpen - Controls panel visibility
 * @param {function} onClose - Callback when panel closes
 * @param {string} title - Panel title
 * @param {ReactNode} children - Panel content
 * @param {string} width - Panel width (default: '600px')
 */
function RightSidePanel({ isOpen, onClose, title, children, width = '600px' }) {
    if (!isOpen) return null;

    return (
        <>
            <style>{`
                @keyframes slideInRight {
                    from {
                        transform: translateX(100%);
                    }
                    to {
                        transform: translateX(0);
                    }
                }
            `}</style>

            {/* Backdrop */}
            <Box
                onClick={onClose}
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: 'rgba(0, 0, 0, 0.5)',
                    zIndex: 999,
                    transition: 'opacity 0.3s ease'
                }}
            />

            {/* Side Panel */}
            <Box
                style={{
                    position: 'fixed',
                    top: 0,
                    right: 0,
                    bottom: 0,
                    width: width,
                    background: '#FFF',
                    boxShadow: '-2px 0 8px rgba(0, 0, 0, 0.1)',
                    zIndex: 1000,
                    display: 'flex',
                    flexDirection: 'column',
                    animation: 'slideInRight 0.3s ease-out'
                }}
            >
                {/* Header */}
                <Box
                    style={{
                        padding: '20px 24px',
                        borderBottom: '1px solid #E1E3E5',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}
                >
                    <Text variant="headingMd" as="h2">
                        {title}
                    </Text>
                    <Box
                        onClick={onClose}
                        style={{
                            cursor: 'pointer',
                            padding: '4px',
                            borderRadius: '4px',
                            display: 'flex',
                            alignItems: 'center',
                            transition: 'background 0.2s ease'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = '#F6F6F7'}
                        onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                        <Icon source={CancelSmallMinor} />
                    </Box>
                </Box>

                {/* Content */}
                <Box
                    style={{
                        flex: 1,
                        overflowY: 'auto',
                        padding: '16px 24px'
                    }}
                >
                    {children}
                </Box>
            </Box>
        </>
    );
}

export default RightSidePanel;
