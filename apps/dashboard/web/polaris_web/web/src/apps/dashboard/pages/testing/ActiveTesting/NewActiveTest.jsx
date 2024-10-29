import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import { Box, Button, Checkbox, Divider, HorizontalStack, Icon, LegacyCard, Link, Text, TextField, VerticalStack } from '@shopify/polaris'
import ActiveTestingSteps from './ActiveTestingSteps'
import func from '@/util/func'
import CollectionSelection from '../../onboarding/components/CollectionSelection'
import Dropdown from '../../../components/layouts/Dropdown'
import TestSuiteResourceList from './TestSuiteResourceList'
import { default as observeApi } from "../../observe/api"
import {default as testingApi} from '../api'
import OnboardingStore from '../../onboarding/OnboardingStore'

const runTypeOptions = [{ label: "Daily", value: "Daily" }, { label: "Continuously", value: "Continuously" }, { label: "Now", value: "Now" }]
const hours = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

const amTimes = hours.map(hour => {
    let hourStr = hour + (hour == 12 ? " noon" : " am")
    return { label: hourStr, value: hour.toString() }
})
const pmTimes = hours.map(hour => {
    let hourStr = hour + (hour == 12 ? " midnight" : " pm")
    return { label: hourStr, value: `${hour + 12}` }
})
const hourlyTimes = [{ label: "Now", value: "Now" }, ...amTimes, ...pmTimes]

const runTimeMinutes = hours.reduce((abc, x) => {
    if (x < 6) {
        let label = x * 10 + " minutes"
        abc.push({ label, value: `${x * 10 * 60}` })
    }
    return abc
}, [])

const runTimeHours = hours.reduce((abc, x) => {
    if (x < 7) {
        let label = x + (x == 1 ? " hour" : " hours")
        abc.push({ label, value: `${x * 60 * 60}` })
    }
    return abc
}, [])
const testRunTimeOptions = [ ...runTimeMinutes, ...runTimeHours]

const maxRequests = hours.reduce((abc, x) => {
    if (x < 11) {
        let label = x * 10
        abc.push({ label, value: `${x * 10}` })
    }
    return abc
}, [])

const maxConcurrentRequestsOptions = [{ label: "Default", value: "-1" }, ...maxRequests]

const slackAlertsMenuItems = [{ label: "Enable", value: "true" }, { label: "Disable", value: "false" }]

const initialState = {
    categories: [],
    tests: {},
    selectedCategory: "BOLA",
    recurringDaily: false,
    continuousTesting: false,
    overriddenTestAppUrl: "",
    hasOverriddenTestAppUrl: false,
    startTimestamp: func.timeNow(),
    hourlyLabel: "Now",
    testRunTime: -1,
    testRunTimeLabel: "30 minutes",
    runTypeLabel: "Now",
    maxConcurrentRequests: -1,
    testName: "",
    authMechanismPresent: false,
    testRoleLabel: "No test role selected",
    testRoleId: "",
    sendSlackAlert: false
}

