import { Card, BlockStack, Button, Box, Avatar, Text } from '@shopify/polaris'
import React from 'react'

function AddOnComponenet() {
  return (
      <div className='card-items'>
           <Card background="bg-subdued">
            <BlockStack gap='300' align="start">
                <Avatar source="/public/PaymentsMajor.svg" size="sm" shape='square'/>
                <BlockStack gap='200'>
                    <Text variant='headingMd'>This is an add-on connector</Text>
                    <Text variant='bodyMd'>
                        In order to use this connector, please contact us.
                    </Text>
                </BlockStack>
                <Box paddingBlockStart={300}>
                    <Button

                        onClick={
                            () => {
                            window.open("https://calendly.com/ankita-akto/akto-demo", "_blank");
                        }
                    }
                        variant="primary">
                    Contact Us
                </Button>
                </Box>
            </BlockStack>
           </Card>
        </div>
  );
}

export default AddOnComponenet
