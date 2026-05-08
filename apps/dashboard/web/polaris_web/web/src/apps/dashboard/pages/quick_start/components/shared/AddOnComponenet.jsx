import { Card, VerticalStack, Button, Box, Avatar, Text } from '@shopify/polaris'
import func from '../../../../../../util/func';

function AddOnComponenet({featureLabel, featureComponent}) {

    if(featureLabel && featureComponent && func.checkForFeatureSaas(featureLabel)){
        return featureComponent;
    }

  return (
      <div className='card-items'>
           <Card background="bg-subdued">
            <VerticalStack gap='3' align="start">
                <Avatar source="/public/PaymentsMajor.svg" size="small" shape='square'/>
                <VerticalStack gap='2'>
                    <Text variant='headingMd'>This is an add-on connector</Text>
                    <Text variant='bodyMd'>
                        In order to use this connector, please contact us.
                    </Text>
                </VerticalStack>
                <Box paddingBlockStart={3}>
                    <Button primary onClick={
                        () => {
                        window.open("https://calendly.com/ankita-akto/akto-demo", "_blank");
                    }
                }>
                    Contact Us
                </Button>
                </Box>
            </VerticalStack>
           </Card>
        </div>
  )
}

export default AddOnComponenet
