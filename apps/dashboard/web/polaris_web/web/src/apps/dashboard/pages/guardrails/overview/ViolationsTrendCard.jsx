import { useMemo, useRef } from 'react'
import CardWithHeader from '../../dashboard/new_components/CardWithHeader'
import StackedChart from '../../../components/charts/StackedChart'

// Red / yellow / cyan are the posture donuts' colors, so an action reads the same tone across pages.
const SERIES = [
    { key: 'blocked', name: 'Blocked', color: '#F24122' },
    { key: 'alerted', name: 'Alerted', color: '#F2B322' },
    { key: 'flagged', name: 'Flagged', color: '#3BC3D3' },
]

const DAY_SECONDS = 86400
const DAY_MS = DAY_SECONDS * 1000
// Past this many bars the columns get too thin to read, so days are summed into wider bars.
const MAX_BARS = 60

// The days are UTC buckets, so the axis is read in UTC too; a local axis would label them a day early west of UTC.
const CHART_OPTIONS = { time: { useUTC: true } }
const X_AXIS = {
    type: 'datetime',
    visible: true,
    title: { text: undefined },
    gridLineWidth: 0,
    labels: { style: { fontSize: '11px' } },
    dateTimeLabelFormats: { day: '%b %e', week: '%b %e', month: '%b' },
}

// The backend returns every day of the range, so a quiet range is a list of all-zero days.
const hasViolations = (points) => points.some((p) => SERIES.some((s) => p[s.key] > 0))

// One bar per day, or per `step` days when the range is too long for a bar a day.
function toBars(points) {
    const step = Math.max(1, Math.ceil(points.length / MAX_BARS))
    const bars = []
    for (let i = 0; i < points.length; i += step) {
        const chunk = points.slice(i, i + step)
        const bar = { ts: chunk[0].ts, days: chunk.length }
        SERIES.forEach((s) => { bar[s.key] = chunk.reduce((sum, p) => sum + p[s.key], 0) })
        bars.push(bar)
    }
    return bars
}

const dayText = (ms) => new Date(ms).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
const barText = (bar) => (bar.days === 1 ? dayText(bar.ts) : `${dayText(bar.ts)} – ${dayText(bar.ts + (bar.days - 1) * DAY_MS)}`)

// points: one per day, ts in ms. onOpenRange(start, end) opens the violations of a bar's days, in seconds.
function ViolationsTrendCard({ points, onOpenRange }) {
    // The parent passes a new function on every render. Reading it through a ref keeps the chart data below
    // the same until the points change, so the chart is not rebuilt when another card loads more rows.
    const openRange = useRef(onOpenRange)
    openRange.current = onOpenRange

    const { data, tooltip, openBar } = useMemo(() => {
        const bars = toBars(points)
        const byTs = new Map(bars.map((b) => [b.ts, b]))
        return {
            data: SERIES.map((s) => ({ name: s.name, color: s.color, data: bars.map((b) => [b.ts, b[s.key]]) })),
            // The hover card of the design: the bar's days, each action's count and the total.
            tooltip: function () {
                const bar = byTs.get(this.x)
                const lines = this.points.map((p) => `<span style="color:${p.series.color}">●</span> ${p.series.name}: <b>${p.y}</b>`)
                const total = this.points.reduce((sum, p) => sum + p.y, 0)
                return [`<b>${bar ? barText(bar) : ''}</b>`, ...lines, `Total: <b>${total}</b>`].join('<br/>')
            },
            openBar: (e) => {
                const bar = byTs.get(e.point.x)
                if (bar) openRange.current(bar.ts / 1000, bar.ts / 1000 + bar.days * DAY_SECONDS - 1)
            },
        }
    }, [points])
    return (
        <CardWithHeader title="Violations over time" hasData={hasViolations(points)} emptyMessage="No violations in this period." minHeight="240px">
            <StackedChart
                type="column"
                height={240}
                backgroundColor="#ffffff"
                data={data}
                yAxisTitle="Violations"
                showGridLines={false}
                noGap={true}
                customXaxis={X_AXIS}
                defaultChartOptions={CHART_OPTIONS}
                tooltipFormatter={tooltip}
                graphPointClick={openBar}
                exportingDisabled={true}
            />
        </CardWithHeader>
    )
}

export default ViolationsTrendCard
