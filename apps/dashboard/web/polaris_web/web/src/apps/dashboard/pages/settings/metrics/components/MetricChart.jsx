import { LegacyCard, EmptyState } from "@shopify/polaris"
import GraphMetric from '../../../../components/GraphMetric'

/**
 * Displays a single metric chart or empty state
 * @param {string} metricId - Metric identifier
 * @param {Array} data - Chart data
 * @param {string} title - Chart title
 * @param {string} description - Chart description
 * @param {Function} chartOptions - Function to get chart options
 */
function MetricChart({ metricId, data, title, description, chartOptions }) {
    const hasData = data && data.length > 0

    return (
        <LegacyCard.Section key={metricId}>
            {hasData ? (
                <GraphMetric
                    data={data}
                    type='spline'
                    color='#6200EA'
                    areaFillHex="true"
                    height="330"
                    title={title || metricId}
                    subtitle={description || ''}
                    defaultChartOptions={chartOptions}
                    background-color="#000000"
                    text="true"
                    inputMetrics={[]}
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
