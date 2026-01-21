import { useState } from 'react'
import { Box, Button, HorizontalStack, Text, Icon, Tooltip } from '@shopify/polaris'
import { DeleteMinor, DragHandleMinor } from '@shopify/polaris-icons'

const ComponentHeader = ({ title, itemId, onRemove, tooltipContent }) => {
    const [isHovered, setIsHovered] = useState(false)

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
                className='graph-menu'
                paddingBlockStart="1" 
                paddingBlockEnd="1"
                borderRadius="100"
                background={isHovered ? "bg-surface-hover" : undefined}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
            >
                <HorizontalStack blockAlign="center" align='space-between'>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Box opacity={isHovered ? "1" : "0"}>
                            <Icon source={DragHandleMinor} color="subdued" />
                        </Box>
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
                    <Box opacity={isHovered ? "1" : "0"}>
                        <Button monochrome plain icon={DeleteMinor} onClick={() => onRemove(itemId)} />
                    </Box>
                </HorizontalStack>
            </Box>
        </Box>
    )
}

export default ComponentHeader
