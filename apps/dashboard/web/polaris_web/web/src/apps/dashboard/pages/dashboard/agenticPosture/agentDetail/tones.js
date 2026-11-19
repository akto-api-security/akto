const TONES = {
    critical: { badge: 'critical', background: 'bg-critical-subdued', icon: 'critical' },
    high: { badge: 'warning', background: 'bg-warning-subdued', icon: 'warning' },
    medium: { badge: 'attention', background: 'bg-caution-subdued', icon: 'warning' },
    low: { badge: 'success', background: 'bg-success-subdued', icon: 'success' },
    neutral: { badge: 'info', background: 'bg-interactive-subdued', icon: 'interactive' },
    muted: { badge: undefined, background: 'bg-subdued', icon: 'subdued' },
}

const CAPABILITY_TONE = {
    RESOURCE_DELETE: 'critical',
    CREDENTIAL_OR_PII_READ: 'critical',
    CRITICAL_RESOURCE_WRITE: 'high',
    FILE_WRITE: 'medium',
}

export function severityTone(severity) {
    return TONES[String(severity || '').toLowerCase()] || TONES.muted
}

export function toolTone(tool) {
    if (!tool.privileged) return TONES.neutral
    return TONES[CAPABILITY_TONE[tool.capability] || 'high']
}
