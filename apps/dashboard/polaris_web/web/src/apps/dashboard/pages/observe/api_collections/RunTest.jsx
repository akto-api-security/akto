import { Box, Button, DataTable, Divider, Modal, Text, TextField, Icon, Checkbox, ButtonGroup, Badge, Banner,HorizontalGrid } from "@shopify/polaris";
import { TickMinor, CancelMajor } from "@shopify/polaris-icons"
import { useEffect, useRef, useState } from "react";
import { default as observeApi } from "../api";
import { default as testingApi } from "../../testing/api";
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import Dropdown from "../../../components/layouts/Dropdown";
import func from "@/util/func"
import { useNavigate } from "react-router-dom"
import Store from "../../../store";

function RunTest({ endpoints, filtered, apiCollectionId, disabled }) {

    const initialState = {
        categories: [],
        tests: {},
        selectedCategory: "",
        recurringDaily: false,
        overriddenTestAppUrl: "",
        hasOverriddenTestAppUrl: false,
        startTimestamp: func.timeNow(),
        hourlyLabel: "Now",
        testRunTime: -1,
        testRunTimeLabel: "Till complete",
        maxConcurrentRequests: "Default",
        testName: "",
        authMechanismPresent: false
    }

    const navigate = useNavigate()

    const [testRun, setTestRun] = useState({
        ...initialState
    })
    const collectionsMap = Store(state => state.collectionsMap)
    const [loading, setLoading] = useState(true)
    const [active, setActive] = useState(false);

    const runTestRef = useRef(null);

    function nameSuffixes(tests) {
        return Object.entries(tests)
            .filter(category => {
                let selectedCount = 0
                category[1].forEach(test => {
                    if (test.selected) selectedCount += 1
                })

                return selectedCount > 0
            })
            .map(category => category[0])
    }

    const convertToLowerCaseWithUnderscores = (inputString) => {
        if(!inputString)
            return ""
        return inputString?.toLowerCase()?.replace(/\s+/g, '_')
    }
    const apiCollectionName = collectionsMap[apiCollectionId]

    async function fetchData() {
        setLoading(true)

        const allSubCategoriesResponse = await testingApi.fetchAllSubCategories()
        const businessLogicSubcategories = allSubCategoriesResponse.subCategories
        const categories = allSubCategoriesResponse.categories
        const { selectedCategory, mapCategoryToSubcategory } = populateMapCategoryToSubcategory(businessLogicSubcategories)

        // Store all tests
        const processMapCategoryToSubcategory = {}
        Object.keys(mapCategoryToSubcategory).map(category => {
            processMapCategoryToSubcategory[category] = [...mapCategoryToSubcategory[category]["all"]]
        })

        // Set if a test is selected or not
        Object.keys(processMapCategoryToSubcategory).map(category => {
            const selectedTests = []

            mapCategoryToSubcategory[category]["selected"].map(test => selectedTests.push(test.value))
            processMapCategoryToSubcategory[category].forEach((test, index, arr) => {
                arr[index]["selected"] = selectedTests.includes(test.value)
            })
        })

        const testName = convertToLowerCaseWithUnderscores(apiCollectionName) + "_" + nameSuffixes(processMapCategoryToSubcategory).join("_")

        //Auth Mechanism
        let authMechanismPresent = false
        const authMechanismDataResponse = await testingApi.fetchAuthMechanismData()
        if (authMechanismDataResponse.authMechanism)
            authMechanismPresent = true

        setTestRun(prev => ({
            ...prev,
            categories: categories,
            tests: processMapCategoryToSubcategory,
            selectedCategory: Object.keys(processMapCategoryToSubcategory)[0],
            testName: testName,
            authMechanismPresent: authMechanismPresent
        }))

        setLoading(false)
    }

    useEffect(() => {
        fetchData()
    }, [apiCollectionName])

    const toggleRunTest = () => setActive(prev => !prev)

    function populateMapCategoryToSubcategory(businessLogicSubcategories) {
        let ret = {}
        businessLogicSubcategories.forEach(x => {
            if (!ret[x.superCategory.name]) {
                ret[x.superCategory.name] = { selected: [], all: [] }
            }

            let obj = {
                label: x.testName,
                value: x.name,
                icon: "$aktoWhite"
            }
            ret[x.superCategory.name].all.push(obj)
            ret[x.superCategory.name].selected.push(obj)
        })

        //store this
        return {
            selectedCategory: Object.keys(ret)[0],
            mapCategoryToSubcategory: ret
        }
    }

    const activator = (
        <div ref={runTestRef}>
            <Button onClick={toggleRunTest} primary disabled={disabled} >Run Test</Button>
        </div>
    );

    let categoryRows = [], testRows = []

    if (!loading) {
        categoryRows = testRun.categories.map(category => {
            const tests = testRun.tests[category.name]

            if (tests) {
                let selected = 0
                const total = tests.length

                tests.forEach(test => {
                    if (test.selected)
                        selected += 1
                })

                return ([(
                    <div
                        style={{ display: "grid", gridTemplateColumns: "auto max-content", alignItems: "center" }}
                        onClick={() => setTestRun(prev => ({ ...prev, selectedCategory: category.name }))}>
                        <div>
                            <Text variany="headingMd" fontWeight="bold" color={category.name === testRun.selectedCategory ? "success" : ""}>{category.displayName}</Text>
                            <Text>{selected} out of {total} selected</Text>
                        </div>
                        {selected > 0 && <Icon source={TickMinor} color="base" />}
                    </div>
                )])
            } else {
                return []
            }

        })

        const handleTestsSelected = (test) => {
            let localCopy = JSON.parse(JSON.stringify(testRun.tests))
            localCopy[testRun.selectedCategory] = localCopy[testRun.selectedCategory].map(curTest =>
                curTest.label === test.label ?
                {
                    ...curTest,
                    selected: !curTest.selected
                } : curTest
            ) 
            const testName = convertToLowerCaseWithUnderscores(apiCollectionName) + "_" + nameSuffixes(localCopy).join("_")
            setTestRun(prev => ({
                ...prev,
                tests: {
                    ...prev.tests,
                    [testRun.selectedCategory]: prev.tests[testRun.selectedCategory].map(curTest =>
                        curTest.label === test.label ?
                            {
                                ...curTest,
                                selected: !curTest.selected
                            } : curTest)
                },
                testName:testName
            }))
        }

        testRows = testRun.tests[testRun.selectedCategory].map(test => {
            const isCustom = test.label.includes("Custom") || test.value.includes("CUSTOM")
            const label = (
                <span style={{display: 'flex', gap: '4px', alignItems: 'flex-start'}}>
                    <Text variant="bodyMd">{test.label}</Text>
                    {isCustom ? <Box paddingBlockStart={"05"}><Badge status="warning" size="small">Custom</Badge></Box> : null}
                </span>
            )
            return ([(
                <Checkbox
                    label={label}
                    checked={test.selected}
                    onChange={() => handleTestsSelected(test)}
                />
            )])
        })
    }

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

    const testRunTimeOptions = [{ label: "Till complete", value: "Till complete" }, ...runTimeMinutes, ...runTimeHours]

    const maxRequests = hours.reduce((abc, x) => {
        if (x < 11) {
            let label = x * 10
            abc.push({ label, value: `${x * 10}` })
        }
        return abc
    }, [])

    const maxConcurrentRequestsOptions = [{ label: "Default", value: "-1" }, ...maxRequests]

    function scheduleString() {
        if (testRun.hourlyLabel === "Now") {
            if (testRun.recurringDaily) {
                return "Run daily at this time"
            } else {
                return "Run once now"
            }
        } else {
            if (testRun.recurringDaily) {
                return "Run daily at " + testRun.hourlyLabel
            } else {
                return "Run today at " + testRun.hourlyLabel
            }
        }
    }

    function handleRemoveAll() {
        setTestRun(prev => {
            const tests = { ...testRun.tests }
            Object.keys(tests).forEach(category => {
                tests[category] = tests[category].map(test => ({ ...test, selected: false }))
            })

            return { ...prev, tests: tests, testName: convertToLowerCaseWithUnderscores(apiCollectionName) }
        })
        func.setToast(true, false, "All tests unselected")
    }

    function checkRemoveAll(){
        const tests = {...testRun.tests}
        let totalTests = 0
        Object.keys(tests).forEach(category =>{
            tests[category].map((test) => {
                if(test.selected){
                    totalTests++
                }
            })
        })
        return totalTests === 0;
    }

    async function handleRun() {
        const { startTimestamp, recurringDaily, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl } = testRun
        const collectionId = parseInt(apiCollectionId)

        const tests = testRun.tests
        const selectedTests = []

        Object.keys(tests).forEach(category => {
            tests[category].forEach(test => {
                if (test.selected) selectedTests.push(test.value)
            })
        })

        const apiInfoKeyList = endpoints.map(endpoint => ({
            apiCollectionId: endpoint.apiCollectionId,
            method: endpoint.method,
            url: endpoint.endpoint
        }))

        if (filtered) {
            await observeApi.scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, "TESTING_UI")
        } else {
            await observeApi.scheduleTestForCollection(collectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl)
        }

        func.setToast(true, false, "Test run created successfully")
    }

    function getLabel(objList, value) {
        const obj = objList.find(obj => obj.value === value)
        return obj
    }

    return (
        <div>
            {activator}
            <Modal
                activator={runTestRef}
                open={active}
                onClose={toggleRunTest}
                title="Configure test"
                primaryAction={{
                    content: scheduleString(),
                    onAction: handleRun,
                    disabled: !testRun.authMechanismPresent
                }}
                large
            >
                {loading ? <SpinnerCentered /> :
                    <Modal.Section>
                        {!testRun.authMechanismPresent &&
                            <div>
                                <Banner
                                    title="Authentication mechanism not configured"
                                    action={
                                        {
                                            content: 'Configure authentication mechanism',
                                            onAction: () => navigate("/dashboard/testing/user-config")
                                        }}
                                    status="critical"
                                >

                                    <Text variant="bodyMd">
                                        Running specialized tests like Broken Object Level Authorization,
                                        Broken User Authentication etc, require an additional attacker
                                        authorization token. Hence before triggering Akto tests on your apis,
                                        you may need to specify an authorization token which can be treated as
                                        attacker token during test run. Attacker Token can be specified
                                        manually, as well as in automated manner. We provide multiple ways to
                                        automate Attacker token generation.
                                    </Text>
                                </Banner>
                                <br />
                            </div>
                        }


                        <div style={{ display: "grid", gridTemplateColumns: "max-content auto max-content", alignItems: "center", gap: "10px" }}>
                            <Text variant="headingMd">Name:</Text>
                            <div style={{ maxWidth: "75%" }}>
                                <TextField
                                    placeholder="Enter test name"
                                    value={testRun.testName}
                                    onChange={(testName) => setTestRun(prev => ({ ...prev, testName: testName }))}
                                />
                            </div>

                            <Button icon={CancelMajor} destructive onClick={handleRemoveAll} disabled={checkRemoveAll()}>Remove All</Button>
                        </div>

                        <br />
                        <div style={{ display: "grid", gridTemplateColumns: "50% 50%", border: "1px solid #C9CCCF" }}>
                            <div style={{ borderRight: "1px solid #C9CCCF" }}>
                                <div style={{ padding: "15px", alignItems: "center" }}>
                                    <Text variant="headingMd">Test Categories</Text>
                                </div>
                                <Divider />
                                <div style={{ maxHeight: "35vh", overflowY: "auto" }}>

                                    <DataTable
                                        columnContentTypes={[
                                            'text',
                                        ]}
                                        headings={[]}
                                        rows={categoryRows}
                                        increasedTableDensity
                                    />
                                </div>
                            </div>
                            <div>
                                <div style={{ padding: "15px", alignItems: "center" }}>
                                    <Text variant="headingMd">Tests</Text>
                                </div>
                                <Divider />
                                <div style={{ maxHeight: "35vh", overflowY: "auto", paddingTop: "5px" }}>
                                    <DataTable
                                        columnContentTypes={[
                                            'text',
                                        ]}
                                        headings={[]}
                                        rows={testRows}
                                        hoverable={false}
                                        increasedTableDensity
                                    />
                                </div>
                            </div>
                        </div>

                        <br />

                        <HorizontalGrid columns="2">
                            <Box>
                                <ButtonGroup>
                                    <Text>Select time:</Text>
                                    <Dropdown
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
                                        }} />
                                </ButtonGroup>
                                <br />

                                <ButtonGroup>
                                    <Checkbox
                                        label="Run daily"
                                        checked={testRun.recurringDaily}
                                        onChange={() => setTestRun(prev => ({ ...prev, recurringDaily: !prev.recurringDaily }))}
                                    />
                                    <Checkbox
                                        label="Use different target for testing"
                                        checked={testRun.hasOverriddenTestAppUrl}
                                        onChange={() => setTestRun(prev => ({ ...prev, hasOverriddenTestAppUrl: !prev.hasOverriddenTestAppUrl }))}
                                    />
                                    {testRun.hasOverriddenTestAppUrl &&
                                        <TextField
                                            placeholder="Override test app host"
                                            value={testRun.overriddenTestAppUrl}
                                            onChange={(overriddenTestAppUrl) => setTestRun(prev => ({ ...prev, overriddenTestAppUrl: overriddenTestAppUrl }))}
                                        />
                                    }
                                </ButtonGroup>
                                </Box>
                            <Box>
                                <ButtonGroup>
                                    <Text>Test run time:</Text>
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
                                        }} />
                                </ButtonGroup>

                                <br />
                                <ButtonGroup>
                                    <Text>Max concurrent requests:</Text>
                                    <Dropdown
                                        menuItems={maxConcurrentRequestsOptions}
                                        initial={testRun.maxConcurrentRequests}
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
                                        }} />
                                </ButtonGroup>
                            </Box>
                        </HorizontalGrid>
                    </Modal.Section>
                }
            </Modal>
        </div>
    )
}

export default RunTest