import { LegacyCard, EmptyState } from "@shopify/polaris"
import GraphMetric from '../../../../components/GraphMetric'

/**
 * Displays a single metric chart or empty state
 * @param {string} metricId - Metric identifier
 * @param {Array} data - Chart data
 * @param {string} title - Chart title
 * @param {string} description - Chart description
 * @param {Function} chartOptions - Function to get chart options
 * @param {number} timezoneOffsetMinutes - Timezone offset in minutes
 */
function MetricChart({ metricId, data, title, description, chartOptions, timezoneOffsetMinutes }) {
    const hasData = data && data.length > 0

    return (
        <LegacyCard.Section key={metricId}>
            {hasData ? (
                <GraphMetric
                    data={data}
                    color='#6200EA'
                    height="330"
                    title={title || metricId}
                    subtitle={description || ''}
                    defaultChartOptions={chartOptions}
                    background-color="#000000"
                    text="true"
                    inputMetrics={[]}
                    timezoneOffsetMinutes={timezoneOffsetMinutes}
                />
            ) : (
                <EmptyState
                    heading={title || metricId}
                    footerContent="No Graph Data exist !"
                >
                    <p>{description || ''}</p>
                </EmptyState>
            )}
        </LegacyCard.Section>
    )
}

export default MetricChart
