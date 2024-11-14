
import React, { useState } from 'react'
import SignUpPageLayout from './SignUpPageLayout';
import { Button, Form, InlineStack, Text, TextField, VerticalStack } from '@shopify/polaris';

function SignUpWithSSO() {

    const [email, setEmail] = useState('')

    const handleSubmit = () => {
        window.location.href = "/trigger-saml-sso?email=" + email
    }

    const customComponent = (
        <VerticalStack gap={8}>
            <Text alignment="center" variant="heading2xl">{"Sign in with SSO"}</Text>
            <VerticalStack gap={5}>
                <Form onSubmit={() => handleSubmit()}>
                    <VerticalStack gap={3}>
                        <div className='form-class'>
                            <TextField onChange={setEmail} value={email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
                        </div>
                        <InlineStack align="end">
                            <Button size="medium" onClick={handleSubmit}  variant="primary">Submit</Button>
                        </InlineStack>
                    </VerticalStack>
                </Form>
            </VerticalStack>
        </VerticalStack>
    )

    return (
       <SignUpPageLayout
            customComponent={customComponent}
        />
    )
}

export default SignUpWithSSO