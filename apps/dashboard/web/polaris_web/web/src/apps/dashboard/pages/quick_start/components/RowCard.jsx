import { Avatar, Link, Button, Card, Text, VerticalStack, HorizontalStack, Badge, Box } from '@shopify/polaris';

function RowCard(props) {

    const {cardObj, onButtonClick, buttonText} = props ;
    const goToDocs = () => {
        window.open(cardObj.docsUrl)
    }

    const handleAction = () => {
        onButtonClick(cardObj)
    }

    return (
        <Card>
            <div style={{display: 'flex', gap: '12px'}}>
                <VerticalStack gap="1">
                    <div style={{display: 'flex' , justifyContent: 'space-between'}}>
                        <Box padding={"2"} borderWidth='1' borderColor='border-subdued' borderRadius='2'>
                        <Avatar customer size="extraSmall" name={cardObj.label} source={cardObj.icon} shape='square'/>
                        </Box>
                        <Box paddingBlockStart="1">
                            {cardObj.badge ? <Badge size='small' status='info'>{cardObj.badge}</Badge> : null}
                        </Box>
                    </div>
                </VerticalStack>
                <VerticalStack gap="1">
                    <Link monochrome removeUnderline textAlign="left" onClick={handleAction}><Text  variant="headingMd" as="h5">{cardObj.label}</Text></Link>
                    <Box minHeight="80px">
                        <Text variant="bodyMd" color='subdued'>{cardObj.text}</Text>
                    </Box>
                </VerticalStack>
            </div>
        </Card>
    )
}

export default RowCard