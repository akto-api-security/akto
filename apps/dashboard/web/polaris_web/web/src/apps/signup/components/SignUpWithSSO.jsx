
import React, { useState } from 'react'
import SignUpPageLayout from './SignUpPageLayout';
import { Button, Form, InlineStack, Text, TextField, BlockStack } from '@shopify/polaris';

function SignUpWithSSO() {

    const [email, setEmail] = useState('')

    const handleSubmit = () => {
        window.location.href = "/trigger-saml-sso?email=" + email
    }

    const customComponent = (
        <BlockStack gap={800}>
            <Text alignment="center" variant="heading2xl">{"Sign in with SSO"}</Text>
            <BlockStack gap={500}>
                <Form onSubmit={() => handleSubmit()}>
                    <BlockStack gap={300}>
                        <div className='form-class'>
                            <TextField onChange={setEmail} value={email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
                        </div>
                        <InlineStack align="end">
                            <Button size="medium" onClick={handleSubmit}  variant="primary">Submit</Button>
                        </InlineStack>
                    </BlockStack>
                </Form>
            </BlockStack>
        </BlockStack>
    )

    return (
       <SignUpPageLayout
            customComponent={customComponent}
        />
    )
}

export default SignUpWithSSO