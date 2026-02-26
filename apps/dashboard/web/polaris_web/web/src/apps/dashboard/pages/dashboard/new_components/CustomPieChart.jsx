import { Card, VerticalStack, Box } from '@shopify/polaris'
import DonutChart from '../../../components/shared/DonutChart'
import ComponentHeader from './ComponentHeader'
import GraphCustomLabels from './GraphCustomLabels'

const CustomPieChart = ({ title = "", subtitle = "", graphData = {}, itemId = "", onRemoveComponent, tooltipContent = "", onSegmentClick }) => {
    const total = Object.values(graphData).reduce((sum, item) => sum + item.text, 0)
    const formattedTotal = total.toLocaleString()

    const labels = Object.keys(graphData).map(key => ({
        label: key,
        color: graphData[key].color
    }))

    return (
        <Card>
            <VerticalStack gap="4" inlineAlign='start' blockAlign="center">
                <ComponentHeader title={title} itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />
                <Box width='100%' minHeight='210px'>
                    <VerticalStack gap="2" inlineAlign='center' blockAlign='center'>
                        <DonutChart
                            title={subtitle}
                            subtitle={formattedTotal}
                            data={graphData}
                            size={200}
                            pieInnerSize="60%"
                            invertTextSizes={true}
                            onSegmentClick={onSegmentClick}
                        />
                        <GraphCustomLabels labels={labels} />
                    </VerticalStack>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default CustomPieChart
