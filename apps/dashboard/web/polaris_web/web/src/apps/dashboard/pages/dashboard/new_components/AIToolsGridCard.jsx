import { Card, VerticalStack, Box } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'
import TreeMapChart from '../../../components/charts/TreeMapChart'

const AIToolsGridCard = ({ toolsData = [], itemId = "", onRemoveComponent }) => {
    // Transform data for Highcharts Treemap
    const treeMapData = toolsData.map(tool => ({
        name: tool.name,
        value: tool.value || 0,
        icon: tool.icon
    }))

    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader title='AI Tools in Use' itemId={itemId} onRemove={onRemoveComponent} />

                <Box>
                    <TreeMapChart
                        data={treeMapData}
                        height={250}
                        exportingDisabled={true}
                    />
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default AIToolsGridCard
