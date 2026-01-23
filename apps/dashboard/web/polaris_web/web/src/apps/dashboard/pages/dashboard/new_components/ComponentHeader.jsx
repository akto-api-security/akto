import { Box, Button, HorizontalStack, Text, Icon, Tooltip } from '@shopify/polaris'
import { CancelMinor, DragHandleMinor } from '@shopify/polaris-icons'

const ComponentHeader = ({ title, itemId, onRemove, tooltipContent }) => {
    const titleStyle = {
        borderBottom: '1px dotted #BEBEBF',
        display: 'inline-block',
        cursor: 'default'
    }

    const tooltipContent300 = tooltipContent ? (
        <div style={{ maxWidth: '300px' }}>
            {tooltipContent}
        </div>
    ) : null

    return (
        <Box width='100%'>
            <Box 
                paddingBlockStart="1" 
                paddingBlockEnd="1"
                borderRadius="100"
            >
                <HorizontalStack blockAlign="center" align='space-between'>
                    <HorizontalStack gap="2" blockAlign="center">
                        <div className="drag-handle-icon">
                            <Icon source={DragHandleMinor} color="subdued" />
                        </div>
                        {tooltipContent ? (
                            <Tooltip content={tooltipContent300} dismissOnMouseOut preferredPosition="above">
                                <Text variant='headingMd' as="span">
                                    <span style={titleStyle}>{title}</span>
                                </Text>
                            </Tooltip>
                        ) : (
                            <Text variant='headingMd'>{title}</Text>
                        )}
                    </HorizontalStack>
                    <Button monochrome plain icon={CancelMinor} onClick={() => onRemove(itemId)} />
                </HorizontalStack>
            </Box>
        </Box>
    )
}

export default ComponentHeader
