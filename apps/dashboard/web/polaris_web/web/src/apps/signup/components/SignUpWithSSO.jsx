
import React, { useState } from 'react'
import SignUpPageLayout from './SignUpPageLayout';
import { Form, Text, TextField, VerticalStack } from '@shopify/polaris';
import api from '../api';

function SignUpWithSSO() {

    const [email, setEmail] = useState('')

    const handleSubmit = async() => {
        await api.triggerGoogleSSO(email)
    }

    const customComponent = (
        <VerticalStack gap={8}>
            <Text alignment="center" variant="heading2xl">{"Sign in with SSO"}</Text>
            <VerticalStack gap={5}>
                <Form onSubmit={() => handleSubmit()}>
                    <VerticalStack gap={4}>
                        <div className='form-class'>
                            <TextField onChange={setEmail} value={email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
                        </div>
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