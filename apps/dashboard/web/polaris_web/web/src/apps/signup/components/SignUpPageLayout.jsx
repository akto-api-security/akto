import { Avatar, Box, Button, Frame, HorizontalStack, Page, VerticalStack } from '@shopify/polaris'
import React from 'react'
import ToastComponent from './ToastComponent'

function SignUpPageLayout({customComponent}) {
    return (
        <div className='login-page'>
        <Frame >
          <Page fullWidth >
            <Box padding="10" paddingBlockStart={"24"}>
              <div style={{display: "flex", justifyContent: 'space-between', flexDirection: "column"}}>
                <HorizontalStack align="center">
                  <Box width='400px'>
                    <VerticalStack gap={8}>
                      <HorizontalStack align='center'>
                        <div className="akto-logo">
                          <Avatar source="/public/akto_name_with_logo.svg" shape="round" size="2xl-experimental" />
                        </div>
                      </HorizontalStack>
                      {customComponent}
  
                    </VerticalStack>
                    <div style={{bottom: "40px", position: "absolute", width: '400px'}}>
                      <HorizontalStack gap={3} align="center">
                        <Button plain onClick={() => window.open("https://www.akto.io/terms-and-policies","_blank")}>Terms of use</Button>
                        <div style={{width: '1px', height: '24px', background: "#E1E3E5"}} />
                        <Button plain onClick={() => window.open("https://www.akto.io/terms/privacy","_blank")}>Privacy policy</Button>
                      </HorizontalStack>
                    </div>
                  </Box>
                </HorizontalStack>
              </div>
            </Box>
          </Page>
          <ToastComponent />
        </Frame>
      </div>
    )
}

export default SignUpPageLayout