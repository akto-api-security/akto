import { Box, Button, HorizontalStack, Text, Tooltip } from '@shopify/polaris'
import { DeleteMinor } from '@shopify/polaris-icons'

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
            <HorizontalStack blockAlign="center" align='space-between'>
                {tooltipContent ? (
                    <Tooltip content={tooltipContent300} dismissOnMouseOut preferredPosition="above">
                        <Text variant='headingMd' as="span">
                            <span style={titleStyle}>{title}</span>
                        </Text>
                    </Tooltip>
                ) : (
                    <Text variant='headingMd'>{title}</Text>
                )}
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
