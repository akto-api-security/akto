import React from 'react'
import { Button, Text, VerticalStack, HorizontalStack, Card, Icon, Box } from '@shopify/polaris'
import { EmailIcon } from '@shopify/polaris-icons'
import SignUpPageLayout from './SignUpPageLayout'

function ContactSales() {
    const handleEmailContact = () => {
        window.location.href = "mailto:sales@akto.io?subject=Request Access to Akto&body=Hello, I would like to request access to Akto. Please contact me to discuss further."
    }

    const customComponent = (
        <VerticalStack gap={8}>
            <VerticalStack gap={4} alignment="center">
                <Text alignment="center" variant="heading2xl">Request Access</Text>
                <Text alignment="center" variant="bodyLg" color="subdued">
                    Akto access requires an invitation. Contact our team to request access to the platform.
                </Text>
            </VerticalStack>
            
            <Card sectioned>
                <VerticalStack gap={4}>
                    <HorizontalStack align="space-between" blockAlign="center">
                        <VerticalStack gap={2}>
                            <Text variant="headingMd">Request Platform Access</Text>
                            <Text variant="bodyMd" color="subdued">
                                Email us to request access to Akto
                            </Text>
                        </VerticalStack>
                        <Icon source={EmailIcon} />
                    </HorizontalStack>
                    <Button 
                        fullWidth 
                        primary
                        size="large" 
                        onClick={handleEmailContact}
                    >
                        Request Access
                    </Button>
                </VerticalStack>
            </Card>
        </VerticalStack>
    )

    const imageStyle = {
        width: '953px',
        borderRadius: '12px',
        border: '6px solid',
        marginTop: '90px'
    }

    return (
        <div style={{display: 'flex', height: '100vh', overflow: 'hidden'}}>
            <div style={{flex: '4', overflow: "hidden", boxShadow: "-1px 0px 0px 0px #E4E5E7 inset"}}>
                <Box paddingBlockStart={"24"} paddingInlineStart={"20"} paddingInlineEnd={"8"}>
                    <VerticalStack gap="6">
                        <Text variant="heading3xl" fontWeight="medium">
                            {`"Akto has revolutionized our API security approach. Their team provides exceptional support and expertise."`}
                        </Text>
                        <Box>
                            <Text variant="headingLg">— DevSecOps Team</Text>
                            <Text fontWeight="medium" color="subdued">Fortune 500 Company</Text>
                        </Box>
                        <img src="/public/productss.png" alt='Akto Dashboard' style={{...imageStyle}}/>
                    </VerticalStack>
                </Box>
            </div>
            <div style={{flex: '3'}}>
                <SignUpPageLayout
                    customComponent={customComponent}
                />
            </div>
        </div>
    )
}

export default ContactSales