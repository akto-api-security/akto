// Blurred-placeholder data for SecurityPosture.jsx — see DummyDataOverlay there for how these
// are used. When a panel genuinely has no data (not "zero, confirmed" — no data at all), showing
// a bare "no data" box reads as broken. Instead: render the SAME chart component with static,
// illustrative numbers (never real account data), blurred, and let a click open a Popover
// explaining why. PANEL_EMPTY_STATE_COPY is a stub map, not final text — copy the product side
// still owns.
export const PANEL_EMPTY_STATE_COPY = {
    shadowAiTrend: 'All data is approved',
    dataLeaving: 'Create policies having PII-types for detecting Data leaks',
    enforcementFunnel: 'Create guardrail policies in Block, Alert mode.',
    attackAttempts: 'Coming Soon',
    frameworkReadiness: 'Run a compliance scan from the Threat Detection page to score framework readiness',
    vendorRiskExposure: 'All data is safe',
    adoptionGap: 'Coming soon',
    riskScoreTrend: 'Coming soon',
}

// Static week-ending-now timestamps for a dummy N-week series — same [ms, value] point shape
// the real backend series use, so the same chart component renders either one identically.
export function dummyWeeklySeries(values) {
    const nowMs = Date.now()
    const weekMs = 7 * 24 * 3600 * 1000
    return values.map((v, i) => [nowMs - (values.length - 1 - i) * weekMs, v])
}

// Illustrative-only numbers, shaped exactly like each panel's real response — never real
// account data. Same figures the original design mockup used for these cards.
export const DUMMY_SHADOW_AI_TREND = {
    series: [
        { name: 'Sanctioned', data: dummyWeeklySeries([48, 52, 55, 58, 61, 64, 66, 69, 71, 74, 77, 79]) },
        { name: 'Unsanctioned', data: dummyWeeklySeries([88, 94, 99, 104, 109, 113, 117, 121, 125, 129, 132, 135]) },
    ],
    currentSanctioned: 79,
    currentUnsanctioned: 135,
    route: null,
}
export const DUMMY_DATA_LEAVING = {
    total: 100,
    segments: [
        { label: 'Source code', count: 34, percent: 34 },
        { label: 'Customer PII', count: 26, percent: 26 },
        { label: 'Financials', count: 18, percent: 18 },
        { label: 'Credentials', count: 12, percent: 12 },
        { label: 'Other', count: 10, percent: 10 },
    ],
    route: null,
}
export const DUMMY_ENFORCEMENT_FUNNEL = {
    stages: [
        { id: 'matched', label: 'Matched a policy', count: 6914, percentOfMatched: 4.6 },
        { id: 'hardBlocked', label: 'Hard-blocked', count: 3180, percentOfMatched: 46 },
        { id: 'warnedOnly', label: 'Warned only', count: 2697, percentOfMatched: 39 },
        { id: 'warningOverridden', label: 'Warning overridden', count: 620, percentOfMatched: 9 },
    ],
    inspectedActions: 148900,
    route: null,
}
export const DUMMY_ATTACK_ATTEMPTS = {
    series: [
        { name: 'Blocked', data: dummyWeeklySeries([58, 52, 61, 64, 69, 71, 75, 81]) },
        { name: 'Got through', data: dummyWeeklySeries([4, 3, 5, 4, 5, 5, 6, 6]) },
    ],
    currentTotal: 87,
    currentBlocked: 81,
    currentGotThrough: 6,
    route: null,
}

// Fallback only — shown blurred until a compliance clause scan has run (see
// ComplianceClauseScanAction / PostureService#frameworkReadiness), same conditional-real pattern
// as ShadowAiTrendCard etc. above, not "no backend at all" like DUMMY_ADOPTION_GAP below.
export const DUMMY_FRAMEWORK_READINESS = [
    { id: 'nist', label: 'NIST AI RMF', value: 78 },
    { id: 'iso', label: 'ISO/IEC 42001', value: 64 },
    { id: 'euai', label: 'EU AI Act (GPAI)', value: 51 },
    { id: 'soc2', label: 'SOC 2 · AI addendum', value: 92 },
]

// This one has no backend yet at all (not "empty data" — the feature itself isn't built), so
// unlike the conditionally-real ones it's ALWAYS shown blurred.
export const DUMMY_ADOPTION_GAP = [
    { department: 'Engineering', shadowPct: 30, approvedPct: 60, shadowSharePct: 41 },
    { department: 'Sales', shadowPct: 24, approvedPct: 68, shadowSharePct: 28 },
    { department: 'Marketing', shadowPct: 16, approvedPct: 78, shadowSharePct: 19 },
    { department: 'Finance', shadowPct: 6, approvedPct: 92, shadowSharePct: 6 },
]
export const DUMMY_VENDOR_RISK_BUBBLE = [
    { id: 1, x: 12, y: 18, actFirst: true },
    { id: 2, x: 18, y: 12, actFirst: true },
    { id: 3, x: 28, y: 22, actFirst: true },
    { id: 4, x: 24, y: 42, actFirst: false },
    { id: 5, x: 45, y: 30, actFirst: false },
    { id: 6, x: 62, y: 55, actFirst: false },
    { id: 7, x: 78, y: 68, actFirst: false },
]

// The risk score flyout's composite trend — still illustrative-only (unlike "what moved the
// score", which is now real; see RiskScoreAnnotationsSection). Same "no posture_score_history
// yet" gap the composite KPI already reports (see RiskScoreCalculator's GAP_POSTURE_HISTORY).
export const DUMMY_RISK_SCORE_TREND = [74, 71, 73, 70, 68, 65, 66, 68, 70, 71, 69, 68]
