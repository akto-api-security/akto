import { Box, Card, Text, VerticalStack } from '@shopify/polaris'

const TONE = {
    RESOURCE_DELETE: '#d82c0d',
    CREDENTIAL_OR_PII_READ: '#d82c0d',
    CRITICAL_RESOURCE_WRITE: '#bf5711',
    FILE_WRITE: '#b98900',
}

const NEUTRAL = '#2c6ecb'

function rgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
}

function ToolRow({ tool }) {
    const tone = tool.privileged ? (TONE[tool.capability] || '#bf5711') : NEUTRAL

    return (
        <Card padding="4">
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <span style={{
                    width: '34px', height: '34px', borderRadius: '9px', flex: 'none',
                    background: rgba(tone, 0.14),
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke={tone}
                        strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round">
                        <path d="M14.5 6.5a4 4 0 0 0-5.4 4.9L4 16.5V20h3.5l5.1-5.1a4 4 0 0 0 4.9-5.4l-2.8 2.8-2-2Z" />
                    </svg>
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                        fontFamily: 'var(--p-font-family-mono)', fontSize: '13.5px', fontWeight: 600,
                        color: 'var(--p-color-text)', overflowWrap: 'anywhere',
                    }}>{tool.name}</div>
                    {tool.detail && (
                        <div style={{ fontSize: '12px', color: 'var(--p-color-text-subdued)', marginTop: '2px' }}>
                            {tool.detail}
                        </div>
                    )}
                </div>
                {tool.privileged && (
                    <span style={{
                        display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '3px 10px',
                        borderRadius: '999px', flex: 'none',
                        background: rgba(tone, 0.14), border: `1px solid ${rgba(tone, 0.35)}`,
                        fontSize: '11px', fontWeight: 600, color: 'var(--p-color-text)',
                    }}>
                        <span style={{ width: '6px', height: '6px', borderRadius: '999px', background: tone }} />
                        {tool.capabilityLabel}
                    </span>
                )}
            </div>
        </Card>
    )
}

function ToolsSection({ tools }) {
    const rows = tools || []
    if (rows.length === 0) {
        return (
            <Card>
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">No tools discovered for this agent.</Text>
                </Box>
            </Card>
        )
    }
    return (
        <VerticalStack gap="3">
            {rows.map((tool) => <ToolRow key={`${tool.method} ${tool.url}`} tool={tool} />)}
        </VerticalStack>
    )
}

export default ToolsSection
