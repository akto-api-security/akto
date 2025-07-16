import { Button, ButtonGroup, Checkbox, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import InformationBannerComponent from './shared/InformationBannerComponent';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import api from '../api';
import func from "@/util/func"

const AktoJax = () => {
    const [loading, setLoading] = useState(false)

    const [hostname, setHostname] = useState('')
    const [requireAuth, setRequireAuth] = useState(false)
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [apiKey, setApiKey] = useState('')

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connector/dast/aktodast")
    }

    const primaryAction = () => {
        if(hostname?.length == 0 || hostname == undefined) {
            func.setToast(true, true, "Please enter a valid hostname.")
            return
        }

        if(!requireAuth) {
            setEmail('')
            setPassword('')
        }

        setLoading(true)
        api.initiateCrawler(hostname, email, password, apiKey, window.location.origin).then((res) => {
            func.setToast(true, false, "Crawler initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating crawler:", err)
            func.setToast(true, true, "An error occurred while initiating the crawler. Please try again later.")
        }).finally(() => {
            setLoading(false)
            setHostname('')
            setRequireAuth(false)
            setEmail('')
            setPassword('')
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our DAST to capture traffic and instantly send it to your dashboard for real-time insights. If you want to scale up and get more accurate data, we recommend integrating with AWS or GCP traffic mirroring. This ensures smooth, automated data collection with minimal noise and maximum accuracy.
            </Text>

            <InformationBannerComponent docsUrl="https://docs.akto.io/traffic-connector/dast/aktodast"
                    content="Please ensure the pre-requisites " 
            />

            <VerticalStack gap="2">
                <TextField label="Enter your website URL" value={hostname} type='url' onChange={(value) => setHostname(value)} placeholder='https://example.com' />
                <PasswordTextField label={
                    <HorizontalStack gap={1}>
                        <Text>Enter your</Text>
                        <Button plain onClick={() => window.open(window.location.origin + "/dashboard/settings/integrations/akto_apis")}> Akto X-API-Key</Button>
                    </HorizontalStack>
                } setField={setApiKey} onFunc={true} field={apiKey}/>

                <Checkbox label="This site requires login?" checked={requireAuth} onChange={() => setRequireAuth(!requireAuth)} />

                {
                    requireAuth &&
                    <>
                        <TextField label="Enter your email" value={email} type='email' onChange={(value) => setEmail(value)} placeholder='john@akto.io' />
                        <PasswordTextField label="Enter your password" setField={setPassword} onFunc={true} field={password}/>
                    </>
                }

                <ButtonGroup>
                    <Button onClick={primaryAction} primary disabled={hostname?.length == 0} loading={loading}>Crawl</Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default AktoJax