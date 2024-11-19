import React from 'react'
import SignUp from "../components/SignUp"
import { Box, Text, BlockStack } from '@shopify/polaris'

function SignupPage() {

    const imageStyle = {
        width: '953px',
        borderRadius: '12px',
        border: '6px solid',
        marginTop: '90px'
    }
    return (
        <div style={{display: 'flex', height: '100vh', overflow: 'hidden'}}>
            <div style={{flex: '4', overflow: "hidden", boxShadow: "-1px 0px 0px 0px #E4E5E7 inset"}}>
                <Box paddingBlockStart={"2400"} paddingInlineStart={"2000"} paddingInlineEnd={"800"}>
                    <BlockStack gap="600">
                        <div className='akto-heading'>
                            <Text variant="heading2xl" fontWeight="medium">
                                {`"It's truly a game-changer and we highly recommend Akto to anyone looking to effortlessly secure their API endpoints."`}
                            </Text>
                        </div>
                        <Box>
                            <Text variant="headingLg">— Security team</Text>
                            <Text fontWeight="medium" tone="subdued">Enterprise SaaS customer</Text>
                        </Box>
                        <img  src="/public/productss.png" alt='ss' style={{...imageStyle}}/>
                    </BlockStack>
                </Box>
            </div>
            <div style={{flex: '3'}}>
                <SignUp />
            </div>
        </div>
    )
}

export default SignupPage