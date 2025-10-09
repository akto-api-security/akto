import { Box, Button, ButtonGroup, Checkbox, Divider, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'
import InformationBannerComponent from './shared/InformationBannerComponent';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import api from '../api';
import func from "@/util/func"
import AktoDastOptions from './AktoDastOptions';
import Dropdown from '../../../components/layouts/Dropdown';
import testingApi from '../../testing/api'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const AktoJax = () => {
    const [loading, setLoading] = useState(false)

    const [hostname, setHostname] = useState('')
    const [authType, setAuthType] = useState("none")
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [apiKey, setApiKey] = useState('')

    const [testRolesArr, setTestRolesArr] = useState([])
    const [testRole, setTestRole] = useState("")

    const [outscopeUrls, setOutscopeUrls] = useState('');
    const [maxPageVisits, setMaxPageVisits] = useState('');
    const [domLoadTimeout, setDomLoadTimeout] = useState('');
    const [waitAfterEvent, setWaitAfterEvent] = useState('');
    const [enableJsRendering, setEnableJsRendering] = useState(true);
    const [parseSoapServices, setParseSoapServices] = useState(true);
    const [parseRestServices, setParseRestServices] = useState(true);
    const [clickExternalLinks, setClickExternalLinks] = useState(false);

    const goToDocs = () => {
        window.open("https://docs.akto.io/dast/akto-dast")
    }

    const primaryAction = () => {
        if(hostname?.length == 0 || hostname == undefined) {
            func.setToast(true, true, "Please enter a valid hostname.")
            return
        }

        setLoading(true)
        api.initiateCrawler(hostname, email, password, apiKey, window.location.origin, testRole, outscopeUrls).then((res) => {
            func.setToast(true, false, "Crawler initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating crawler:", err)
        }).finally(() => {
            setLoading(false)
            setHostname('')
            setAuthType('none')
            setEmail('')
            setPassword('')
            setTestRole('')
        })
    }

    const fetchTestRoles = async () => {
        const testRolesResponse = await testingApi.fetchTestRoles()
            var testRoles = testRolesResponse.testRoles.map(testRole => {
                return {
                    "label": testRole.name,
                    "value": testRole.hexId
                }
            })
        setTestRolesArr(testRoles)
    }

    useEffect(() => {
        fetchTestRoles()
    }, [])

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our DAST to capture traffic and instantly send it to your dashboard for real-time insights. If you want to scale up and get more accurate data, we recommend integrating with AWS or GCP traffic mirroring. This ensures smooth, automated data collection with minimal noise and maximum accuracy.
            </Text>

            <InformationBannerComponent docsUrl="https://docs.akto.io/dast/akto-dast"
                    content="Please ensure the pre-requisites " 
            />

            <AktoDastOptions
                outscopeUrls={outscopeUrls}
                setOutscopeUrls={setOutscopeUrls}
                maxPageVisits={maxPageVisits}
                setMaxPageVisits={setMaxPageVisits}
                domLoadTimeout={domLoadTimeout}
                setDomLoadTimeout={setDomLoadTimeout}
                waitAfterEvent={waitAfterEvent}
                setWaitAfterEvent={setWaitAfterEvent}
                enableJsRendering={enableJsRendering}
                setEnableJsRendering={setEnableJsRendering}
                parseSoapServices={parseSoapServices}
                setParseSoapServices={setParseSoapServices}
                parseRestServices={parseRestServices}
                setParseRestServices={setParseRestServices}
                clickExternalLinks={clickExternalLinks}
                setClickExternalLinks={setClickExternalLinks}
            />

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField label="Enter your website URL" value={hostname} type='url' onChange={(value) => setHostname(value)} placeholder='https://example.com' />
                <PasswordTextField label={
                    <HorizontalStack gap={1}>
                        <Text>Enter your</Text>
                        <Button plain onClick={() => window.open(window.location.origin + "/dashboard/settings/integrations/akto_apis")}> Akto X-API-Key</Button>
                    </HorizontalStack>
                } setField={setApiKey} onFunc={true} field={apiKey}/>
                
                <Dropdown
                    label="Auth type"
                    menuItems={[
                        { value: "none", label: "None", id: "none" },
                        { value: "emailpass", label: "Email & Password", id: "emailpass" },
                        { value: "test-role", label: "Test Role", id: "test-role" },
                    ]}
                    initial={authType}
                    selected={(val) => setAuthType(val)}
                />

                {
                    authType === 'emailpass' &&
                    <>
                        <TextField label="Enter your email" value={email} type='email' onChange={(value) => setEmail(value)} placeholder='john@akto.io' />
                        <PasswordTextField label="Enter your password" setField={setPassword} onFunc={true} field={password}/>
                    </>
                }

                {
                    authType === 'test-role' &&
                    <>
                        <Dropdown
                            menuItems={testRolesArr}
                            label={"Select " + mapLabel("Test", getDashboardCategory()) + " Role"}
                            initial={testRole}
                            selected={(role) => setTestRole(role)}
                        />
                    </>
                }
                
                <div style={{height:"20px"}}></div>

                <ButtonGroup>
                    <Button onClick={primaryAction} primary disabled={hostname?.length == 0} loading={loading}>Crawl</Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default AktoJax