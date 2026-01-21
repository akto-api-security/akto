import { Card, VerticalStack, Box } from '@shopify/polaris'
import LineChart from '../../../components/charts/LineChart'
import ComponentHeader from './ComponentHeader'
import GraphCustomLabels from './GraphCustomLabels'

const CustomLineChart = ({ title = "", chartData = [], labels = [], itemId = "", onRemoveComponent, chartHeight = 290, tooltipContent = "" }) => {
    return (
        <Card>
            <VerticalStack gap="6" inlineAlign='start' blockAlign="center">
                <ComponentHeader title={title} itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />

                <Box width='100%'>
                    <LineChart
                        data={chartData}
                        height={chartHeight}
                        type="line"
                        text={true}
                        showGridLines={true}
                        exportingDisabled={true}
                        defaultChartOptions={{
                            xAxis: {
                                type: 'datetime',
                                dateTimeLabelFormats: {
                                    day: '%b %e',
                                    month: '%b',
                                },
                                title: { text: '' },
                                visible: true,
                                gridLineWidth: 0
                            },
                            yAxis: {
                                title: { text: '' },
                                gridLineWidth: 1,
                                min: 0,
                                labels: {
                                    formatter: function() {
                                        return this.value.toLocaleString();
                                    }
                                }
                            },
                            legend: {
                                enabled: false
                            }
                        }}
                    />
                </Box>

                <GraphCustomLabels labels={labels} />
            </VerticalStack>
        </Card>
    )
}

export default CustomLineChart
