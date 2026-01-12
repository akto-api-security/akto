import { Box, Button, HorizontalStack, Text } from '@shopify/polaris'
import { DeleteMinor } from '@shopify/polaris-icons'

const ComponentHeader = ({ title, itemId, onRemove }) => {
    return (
        <Box width='100%'>
            <HorizontalStack blockAlign="center" align='space-between'>
                <Text variant='headingMd'>{title}</Text>
                <HorizontalStack gap={2}>
                    <Button monochrome plain icon={DeleteMinor} onClick={() => onRemove(itemId)} />
                    <div className='graph-menu'>
                        <img src={"/public/MenuVerticalIcon.svg"} alt='graph-menu' />
                    </div>
                </HorizontalStack>
            </HorizontalStack>
        </Box>
    )
}

export default ComponentHeader
