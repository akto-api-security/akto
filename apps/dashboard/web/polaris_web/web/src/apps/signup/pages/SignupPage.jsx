import React from 'react'
import SignUp from "../components/SignUp"
import { Box, Text, VerticalStack } from '@shopify/polaris'

function SignupPage() {

    // No border: mainproduct.png ships its own framed card on a purple bleed.
    const imageStyle = {
        width: '953px',
        borderRadius: '12px',
        marginTop: '90px'
    }
    return (
        <div style={{display: 'flex', height: '100vh', overflow: 'hidden'}}>
            <div style={{flex: '4', overflow: "hidden", boxShadow: "-1px 0px 0px 0px #E4E5E7 inset"}}>
                <Box paddingBlockStart={"24"} paddingInlineStart={"20"} paddingInlineEnd={"8"}>
                    <VerticalStack gap="6">
                        <Text variant="heading3xl" fontWeight="medium">
                            {`"It's truly a game-changer and we highly recommend Akto as an AI Security platform to anyone looking to secure their AI agents, MCPs, LLMs, Agent skills & gen AI apps."`}
                        </Text>
                        <Box>
                            <Text variant="headingLg">— Security team</Text>
                            <Text fontWeight="medium" color="subdued">Enterprise SaaS customer</Text>
                        </Box>
                        <img  src="/public/mainproduct.png" alt='Akto Agentic Discovery' style={{...imageStyle}}/>
                    </VerticalStack>
                </Box>
            </div>
            <div style={{flex: '3'}}>
                <SignUp />
            </div>
        </div>
    )
}

export default SignupPage