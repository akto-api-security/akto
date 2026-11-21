import SegmentBar from '../../../../components/shared/SegmentBar'

const TRACK = 'var(--p-color-bg-subdued)'

// A bar `max` long on a grey track, filled by one or more parts: [{ label, count, color }].
function MiniBar({ parts, max, title, height = '6px' }) {
    const used = parts.reduce((sum, part) => sum + part.count, 0)
    const segments = [...parts, { label: 'rest', count: Math.max(max - used, 0), color: TRACK, tooltip: title }]
    return <SegmentBar segments={segments.map((s) => ({ ...s, tooltip: s.tooltip || title }))} height={height} />
}

export default MiniBar
