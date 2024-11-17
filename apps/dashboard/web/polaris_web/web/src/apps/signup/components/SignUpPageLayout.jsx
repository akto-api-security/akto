import { Avatar, Box, Button, Frame, InlineStack, Page, BlockStack } from '@shopify/polaris'
import React from 'react'
import ToastComponent from './ToastComponent'

function SignUpPageLayout({customComponent}) {
    return (
      <div className='login-page'>
      <Frame >
        <Page fullWidth >
          <Box padding="1000" paddingBlockStart={"2400"}>
            <div style={{display: "flex", justifyContent: 'space-between', flexDirection: "column"}}>
              <InlineStack align="center">
                <Box width='400px'>
                  <BlockStack gap={800}>
                    <InlineStack align='center'>
                      <div className="akto-logo">
                        <Avatar source="/public/akto_name_with_logo.svg" size="xl" />
                      </div>
                    </InlineStack>
                    {customComponent}
                  </BlockStack>
                  <div style={{bottom: "40px", position: "absolute", width: '400px'}}>
                    <InlineStack gap={300} align="center">
                      <Button

                        onClick={() => window.open("https://www.akto.io/terms-and-policies","_blank")}
                        variant="plain">Terms of use</Button>
                      <div style={{width: '1px', height: '24px', background: "#E1E3E5"}} />
                      <Button

                        onClick={() => window.open("https://www.akto.io/terms/privacy","_blank")}
                        variant="plain">Privacy policy</Button>
                    </InlineStack>
                  </div>
                </Box>
              </InlineStack>
            </div>
          </Box>
        </Page>
        <ToastComponent />
      </Frame>
    </div>
    );
}

export default SignUpPageLayout