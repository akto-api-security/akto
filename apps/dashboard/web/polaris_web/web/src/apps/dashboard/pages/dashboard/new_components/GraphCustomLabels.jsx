import { HorizontalStack, Text } from '@shopify/polaris'

const GraphCustomLabels = ({ labels = [], onLabelClick }) => {
    return (
        <HorizontalStack gap={4} align='center' blockAlign='center' wrap>
            {
                labels.map((labelObj, idx) => (
                    <HorizontalStack
                        key={`${idx}-${labelObj.label}`}
                        gap={2}
                        blockAlign='center'
                        align='center'
                        onClick={onLabelClick ? () => onLabelClick(labelObj.filterId) : undefined}
                    >
                        <div style={{ width: '8px', height: '8px', backgroundColor: labelObj.color || '#3d3d3d', borderRadius: '50%', ...(onLabelClick ? { cursor: 'pointer' } : {}) }} />
                        <Text variant='bodyMd'>
                            <span style={onLabelClick ? { cursor: 'pointer' } : {}}>{labelObj.label}</span>
                        </Text>
                    </HorizontalStack>
                ))
            }
        </HorizontalStack>
    )
}

export default GraphCustomLabels
