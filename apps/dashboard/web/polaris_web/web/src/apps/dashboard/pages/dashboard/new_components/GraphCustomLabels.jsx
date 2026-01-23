import { HorizontalStack, Text } from '@shopify/polaris'

const GraphCustomLabels = ({ labels = [] }) => {
    return (
        <HorizontalStack gap={4} align='center' blockAlign='center' wrap>
            {
                labels.map((labelObj, idx) => (
                    <HorizontalStack key={`${idx}-${labelObj.label}`} gap={2} blockAlign='center' align='center'>
                        <div style={{ width: '8px', height: '8px', backgroundColor: labelObj.color || '#3d3d3d', borderRadius: '50%' }} />
                        <Text variant='bodyMd'>{labelObj.label}</Text>
                    </HorizontalStack>
                ))
            }
        </HorizontalStack>
    )
}

export default GraphCustomLabels
