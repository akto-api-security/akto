import { useState } from 'react'
import { Box, Button, HorizontalStack, Text, Icon } from '@shopify/polaris'
import { DeleteMinor, DragHandleMinor } from '@shopify/polaris-icons'

const ComponentHeader = ({ title, itemId, onRemove }) => {
    const [isHovered, setIsHovered] = useState(false)

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
                        <Text variant='headingMd'>{title}</Text>
                    </HorizontalStack>
                    <Button monochrome plain icon={DeleteMinor} onClick={() => onRemove(itemId)} />
                </HorizontalStack>
            </Box>
        </Box>
    )
}

export default ComponentHeader