const NewActiveTest = () => {
    const [currentStep, setCurrentStep] = useState(0)
    const [slackAlertsVal, setSlackAlertsVal] = useState("true")
    const [authenticationMenuItems, setAuthenticationMenuItems] = useState([])
    const [slackIntegrated, setSlackIntegrated] = useState(false)
    const [selectedTests, setSelectedTests] = useState([])

    const collectionId = OnboardingStore(state => state.selectedCollection)

    const fetchData = async () => {
        observeApi.fetchSlackWebhooks().then((resp) => {
            const apiTokenList = resp.apiTokenList
            setSlackIntegrated(apiTokenList && apiTokenList.length > 0)
        })

        const testRolesResponse = await testingApi.fetchTestRoles()
        let testRoles = testRolesResponse.testRoles.map(testRole => {
            return {
                "label": testRole.name,
                "value": testRole.hexId
            }
        })
        testRoles.unshift({"label": "No test role selected", "value": ""})
        setAuthenticationMenuItems(testRoles)
    }

    useEffect(() => {
        fetchData()
    }, [])

    const [testRun, setTestRun] = useState({
        ...initialState
    })

    function getLabel(objList, value) {
        const obj = objList.find(obj => obj.value === value)
        return obj
    }

    function generateLabelForSlackIntegration() {
        return <HorizontalStack align='start' gap={1}>
            <Link url='/dashboard/settings/integrations/slack' target="_blank" rel="noopener noreferrer" style={{ color: "#3385ff", textDecoration: 'none' }}>
                Enable
            </Link>
            <Text>
                Slack integration to send alerts post completion
            </Text>
        </HorizontalStack>
    }

    const activeTestingStepsComp = [
        (
            <>
                <Divider />
                <Box padding={5}>
                    <VerticalStack gap={5}>
                        <TextField
                            label={"Name of the scan"}
                            value={testRun.testName}
                            onChange={(newVal) => setTestRun((prev) => {return {...prev, testName: newVal}})}
                            placeholder='Juice shop scan'
                            maxLength="60"
                            suffix={(
                                <Text>{testRun.testName.length}/60</Text>
                            )}
                        />

                        <CollectionSelection />
                    </VerticalStack>
                </Box>
                <Box paddingBlockEnd={"5"}>
                    <Divider />
                </Box>
            </>
        ),
        

        <>
            <Divider />
            <TestSuiteResourceList setSelectedTests={setSelectedTests} />
            <Box paddingBlockEnd={"5"}>
                <Divider />
            </Box>
        </>,


        <>
        <Divider />
            <Box padding={5}>
                <VerticalStack gap={5}>
                    <Text variant='headingMd'>Authentication token</Text>
                    <Dropdown
                        label={"Select authentication"}
                        placeHolder={"Authentication token"}
                        menuItems={authenticationMenuItems}
                        initial={"No test role selected"}
                        selected={(requests) => {
                            let testRole
                            if (!(requests === "No test role selected")){testRole = requests}
                            const testRoleOption = getLabel(authenticationMenuItems, requests)

                            setTestRun(prev => ({
                                ...prev,
                                testRoleId: testRole,
                                testRoleLabel: testRoleOption.label
                            }))
                        }}
                    />

                    <Box style={{marginInline: "-40px"}} paddingBlockEnd={"5"}>
                        <Divider />
                    </Box>

                    <Text variant='headingMd'>Schedule & Configuration</Text>
                    <HorizontalStack align='space-between'>
                        <Text>Run type</Text>
                        <div style={{ width: '400px'}}>
                            <Dropdown
                                menuItems={runTypeOptions}
                                initial={testRun.runTypeLabel}
                                selected={(runType) => {
                                    let recurringDaily = false
                                    let continuousTesting = false

                                    if(runType === 'Continuously') {
                                        continuousTesting = true
                                    } else if(runType === 'Daily') {
                                        recurringDaily = true
                                    }
                                    setTestRun(prev => ({
                                            ...prev,
                                            recurringDaily,
                                            continuousTesting,
                                            runTypeLabel: runType.label
                                    }))
                                }}
                            />
                        </div>
                    </HorizontalStack>

                    <HorizontalStack align='space-between'>
                        <Text>Select time</Text>
                        <div style={{ width: '400px'}}>
                            <Dropdown
                                disabled={testRun.continuousTesting === true}
                                menuItems={hourlyTimes}
                                initial={testRun.hourlyLabel}
                                selected={(hour) => {
                                    let startTimestamp

                                    if (hour === "Now") startTimestamp = func.timeNow()
                                    else {
                                        const dayStart = +func.dayStart(+new Date());
                                        startTimestamp = parseInt(dayStart / 1000) + parseInt(hour) * 60 * 60
                                    }

                                    const hourlyTime = getLabel(hourlyTimes, hour)
                                    setTestRun(prev => ({
                                        ...prev,
                                        startTimestamp,
                                        hourlyLabel: hourlyTime ? hourlyTime.label : ""
                                    }))
                                }}
                            />
                        </div>
                    </HorizontalStack>

                    <HorizontalStack align='space-between'>
                        <Text>Test run time</Text>
                        <div style={{ width: '400px'}}>
                            <Dropdown
                                menuItems={testRunTimeOptions}
                                initial={testRun.testRunTimeLabel}
                                selected={(timeInSeconds) => {
                                    let testRunTime
                                    if (timeInSeconds === "Till complete") testRunTime = -1
                                    else testRunTime = timeInSeconds

                                    const testRunTimeOption = getLabel(testRunTimeOptions, timeInSeconds)

                                    setTestRun(prev => ({
                                        ...prev,
                                        testRunTime: testRunTime,
                                        testRunTimeLabel: testRunTimeOption.label
                                    }))
                                }}
                            />
                        </div>
                    </HorizontalStack>
                    <HorizontalStack align='space-between'>
                        <Text>Max Concurrent Requests</Text>
                        <div style={{ width: '400px'}}>
                            <Dropdown
                                menuItems={maxConcurrentRequestsOptions}
                                initial={"Default"}
                                selected={(requests) => {
                                    let maxConcurrentRequests
                                    if (requests === "Default") maxConcurrentRequests = -1
                                    else maxConcurrentRequests = requests

                                    const maxConcurrentRequestsOption = getLabel(maxConcurrentRequestsOptions, requests)

                                    setTestRun(prev => ({
                                        ...prev,
                                        maxConcurrentRequests: maxConcurrentRequests,
                                        maxConcurrentRequestsLabel: maxConcurrentRequestsOption.label
                                    }))
                                }}
                            />
                        </div>
                    </HorizontalStack>
                    <HorizontalStack align='space-between'>
                        <Text>Send alerts to Slack</Text>
                        <div style={{ width: '400px'}}>
                            {
                                slackIntegrated ?
                                <Dropdown
                                    disabled={!slackIntegrated}
                                    menuItems={slackAlertsMenuItems}
                                    initial={slackAlertsVal}
                                    selected={setSlackAlertsVal}
                                /> : generateLabelForSlackIntegration()
                            }
                        </div>
                    </HorizontalStack>

                    <HorizontalStack align='space-between'>
                        <Checkbox
                            label="Use different target for testing"
                            checked={testRun.hasOverriddenTestAppUrl}
                            onChange={() => setTestRun(prev => ({ ...prev, hasOverriddenTestAppUrl: !prev.hasOverriddenTestAppUrl }))}
                        />
                        {testRun.hasOverriddenTestAppUrl &&
                            <div style={{ width: '400px'}}>
                                <TextField
                                    type='url'
                                    placeholder="URL"
                                    value={testRun.overriddenTestAppUrl}
                                    onChange={(overriddenTestAppUrl) => setTestRun(prev => ({ ...prev, overriddenTestAppUrl: overriddenTestAppUrl }))}
                                />
                            </div>
                        }
                    </HorizontalStack>
                </VerticalStack>
            </Box>
            <Box paddingBlockEnd={"5"}>
                <Divider />
            </Box>
        </>,
    ]

    const runActiveTest = async () => {
        const { startTimestamp, recurringDaily, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert } = testRun
        await observeApi.scheduleTestForCollection(collectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert)
        
        const forwardLink = (
            <HorizontalStack gap={1}>
                <Text> Test run created successfully. Click </Text>
                <Link url="/dashboard/testing">here</Link>
                <Text> to view results.</Text>
            </HorizontalStack>
        )
        func.setToast(true, false, <div data-testid="test_run_created_message">{forwardLink}</div>)
    }

    const activeTestNextFooterActions = currentStep < 3 ? {
        content: currentStep < 2 ? 'Next step' : 'Run Test',
        onAction: ( 
            (testRun?.testName?.length === 0 && currentStep === 0) || 
            (currentStep === 0 && (collectionId === null || collectionId <= 0)) || 
            (currentStep === 1 && selectedTests.length === 0) 
        ) ? () => {} : 
        (currentStep < 2 ? () => { setCurrentStep((prev) => prev + 1) } : runActiveTest)        
    } : null

    const activeTestPreviousFooterActions = currentStep > 0 && currentStep < 3 ? [{
        content: 'Back',
        onAction: () => {setCurrentStep((prev) => prev-1)}
    }] : null

    const components = (
        <LegacyCard primaryFooterAction={activeTestNextFooterActions} secondaryFooterActions={activeTestPreviousFooterActions} >
            <LegacyCard.Section>
                <ActiveTestingSteps totalSteps={3} currentStep={currentStep} stepsTextArr={['Collection', "Test suites", 'Authentication'] } />
            </LegacyCard.Section>

            { activeTestingStepsComp[currentStep] }

        </LegacyCard>
    )

    return (
        <PageWithMultipleCards
            fullWidth={false}
            title={
              <TitleWithInfo
                titleText={"New test"}
              />
            }
            backUrl={"/dashboard/testing/active-testing"}
            secondaryActions={<Button secondaryActions>Documentation</Button>}
            components={[components]}
        />
    )
}

export default NewActiveTest