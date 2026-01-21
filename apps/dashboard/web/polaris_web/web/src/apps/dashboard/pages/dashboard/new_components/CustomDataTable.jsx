import { Card, VerticalStack, Box, HorizontalStack, Text, DataTable } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const CustomDataTable = ({ title = "", data = [], showSignalIcon = true, itemId = "", onRemoveComponent }) => {
    const rows = data.map(item => [
        <HorizontalStack gap={3} blockAlign='center'>
            {showSignalIcon && <img src='/public/menu-graph.svg' alt='growth-icon' />}
            <div style={{ maxWidth: '300px', wordBreak: 'break-word', overflowWrap: 'break-word' }}>
                <Text variant='bodyMd' fontWeight='medium'>{item.name}</Text>
            </div>
        </HorizontalStack>,
        <div>
            <Text variant='bodyMd' fontWeight='medium'>{item.value}</Text>
        </div>
    ])

    return (
        <Card>
            <VerticalStack gap="4">
                <ComponentHeader title={title} itemId={itemId} onRemove={onRemoveComponent} />

                <Box width='100%'>
                    <DataTable
                        columnContentTypes={['text', 'numeric']}
                        headings={[]}
                        rows={rows}
                        hideScrollIndicator
                    />
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default CustomDataTable
