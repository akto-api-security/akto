import { Box, Button, DataTable, Divider, Modal, Text, TextField, Icon, Checkbox, ButtonGroup, Badge, Banner,HorizontalGrid, HorizontalStack, Link, VerticalStack } from "@shopify/polaris";
import { TickMinor, CancelMajor } from "@shopify/polaris-icons"
import { useEffect, useRef, useState } from "react";
import { default as observeApi } from "../api";
import { default as testingApi } from "../../testing/api";
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import Dropdown from "../../../components/layouts/Dropdown";
import func from "@/util/func"
import { useNavigate } from "react-router-dom"
import PersistStore from "../../../../main/PersistStore";

function RunTest({ endpoints, filtered, apiCollectionId, disabled, runTestFromOutside }) {

    const initialState = {
        categories: [],
        tests: {},
        selectedCategory: "",
        recurringDaily: false,
        continuousTesting: false,
        overriddenTestAppUrl: "",
        hasOverriddenTestAppUrl: false,
        startTimestamp: func.timeNow(),
        hourlyLabel: "Now",
        testRunTime: -1,
        testRunTimeLabel: "Till complete",
        runTypeLabel: "Now",
        maxConcurrentRequests: -1,
        testName: "",
        authMechanismPresent: false,
        testRoleLabel: "No test role selected",
        testRoleId: "",
    }

    const navigate = useNavigate()

    const [testRun, setTestRun] = useState({
        ...initialState
    })
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [loading, setLoading] = useState(true)
    const [testRolesArr, setTestRolesArr] = useState([])
    const [active, setActive] = useState(runTestFromOutside || false);

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

        const allSubCategoriesResponse = await testingApi.fetchAllSubCategories(true, "runTests")
        const testRolesResponse = await testingApi.fetchTestRoles()
        var testRoles = testRolesResponse.testRoles.map(testRole => {
            return {
                "label": testRole.name,
                "value": testRole.hexId
            }
        })
        testRoles.unshift({"label": "No test role selected", "value": ""})
        setTestRolesArr(testRoles)
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
        if(runTestFromOutside === true){
            setActive(true)
        }
    }, [apiCollectionName,runTestFromOutside])

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
                author: x.author
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
            <Button onClick={toggleRunTest} primary disabled={disabled} ><div data-testid="run_test_button">Run test</div></Button>
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
            const isCustom = test?.author !== "AKTO"
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
                    ariaDescribedBy={test.label}
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

    const runTypeOptions = [{ label: "Daily", value: "Daily" }, { label: "Continuously", value: "Continuously" }, { label: "Now", value: "Now" }]

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
                return <div data-testid="schedule_run_button">Run daily at this time</div>
            } else if (testRun.continuousTesting) {
                return <div data-testid="schedule_run_button">Run continuous testing</div>
            } else {
                return <div data-testid="schedule_run_button">Run once now</div>
            }
        } else {
            if (testRun.recurringDaily) {
                return <div data-testid="schedule_run_button">Run daily at {testRun.hourlyLabel}</div>

            } else {
                return <div data-testid="schedule_run_button">Run today at {testRun.hourlyLabel}</div>
                
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
        const { startTimestamp, recurringDaily, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting } = testRun
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
            await observeApi.scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, "TESTING_UI", testRoleId, continuousTesting)
        } else {
            await observeApi.scheduleTestForCollection(collectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting)
        }

        setActive(false)
        const forwardLink = (
            <HorizontalStack gap={1}>
                <Text> Test run created successfully. Click </Text>
                <Link url="/dashboard/testing">here</Link>
                <Text> to view results.</Text>
            </HorizontalStack>
        )

        func.setToast(true, false, <div data-testid="test_run_created_message">{forwardLink}</div>)

    }

    function getLabel(objList, value) {
        const obj = objList.find(obj => obj.value === value)
        return obj
    }

    function getCurrentStatus(){
        if(!testRun || testRun?.tests === undefined || testRun?.selectedCategory === undefined || testRun.tests[testRun.selectedCategory] === undefined)
            return false;

        let res = true;
        const tests = testRun.tests[testRun.selectedCategory];
        for (let i = 0; i < tests.length; i++) {
            if (tests[i].selected === false) {
                res = false;
                break; 
            }
        }
        return res;
    }

    const allTestsSelectedOfCategory = getCurrentStatus()

    function toggleTestsSelection(val) {
        let copyTestRun = testRun
        copyTestRun.tests[testRun.selectedCategory].forEach((test) => {
            test.selected = val
        })
        setTestRun(prev => {
            return { ...prev, tests: copyTestRun.tests, testName: convertToLowerCaseWithUnderscores(apiCollectionName) + "_" + nameSuffixes(copyTestRun.tests).join("_") }
        })
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

                            <Button icon={CancelMajor} destructive onClick={handleRemoveAll} disabled={checkRemoveAll()}><div data-testid="remove_all_tests">Remove All</div></Button>
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
                                    <HorizontalStack gap={"2"}>
                                        <Checkbox
                                            checked={allTestsSelectedOfCategory}
                                            onChange={(val) => toggleTestsSelection(val)}
                                        />
                                        <Text variant="headingMd">Tests</Text>
                                    </HorizontalStack>
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

                        <VerticalStack gap={"4"}>
                            <HorizontalGrid gap={"4"} columns={"3"}>
                                    <Dropdown
                                        label="Run Type"
                                        menuItems={runTypeOptions}
                                        initial={testRun.runTypeLabel}
                                        selected={(runType) => {
                                            let recurringDaily = false
                                            let continuousTesting = false

                                            if(runType === 'Continuously'){
                                                continuousTesting = true;
                                            }else if(runType === 'Daily'){
                                                recurringDaily = true;
                                            }
                                           setTestRun(prev => ({
                                                   ...prev,
                                                   recurringDaily,
                                                   continuousTesting,
                                                   runTypeLabel: runType.label
                                               }))
                                        }} />

                                <Dropdown
                                    label="Select Time:"
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
                                    }} />
                                    <Dropdown
                                        label="Test run time:"
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
                            </HorizontalGrid>
                            <HorizontalGrid gap={"4"} columns={"2"}>
                                <div style={{marginTop: '-10px'}}>
                                    <Text>Select Test Role</Text>
                                    <Dropdown
                                        menuItems={testRolesArr}
                                        initial={"No test role selected"}
                                        selected={(requests) => {
                                            let testRole
                                            if (!(requests === "No test role selected")){testRole = requests}
                                            const testRoleOption = getLabel(testRolesArr, requests)

                                            setTestRun(prev => ({
                                                ...prev,
                                                testRoleId: testRole,
                                                testRoleLabel: testRoleOption.label
                                            }))
                                        }} />
                                </div>
                                        
                                <div style={{marginTop: '-10px'}}>
                                <Text>Max Concurrent Requests</Text>
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
                                    }} />
                            </div>

                            </HorizontalGrid>

                            <Checkbox
                                label="Use different target for testing"
                                checked={testRun.hasOverriddenTestAppUrl}
                                onChange={() => setTestRun(prev => ({ ...prev, hasOverriddenTestAppUrl: !prev.hasOverriddenTestAppUrl }))}
                            />
                            {testRun.hasOverriddenTestAppUrl &&
                                <div style={{ width: '400px'}}> 
                                    <TextField
                                        placeholder="Override test app host"
                                        value={testRun.overriddenTestAppUrl}
                                        onChange={(overriddenTestAppUrl) => setTestRun(prev => ({ ...prev, overriddenTestAppUrl: overriddenTestAppUrl }))}
                                    />
                                </div>
                            }
                        </VerticalStack>

                    </Modal.Section>
                }
            </Modal>
        </div>
    )
}

export default RunTest