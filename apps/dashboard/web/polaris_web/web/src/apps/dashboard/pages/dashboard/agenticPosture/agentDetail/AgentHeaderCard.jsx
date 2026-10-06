import { Box, Card, Text, Tooltip } from '@shopify/polaris'
import func from '@/util/func'

const SEVERITY_COLOR = {
    CRITICAL: '#d82c0d',
    HIGH: '#bf5711',
    MEDIUM: '#b98900',
    LOW: '#008060',
}

function colorFor(severity) {
    return SEVERITY_COLOR[String(severity || '').toUpperCase()] || '#6d7175'
}

function rgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
}

const DESCRIPTION_LIMIT = 255

function AgentHeaderCard({ header }) {
    if (!header) return null

    const tone = colorFor(header.severity)
    const severityLabel = header.severity
        ? header.severity.charAt(0) + header.severity.slice(1).toLowerCase()
        : null

    const metadata = [
        header.createdAt ? `Created · ${func.prettifyEpoch(header.createdAt)}` : null,
        header.lastActive ? `Last active · ${func.prettifyEpoch(header.lastActive)}` : null,
    ].filter(Boolean)

    return (
        <Card>
            <Box padding="6">
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '20px', flexWrap: 'wrap' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <span style={{
                            width: '52px', height: '52px', borderRadius: '12px', flex: 'none',
                            background: rgba(tone, 0.12), border: `1px solid ${rgba(tone, 0.3)}`,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke={tone} strokeWidth="1.5" strokeLinejoin="round">
                                <path d="M12 3 20 7.5 20 16.5 12 21 4 16.5 4 7.5Z" />
                                <circle cx="12" cy="12" r="2.6" />
                            </svg>
                        </span>
                        <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                                <h1 style={{
                                    margin: 0, fontFamily: 'var(--p-font-family-mono)', fontSize: '22px',
                                    fontWeight: 600, color: 'var(--p-color-text)',
                                }}>{header.name}</h1>
                                {severityLabel && (
                                    <span style={{
                                        display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '3px 10px',
                                        borderRadius: '999px', background: rgba(tone, 0.14), border: `1px solid ${rgba(tone, 0.35)}`,
                                        fontSize: '11px', fontWeight: 600, color: 'var(--p-color-text)',
                                    }}>
                                        <span style={{ width: '6px', height: '6px', borderRadius: '999px', background: tone }} />
                                        {severityLabel}
                                    </span>
                                )}
                                {header.environment && (
                                    <span style={{
                                        fontSize: '11.5px', color: 'var(--p-color-text-subdued)', padding: '3px 10px',
                                        border: '1px solid var(--p-color-border)', borderRadius: '999px',
                                    }}>{header.environment.toLowerCase()}</span>
                                )}
                            </div>
                            {header.description && (() => {
                                const truncated = header.description.length > DESCRIPTION_LIMIT
                                const shown = truncated
                                    ? `${header.description.slice(0, DESCRIPTION_LIMIT).trimEnd()}...`
                                    : header.description
                                const paragraph = (
                                    <p style={{
                                        margin: '8px 0 0', fontSize: '13.5px', color: 'var(--p-color-text-subdued)',
                                        maxWidth: '540px', lineHeight: 1.5,
                                    }}>{shown}</p>
                                )
                                return truncated
                                    ? <Tooltip content={header.description} preferredPosition="below">{paragraph}</Tooltip>
                                    : paragraph
                            })()}
                        </div>
                    </div>
                    <div style={{ textAlign: 'right', flex: 'none' }}>
                        <div style={{ fontSize: '26px', fontWeight: 700, color: tone }}>
                            {header.riskScore}
                            <span style={{ fontSize: '14px', color: 'var(--p-color-text-subdued)', fontWeight: 700 }}>/100</span>
                        </div>
                        <div style={{ fontSize: '11.5px', color: 'var(--p-color-text-subdued)' }}>risk score</div>
                    </div>
                </div>
                {metadata.length > 0 && (
                    <div style={{
                        display: 'flex', gap: '22px', flexWrap: 'wrap', marginTop: '18px', paddingTop: '16px',
                        borderTop: '1px solid var(--p-color-border)', fontSize: '12.5px', color: 'var(--p-color-text-subdued)',
                    }}>
                        {metadata.map((item) => <span key={item}>{item}</span>)}
                    </div>
                )}
            </Box>
        </Card>
    )
}

export default AgentHeaderCard
