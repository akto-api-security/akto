import { Card } from '@shopify/polaris'

const MAX_TAGS = 5

const GOOD = '#008060'
const CRITICAL = '#d82c0d'
const MUTED = '#6d7175'

const CELL = { textAlign: 'left', padding: '10px 12px', fontSize: '13px', verticalAlign: 'middle' }
const HEAD = {
    ...CELL,
    fontSize: '11px',
    fontWeight: 600,
    color: MUTED,
    textTransform: 'uppercase',
    letterSpacing: '.4px',
    borderBottom: '1px solid var(--p-color-border-subdued)',
}

const CHIP = {
    padding: '3px 10px',
    borderRadius: '999px',
    background: 'var(--p-color-bg-subdued)',
    border: '1px solid var(--p-color-border-subdued)',
    fontSize: '12px',
    color: 'var(--p-color-text-subdued)',
    whiteSpace: 'nowrap',
}

// Dot + coloured label, as the design renders Present / Missing — a rule nobody enabled is a gap,
// so it reads critical rather than muted.
function Status({ enabled }) {
    const tone = enabled ? GOOD : CRITICAL
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: 600, color: tone }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '999px', background: tone }} />
            {enabled ? 'Enabled' : 'Not enabled'}
        </span>
    )
}

function Details({ rule }) {
    const details = rule.details || []
    if (details.length === 0) return <span style={{ color: MUTED }}>—</span>

    const shown = details.slice(0, MAX_TAGS)
    const rest = (rule.detailsTotal || details.length) - shown.length

    return (
        <span style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {shown.map((value) => <span key={value} style={CHIP}>{value}</span>)}
            {rest > 0 && <span style={CHIP}>{`+${rest}`}</span>}
        </span>
    )
}

function ProtectionSection({ protection }) {
    const rows = protection || []
    if (rows.length === 0) return null

    return (
        <Card padding="2">
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                    <tr>
                        <th style={{ ...HEAD, width: '22%' }}>Rule</th>
                        <th style={{ ...HEAD, width: '14%' }}>Status</th>
                        <th style={HEAD}>Details</th>
                        <th style={{ ...HEAD, width: '18%' }}>Applies on</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map((rule, index) => (
                        <tr key={rule.name} style={index === 0 ? undefined : { borderTop: '1px solid var(--p-color-border-subdued)' }}>
                            <td style={{ ...CELL, fontWeight: 500, color: 'var(--p-color-text)' }}>{rule.name}</td>
                            <td style={CELL}><Status enabled={rule.enabled} /></td>
                            <td style={CELL}><Details rule={rule} /></td>
                            <td style={{ ...CELL, color: rule.appliesOn ? 'var(--p-color-text-subdued)' : MUTED }}>{rule.appliesOn || '—'}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </Card>
    )
}

export default ProtectionSection
