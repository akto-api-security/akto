import { useState } from 'react'

// hidden: the section has no data source in the Argus context yet, so the page does not render it
// and this step stays out of the nav. The entry is kept so turning the section back on is one flag.
const STEPS = [
    { id: 'agent', label: 'Agent' },
    { id: 'owner', label: 'Owner', hidden: true },
    { id: 'identity', label: 'Identity', hidden: true },
    { id: 'permissions', label: 'Permissions', hidden: true },
    { id: 'tools', label: 'Tools & Capabilities' },
    { id: 'data', label: 'Data' },
    { id: 'protection', label: 'Protection' },
    { id: 'runtime', label: 'Runtime Activity', hidden: true },
]

const BASE = {
    display: 'flex',
    alignItems: 'center',
    gap: '7px',
    padding: '9px 15px',
    borderRadius: '999px',
    fontSize: '13px',
    fontWeight: 500,
    whiteSpace: 'nowrap',
    textDecoration: 'none',
    transition: 'background .12s ease, color .12s ease',
}

// Plain in-page anchor links — the browser handles the scroll natively (matches the original
// mockup's own <a href="#section"> behaviour), no scrollspy JS or custom CSS needed.
function StepNav() {
    const [current, setCurrent] = useState('agent')
    const [hovered, setHovered] = useState(null)

    return (
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
            {STEPS.filter((step) => !step.hidden).map((step) => {
                const isCurrent = step.id === current
                const isHovered = step.id === hovered
                return (
                    <a
                        key={step.id}
                        href={`#${step.id}`}
                        onClick={() => setCurrent(step.id)}
                        onMouseEnter={() => setHovered(step.id)}
                        onMouseLeave={() => setHovered(null)}
                        style={{
                            ...BASE,
                            background: isCurrent
                                ? 'rgba(44,110,203,0.10)'
                                : (isHovered ? 'rgba(26,27,31,0.04)' : 'transparent'),
                            color: isCurrent || isHovered ? 'var(--p-color-text)' : 'var(--p-color-text-subdued)',
                        }}
                    >
                        {step.label}
                    </a>
                )
            })}
        </div>
    )
}

export default StepNav
