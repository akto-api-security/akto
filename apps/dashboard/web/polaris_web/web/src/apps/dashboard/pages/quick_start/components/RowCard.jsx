import { Avatar, Button, Card, Text, VerticalStack, HorizontalStack, Badge, Box } from '@shopify/polaris';

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
            <div className='connection-card-container'>
                <VerticalStack gap="5">
                    <HorizontalStack gap="2" align='space-between'>
                        <Box padding={"2"} borderWidth='1' borderColor='border-subdued' borderRadius='2'>
                        <Avatar customer size="small" name={cardObj.label} source={cardObj.icon} shape='square'/>
                        </Box>
                        {cardObj.badge ? <Badge size='small' status='info'>{cardObj.badge}</Badge> : null}
                    </HorizontalStack>
                    <VerticalStack gap="1">
                        <Text variant="headingMd" as="h5">{cardObj.label}</Text>
                        <Text variant="bodyMd" color='subdued'>{cardObj.text}</Text>
                    </VerticalStack>
                </VerticalStack>

                <HorizontalStack gap={"4"} align='start'>
                    <Button onClick={handleAction}>{buttonText}</Button>
                    <Button plain onClick={goToDocs} size='medium'>See Docs</Button>
                </HorizontalStack>
            </div>
        </Card>
    )
}

export default RowCard