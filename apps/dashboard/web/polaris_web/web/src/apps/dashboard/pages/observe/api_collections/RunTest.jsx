import { Box, Button, DataTable, Divider, Modal, Text, TextField, Icon, Checkbox, Badge, Banner, HorizontalStack, Link, VerticalStack, Tooltip, Popover, OptionList, ButtonGroup } from "@shopify/polaris";
import { TickMinor, CancelMajor, SearchMinor, NoteMinor, AppsFilledMajor, MagicMajor } from "@shopify/polaris-icons";
import api, { default as observeApi } from "../api";
import { useEffect, useReducer, useRef, useState } from "react";
import { default as testingApi } from "../../testing/api";
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import func from "@/util/func"
import { useNavigate } from "react-router-dom"
import PersistStore from "../../../../main/PersistStore";
import transform from "../../testing/transform";
import LocalStore from "../../../../main/LocalStorageStore";
import AdvancedSettingsComponent from "./component/AdvancedSettingsComponent";
import { mapLabel, getDashboardCategory } from "../../../../main/labelHelper";

import { produce } from "immer"
import RunTestSuites from "./RunTestSuites";
import RunTestConfiguration from "./RunTestConfiguration";
import {createTestName,convertToLowerCaseWithUnderscores} from "./Utils"
import settingsApi from "../../settings/api";

const initialAutoTicketingDetails = {
    shouldCreateTickets: false,
    projectId: "",
    severities: [],
    issueType: "",
}

function RunTest({ endpoints, filtered, apiCollectionId, disabled, runTestFromOutside, closeRunTest, selectedResourcesForPrimaryAction, useLocalSubCategoryData, preActivator, testIdConfig, activeFromTesting, setActiveFromTesting, showEditableSettings, setShowEditableSettings, parentAdvanceSettingsConfig, testRunType, shouldDisable }) {

    const initialState = {
        categories: [],
        tests: {},
        selectedCategory: "BOLA",
        recurringDaily: false,
        recurringWeekly: false,
        recurringMonthly: false,
        continuousTesting: false,
        overriddenTestAppUrl: "",
        hasOverriddenTestAppUrl: false,
        startTimestamp: func.timeNow(),
        hourlyLabel: "Now",
        testRunTime: -1,
        testRunTimeLabel: "30 minutes",
        runTypeLabel: "Once",
        maxConcurrentRequests: -1,
        testName: "",
        authMechanismPresent: false,
        testRoleLabel: "No test role selected",
        testRoleId: "",
        sendSlackAlert: false,
        sendMsTeamsAlert: false,
        cleanUpTestingResources: false,
        autoTicketingDetails: initialAutoTicketingDetails,
        miniTestingServiceName: "",
        slackChannel: ""
    }
    const navigate = useNavigate()

    const [testRun, setTestRun] = useState({
        ...initialState
    })
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [loading, setLoading] = useState(true)
    const [testRolesArr, setTestRolesArr] = useState([])
    const [active, setActive] = useState(runTestFromOutside || false);
    const [parentActivator, setParentActivator] = useState(false)

    const runTestRef = useRef(null);
    const [searchValue, setSearchValue] = useState('')
    const [showSearch, setShowSearch] = useState(false)
    const [showFiltersOption, setShowFiltersOption] = useState(false);
    const sectionsForFilters = [
        {
            title: 'Author',
            filterKey: 'author',
            options: [
                { value: 'akto', label: 'Akto default' },
                { value: 'custom', label: 'Custom tests' }
            ]
        }
    ]

    const initialArr = ['akto', 'custom']

    const [optionsSelected, setOptionsSelected] = useState(initialArr)
    const [slackIntegrated, setSlackIntegrated] = useState(false)
    const [teamsTestingWebhookIntegrated, setTeamsTestingWebhookIntegrated] = useState(false)

    const [miniTestingServiceNames, setMiniTestingServiceNames] = useState([])
    const [slackChannels, setSlackChannels] = useState([])
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => func.conditionsReducer(draft, action)), []);

    const localCategoryMap = LocalStore.getState().categoryMap
    const localSubCategoryMap = LocalStore.getState().subCategoryMap
    const [testMode, setTestMode] = useState(true)
    const [shouldRuntestConfig, setShouldRuntestConfig] = useState(false)

    const [openConfigurations, openConfigurationsToggle] = useState(false);

    const [testSuiteIds, setTestSuiteIds] = useState([])

    const [testNameSuiteModal, setTestNameSuiteModal] = useState("")
    const [jiraProjectMap, setJiraProjectMap] = useState(null);
    const isAgenticCategory = getDashboardCategory() !== 'API Security'
    const [agenticMode, setAgenticMode] = useState(false)

    useEffect(() => {
        if (preActivator) {
            setParentActivator(true);
        }
    }, [testMode])

    const apiCollectionName = collectionsMap[apiCollectionId]

    async function fetchData() {
        setLoading(true)

        Promise.all([
            observeApi.fetchSlackWebhooks(),
            observeApi.checkWebhook("MICROSOFT_TEAMS", "TESTING_RUN_RESULTS"),
            settingsApi.fetchJiraIntegration()]).then(([slackResp, teamsResp, jiraResp]) => {
                const apiTokenList = slackResp.apiTokenList
                setSlackIntegrated(apiTokenList && apiTokenList.length > 0)
                setSlackChannels(apiTokenList.map(token => {
                    let slackName = token.name;
                        // Check if name is missing or is a URL
                        if (!slackName ) {
                            slackName = 'Slack Webhook';
                        } else if ( /^https?:\/\//i.test(slackName)){
                            slackName = token.name.replace(/^https?:\/\//i, '').replace(/\/$/, '');
                        }
                        return {
                            label: slackName,
                            value: token.id
                        }
                }))

                const webhookPresent = teamsResp.webhookPresent
                if (webhookPresent) {
                    setTeamsTestingWebhookIntegrated(true)
                }

                setJiraProjectMap(jiraResp?.projectIdsMap || null);
            })

        let metaDataObj = {
            categories: [],
            subCategories: [],
            testSourceConfigs: []
        }

        if ((localCategoryMap && Object.keys(localCategoryMap).length > 0) && (localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)) {
            metaDataObj = {
                categories: Object.values(localCategoryMap),
                subCategories: Object.values(localSubCategoryMap),
                testSourceConfigs: []
            }

        } else {
            metaDataObj = await transform.getAllSubcategoriesData(true, "runTests")
        }
        let categories = metaDataObj.categories
        categories = func.sortByCategoryPriority(categories, 'name')
        const categoriesNames = categories.map(category => category.name)
        let businessLogicSubcategories = metaDataObj.subCategories.filter((subCategory) => {return !subCategory.inactive})
        const testRolesResponse = await testingApi.fetchTestRoles()
        var testRoles = testRolesResponse.testRoles.map(testRole => {
            return {
                "label": testRole.name,
                "value": testRole.hexId
            }
        })
        testRoles.unshift({ "label": "No test role selected", "value": "" })
        setTestRolesArr(testRoles)
        const { selectedCategory, mapCategoryToSubcategory } = populateMapCategoryToSubcategory(businessLogicSubcategories)
        // Store all tests
        const processMapCategoryToSubcategory = {}
        if (Object.keys(businessLogicSubcategories).length > 0) {
            Object.keys(mapCategoryToSubcategory).map(category => {
                processMapCategoryToSubcategory[category] = [...mapCategoryToSubcategory[category]["all"]]
            })
        }

        Object.keys(processMapCategoryToSubcategory).map(category => {
            if(categoriesNames.includes(category)){
                const selectedTests = []

                mapCategoryToSubcategory[category]["selected"].map(test => selectedTests.push(test.value))
                processMapCategoryToSubcategory[category].forEach((test, index, arr) => {
                    arr[index]["selected"] = selectedTests.includes(test.value)
                })
            }else{
                delete processMapCategoryToSubcategory[category];
            }
            
        })
        const testName = createTestName(apiCollectionName, processMapCategoryToSubcategory);
        // determine selectedCategory based on the same priority ordering
        const sortedCategoryKeys = func.sortByCategoryPriority(Object.keys(processMapCategoryToSubcategory))
        //Auth Mechanism
        let authMechanismPresent = false
        const authMechanismDataResponse = await testingApi.fetchAuthMechanismData()
        if (authMechanismDataResponse.authMechanism)
            authMechanismPresent = true
            testingApi.fetchMiniTestingServiceNames().then(({miniTestingServiceNames}) => {
            const miniTestingServiceNamesOptions = (miniTestingServiceNames || []).map(name => {
                return {
                    label: name,
                    value: name
                }
            })
            setMiniTestingServiceNames(miniTestingServiceNamesOptions)
        })

        setTestRun(prev => {
            const state = {
                ...prev,
                categories: categories,
                tests: processMapCategoryToSubcategory,
                selectedCategory: sortedCategoryKeys.length > 0 ? sortedCategoryKeys[0] : "",
                testName: testName,
                authMechanismPresent: authMechanismPresent
            };
            return state;
        });
        setLoading(false)
        setShouldRuntestConfig(true);
        setTestNameSuiteModal(convertToLowerCaseWithUnderscores(apiCollectionName))
    }

    useEffect(() => {
        fetchData()
        if (runTestFromOutside === true) {
            setActive(true)
        }
    }, [apiCollectionName, runTestFromOutside])

    useEffect(() => {
        if (shouldRuntestConfig === false) return;
        if (testIdConfig?.testingRunConfig?.testSubCategoryList?.length >= 0) {
            const testSubCategoryList = [...testIdConfig.testingRunConfig.testSubCategoryList];

            const updatedTests = { ...testRun.tests };
            // console.log("updatedTests", updatedTests);
            // Reset all test selections
            Object.keys(updatedTests).forEach(category => {
                updatedTests[category] = updatedTests[category].map(test => ({ ...test, selected: false }));
            });

            const testSubCategorySet = new Set(testSubCategoryList);

            Object.keys(updatedTests).forEach(category => {
                updatedTests[category] = updatedTests[category].map(test => ({
                    ...test,
                    selected: testSubCategorySet.has(test.value),
                }));
            });

                handleAddSettings(parentAdvanceSettingsConfig);
            
                const hourOfTest = func.getHourFromEpoch(testIdConfig.scheduleTimestamp)
                const hourLabel = func.getFormattedHoursUsingLabel(hourOfTest, hourlyTimes, nowLabel)

                setTestRun(prev => ({
                    ...testRun,
                    tests: updatedTests,
                    overriddenTestAppUrl: testIdConfig.testingRunConfig.overriddenTestAppUrl,
                    hasOverriddenTestAppUrl: testIdConfig?.testingRunConfig?.overriddenTestAppUrl?.length > 0,
                    maxConcurrentRequests: testIdConfig.maxConcurrentRequests,
                    testRunTime: testIdConfig.testRunTime,
                    testRoleId: testIdConfig.testingRunConfig.testRoleId,
                    testRunTimeLabel: (testIdConfig.testRunTime === -1) ? "30 minutes" : getLabel(testRunTimeOptions, testIdConfig.testRunTime.toString())?.label,
                    testRoleLabel: getLabel(testRolesArr, testIdConfig?.testingRunConfig?.testRoleId)?.label,
                    runTypeLabel: func.getRunTypeLabel(testRunType, testIdConfig?.periodInSeconds),
                    testName: testIdConfig.name,
                    sendSlackAlert: testIdConfig?.sendSlackAlert,
                    sendMsTeamsAlert: testIdConfig?.sendMsTeamsAlert,
                    recurringDaily: func.getRecurringContext(testIdConfig?.periodInSeconds) === "Daily",
                    recurringMonthly: func.getRecurringContext(testIdConfig?.periodInSeconds) === "Monthly",
                    recurringWeekly: func.getRecurringContext(testIdConfig?.periodInSeconds) === "Weekly",
                    continuousTesting: func.getRecurringContext(testIdConfig?.periodInSeconds) === "Continuously",
                    autoTicketingDetails: testIdConfig?.testingRunConfig?.autoTicketingDetails || initialAutoTicketingDetails,
                    hourlyLabel: hourLabel.label,
                    scheduleTimestamp: testIdConfig?.scheduleTimestamp,
                    startTimestamp: testIdConfig?.scheduleTimestamp,
                    runTypeParentLabel: testRunType,
                    miniTestingServiceName: testIdConfig?.miniTestingServiceName || "",
                    slackChannel: testIdConfig?.selectedSlackChannelId || 0,
                }));
                setTestSuiteIds(testIdConfig?.testingRunConfig?.testSuiteIds || [])
                setTestNameSuiteModal(testIdConfig?.name||"")
        }
        setShouldRuntestConfig(false);
    }, [shouldRuntestConfig])

    const toggleRunTest = () => {
        if (activeFromTesting) {
            setActiveFromTesting(false);
            setShouldRuntestConfig(true);
            return;
        }
        if (!activeFromTesting) {
            setTestRun(prev => {
                const tests = { ...testRun.tests }
                Object.keys(tests).forEach(category => {
                    tests[category] = tests[category].map(test => ({ ...test, selected: true }))
                })
                const testName = createTestName(apiCollectionName, tests)
                return { ...prev, tests: tests, testName: testName }
            })
        }
        setActive(prev => !prev)
        if (active) {
            if (closeRunTest !== undefined) closeRunTest()
        }
    }

    function populateMapCategoryToSubcategory(businessLogicSubcategories) {
        let ret = {}
        businessLogicSubcategories.forEach(x => {
            if (!ret[x.superCategory.name]) {
                ret[x.superCategory.name] = { selected: [], all: [] }
            }

            let obj = {
                label: x.testName,
                value: x.name,
                author: x.author,
                nature: x?.attributes?.nature?._name || "",
                severity: x?.superCategory?.severity?._name || "",
                duration: x?.attributes?.duration?._name || ""
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
            <Button onClick={toggleRunTest} primary disabled={disabled || testRun.selectedCategory.length === 0} ><div data-testid="run_test_button">{mapLabel('Run test', getDashboardCategory())}</div></Button>
        </div>
    );

    const filterFunc = (test, selectedVal = []) => {
        const useFilterArr = selectedVal.length > 0 ? selectedVal : optionsSelected
        let ans = false;
        sectionsForFilters.forEach((filter) => {
            const filterKey = filter.filterKey;
            if (filterKey === 'author') {
                if (useFilterArr.includes('custom')) {
                    ans = useFilterArr.includes(test[filterKey].toLowerCase()) || test[filterKey].toLowerCase() !== 'akto'
                } else {
                    ans = useFilterArr.length > 0 && useFilterArr.includes(test[filterKey].toLowerCase())
                }
            }
            else if (useFilterArr.includes(test[filterKey].toLowerCase())) {
                ans = true
            }
        })
        return ans;
    }

    const resetSearchFunc = () => {
        setShowSearch(false);
        setSearchValue('');
    }

    let categoryRows = [], testRows = []

    const handleTestsSelected = (test) => {
        let localCopy = JSON.parse(JSON.stringify(testRun.tests))
        localCopy[testRun.selectedCategory] = localCopy[testRun.selectedCategory].map(curTest =>
            curTest.label === test.label ?
                {
                    ...curTest,
                    selected: !curTest.selected
                } : curTest
        )
        const testName = createTestName(apiCollectionName, localCopy, activeFromTesting, testRun.testName)
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
            testName: testName
        }))
    }

    const selectOnlyFilteredTests = (selected) => {
        const filteredTests = testRun.selectedCategory.length > 0 ? testRun.tests[testRun.selectedCategory].filter(x => filterFunc(x, selected)).map(item => item.value) : []
        setTestRun(prev => ({
            ...prev,
            tests: {
                ...prev.tests,
                [testRun.selectedCategory]: prev.tests[testRun.selectedCategory].map((curTest) => {
                    return {
                        ...curTest,
                        selected: filteredTests.length > 0 && filteredTests.includes(curTest.value)
                    }
                })
            }
        }))
    }

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

                return [(
                    <div
                        style={{ display: "grid", gridTemplateColumns: "auto max-content", alignItems: "center" }}
                        onClick={() => { setTestRun(prev => ({ ...prev, selectedCategory: category.name })); resetSearchFunc(); setOptionsSelected(initialArr) }}>
                        <div>
                            <Text variant="headingMd" fontWeight="bold" color={category.name === testRun.selectedCategory ? "success" : ""}>{category.displayName}</Text>
                            <Text>{selected} out of {total} selected</Text>
                        </div>
                        {selected > 0 && <Icon source={TickMinor} color="base" />}
                    </div>
                )];
            } else {
                return []
            }

        })

        const filteredTests = testRun.selectedCategory.length > 0 ? testRun.tests[testRun.selectedCategory].filter(x => (x.label.toLowerCase().includes(searchValue.toLowerCase()) && filterFunc(x))) : []
        testRows = filteredTests.map(test => {
            const isCustom = test?.author !== "AKTO"
            const label = (
                <span style={{ display: 'flex', gap: '4px', alignItems: 'flex-start' }}>
                    <Text variant="bodyMd">{test.label}</Text>
                    {isCustom ? <Box paddingBlockStart={"050"}><Badge status="warning" size="small">Custom</Badge></Box> : null}
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
        let hourStr = hour + (hour === 12 ? " noon" : " am")
        return { label: hourStr, value: hour.toString() }
    })
    const pmTimes = hours.map(hour => {
        let hourStr = hour + (hour === 12 ? " midnight" : " pm")
        return { label: hourStr, value: `${hour + 12}` }
    })

    const nowLabel = { label: "Now", value: "Now" }
    const hourlyTimes = [nowLabel, ...amTimes, ...pmTimes]

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

    const testRunTimeOptions = [...runTimeMinutes, ...runTimeHours]

    const runTypeOptions = [{ label: "Daily", value: "Daily" }, {label: 'Weekly',value: 'Weekly'}, {label: 'Monthly', value: 'Monthly'}, { label: "Continuously", value: "Continuously" }, { label: "Once", value: "Once" }]

    const maxRequests = hours.reduce((abc, x) => {
        if (x < 11) {
            let label = x * 10
            abc.push({ label, value: `${x * 10}` })
        }
        return abc
    }, [])

    const maxConcurrentRequestsOptions = [{ label: "Default", value: "-1" }, ...maxRequests, {label: '200', value: '200'}, {label: '300', value: '300'}, {label: '500', value: '500'}]

    function scheduleString() {
        if (testRun.hourlyLabel === "Now") {
            if (testRun.recurringDaily) {
                return <div data-testid="schedule_run_button">Run daily at this time</div>
            } else if (testRun.recurringWeekly) {
                return <div data-testid="schedule_run_button">Run weekly at this time</div>
            } else if (testRun.recurringMonthly) {
                return <div data-testid="schedule_run_button">Run monthly at this time</div>
            }
            else if (testRun.continuousTesting) {
                return <div data-testid="schedule_run_button">Run continuous testing</div>
            } else {
                return <div data-testid="schedule_run_button">Run once {func.prettifyFutureEpoch(testRun.startTimestamp)}</div>
            }
        } else {
            if (testRun.recurringDaily) {
                return <div data-testid="schedule_run_button">Run daily at {testRun.hourlyLabel}</div>
            } else if (testRun.recurringWeekly) {
                return <div data-testid="schedule_run_button">Run weekly on every {func.getDayOfWeek(testRun.startTimestamp)} at {testRun.hourlyLabel}</div>
            } else if (testRun.recurringMonthly) {
                return <div data-testid="schedule_run_button">Run monthly on every {new Date(testRun.startTimestamp * 1000).getDate()} at {testRun.hourlyLabel}</div>
            }else {
                return <div data-testid="schedule_run_button">Run once on {new Date(testRun.startTimestamp * 1000).getDate()} at {testRun.hourlyLabel}</div>
            }
        }
    }

    function handleRemoveAll() {
        setTestRun(prev => {
            const tests = { ...testRun.tests }
            Object.keys(tests).forEach(category => {
                tests[category] = tests[category].map(test => ({ ...test, selected: false }))
            })

            return { ...prev, tests: tests, testName: createTestName(apiCollectionName, tests, activeFromTesting, testRun.testName) }
        })
        func.setToast(true, false, "All tests unselected")
    }

    function checkRemoveAll() {
        const tests = { ...testRun.tests }
        let totalTests = 0
        Object.keys(tests).forEach(category => {
            tests[category].map((test) => {
                if (test.selected) {
                    totalTests++
                }
            })
        })
        return totalTests === 0;
    }

    async function handleRun() {
        const { startTimestamp, recurringDaily, recurringMonthly, recurringWeekly, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, cleanUpTestingResources, miniTestingServiceName, slackChannel } = testRun
        let {testName} = testRun;
        const autoTicketingDetails = jiraProjectMap ? testRun.autoTicketingDetails : null;
        const collectionId = parseInt(apiCollectionId)

        const tests = testRun.tests

        let selectedTests = []
        if (testMode) {
            Object.keys(tests).forEach(category => {
                tests[category].forEach(test => {
                    if (test.selected) selectedTests.push(test.value)
                })
            })
        }
        else {
            testName = testNameSuiteModal
        }

        let apiInfoKeyList;
        if (!selectedResourcesForPrimaryAction || selectedResourcesForPrimaryAction.length === 0) {
            apiInfoKeyList = endpoints.map(endpoint => ({
                apiCollectionId: endpoint.apiCollectionId,
                method: endpoint.method,
                url: endpoint.endpoint
            }))
        } else {
            apiInfoKeyList = selectedResourcesForPrimaryAction.map(str => {
                const parts = str.split('###')

                const method = parts[0]
                const url = parts[1]
                const apiCollectionId = parseInt(parts[2], 10)

                return {
                    apiCollectionId: apiCollectionId,
                    method: method,
                    url: url
                }
            })
        }
        let finalAdvancedConditions = []

        if (conditions.length > 0 && (conditions[0]?.data?.key?.length > 0 || conditions[0]?.data?.position?.length > 0)) {
            finalAdvancedConditions = transform.prepareConditionsForTesting(conditions)
        }

        if (filtered || selectedResourcesForPrimaryAction?.length > 0) {
            await observeApi.scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, "TESTING_UI", testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, finalAdvancedConditions, cleanUpTestingResources, testMode? []: testSuiteIds, (miniTestingServiceName || miniTestingServiceNames?.[0]?.value), (slackChannel || slackChannels?.[0]?.value) ,autoTicketingDetails)
        } else {
            await observeApi.scheduleTestForCollection(collectionId, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, finalAdvancedConditions, cleanUpTestingResources, testMode? []: testSuiteIds, (miniTestingServiceName || miniTestingServiceNames?.[0]?.value), (slackChannel || slackChannels?.[0]?.value), autoTicketingDetails)
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
        if(value === null || objList.length === 0){
            return {label: ''}
        }
        const obj = objList.find(obj => obj.value === value)
        return obj !== null ? obj : {label: ''}
    }

    function getCurrentStatus() {
        if (!testRun || testRun?.tests === undefined || testRun?.selectedCategory === undefined || testRun.tests[testRun.selectedCategory] === undefined)
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

    function countAllSelectedTests() {
        let count = 0;
        const test = { ...testRun.tests };
        Object.keys(test).forEach(category => {
            count += test[category].filter(test => test.selected).length;
        });
        return count;
    }

    function toggleTestsSelection(val) {
        let copyTestRun = testRun
        copyTestRun.tests[testRun.selectedCategory].forEach((test) => {
            test.selected = val
        })
        setTestRun(prev => {
            return { ...prev, tests: copyTestRun.tests, testName: createTestName(apiCollectionName, copyTestRun.tests, activeFromTesting, testRun.testName) }
        })
    }

    const handleInputValue = (val) => {
        setSearchValue(val);
    }

    function generateLabelForSlackIntegration() {
        return (
            <HorizontalStack gap={1}>
                <Link url='/dashboard/settings/integrations/slack' target="_blank" rel="noopener noreferrer" style={{ color: "#3385ff", textDecoration: 'none' }}>
                    Enable
                </Link>
                <Text>
                    Slack integration to send alerts post completion
                </Text>
            </HorizontalStack>
        );
    }

    function generateLabelForTeamsIntegration() {
        return (
            <HorizontalStack gap={1}>
                <Link url='/dashboard/settings/integrations/teamsWebhooks' target="_blank" rel="noopener noreferrer" style={{ color: "#3385ff", textDecoration: 'none' }}>
                    Enable
                </Link>
                <Text>
                    Microsoft Teams integration to send alerts post completion
                </Text>
            </HorizontalStack>
        );
    }

    function generateLabelForJiraIntegration() {
        return (
            <HorizontalStack gap={1}>
                {!jiraProjectMap && <Link url='/dashboard/settings/integrations/jira' target="_blank" rel="noopener noreferrer" style={{ color: "#3385ff", textDecoration: 'none' }}>
                    Enable
                </Link>}
                <Text>
                    Auto-create tickets
                </Text>
            </HorizontalStack>
        )
    }

    const handleButtonClick = (check) => {
        setTestMode(check);
        setAgenticMode(false);
    }

    // only for configurations
    const handleModifyConfig = async () => {
        const settings = transform.prepareConditionsForTesting(conditions)
        let autoTicketingDetails = jiraProjectMap? testRun.autoTicketingDetails : null;
        const editableConfigObject = transform.prepareEditableConfigObject(testRun, settings, testIdConfig.hexId,testSuiteIds,autoTicketingDetails)
        await testingApi.modifyTestingRunConfig(testIdConfig?.testingRunConfig?.id, editableConfigObject).then(() => {
            func.setToast(true, false, "Modified testing run config successfully")
            setShowEditableSettings(false)
        })
        if(activeFromTesting){
            toggleRunTest();
        }
        
    }

    const handleAddSettings = (parentAdvanceSettingsConfig) => {
        if (parentAdvanceSettingsConfig.length > 0) {
            dispatchConditions({ type: "clear" });
            parentAdvanceSettingsConfig.forEach((condition) => {
                const operatorType = condition.operator.type
                const obj = condition.data
                const finalObj = { 'data': obj, 'operator': { 'type': operatorType } }
                dispatchConditions({ type: "add", obj: finalObj })

            })
        }
    }

    const RunTestConfigurationComponent = (
        <RunTestConfiguration
            timeFieldsDisabled={shouldDisable}
            testRun={testRun}
            setTestRun={setTestRun}
            runTypeOptions={runTypeOptions}
            hourlyTimes={hourlyTimes}
            testRunTimeOptions={testRunTimeOptions}
            testRolesArr={testRolesArr}
            maxConcurrentRequestsOptions={maxConcurrentRequestsOptions}
            slackIntegrated={slackIntegrated}
            teamsTestingWebhookIntegrated={teamsTestingWebhookIntegrated}
            generateLabelForSlackIntegration={generateLabelForSlackIntegration}
            generateLabelForTeamsIntegration={generateLabelForTeamsIntegration}
            getLabel={getLabel}
            jiraProjectMap={jiraProjectMap}
            generateLabelForJiraIntegration={generateLabelForJiraIntegration}
            miniTestingServiceNames={miniTestingServiceNames}
            slackChannels={slackChannels}
        />
    )

    const editableConfigsComp = (
        <Modal
            large
            fullScreen
            open={showEditableSettings}
            onClose={() => setShowEditableSettings(false)}
            title={"Edit test configurations"}
            primaryAction={{
                content: 'Save',
                onAction: () => { handleModifyConfig(); },
            }}
        >
            <Modal.Section>
                <>
                    {RunTestConfigurationComponent}
                    <AdvancedSettingsComponent dispatchConditions={dispatchConditions} conditions={conditions} />
                </>
            </Modal.Section>
        </Modal>
    )

    return (
        <div>
            {!parentActivator ? activator : null}
            {showEditableSettings ? editableConfigsComp : null}
            <Modal

                activator={runTestRef}
                open={active || activeFromTesting}
                onClose={toggleRunTest}
                title={<HorizontalStack gap={4}><Text as="h2" fontWeight="semibold">{ "Configure " + mapLabel("test", getDashboardCategory())}</Text>
                    <ButtonGroup segmented>
                        <Button monochrome pressed={testMode && !agenticMode} icon={NoteMinor} onClick={() => handleButtonClick(true)}></Button>
                        <Button monochrome pressed={!testMode && !agenticMode} icon={AppsFilledMajor} onClick={() => handleButtonClick(false)}></Button>
                        {isAgenticCategory && <Button monochrome pressed={agenticMode} icon={MagicMajor} onClick={() => {setAgenticMode(true); setTestMode(false)}}></Button>}
                    </ButtonGroup>
                </HorizontalStack>}
                primaryAction={{
                    content: activeFromTesting ? "Save" : scheduleString(),
                    onAction: activeFromTesting ? handleModifyConfig : handleRun,
                    disabled: (testMode && activeFromTesting && (testSuiteIds.length !== 0)) || (countAllSelectedTests() === 0 && testSuiteIds.length === 0  ) || !testRun.authMechanismPresent
                }}
                secondaryActions={[
                    countAllSelectedTests() && testMode ? {
                        content: `${countAllSelectedTests()} ${mapLabel("tests selected", getDashboardCategory())}` ,
                        disabled: true,
                        plain: true,
                    } : null,
                    {
                        content: 'Cancel',
                        onAction: () => toggleRunTest(),
                    },

                ].filter(Boolean)}

                large

                footer={testMode || agenticMode ? null : openConfigurations ? <Button onClick={() => openConfigurationsToggle(false)} plain><Text as="p" fontWeight="regular">Go back to test selection</Text></Button> : <Button onClick={() => openConfigurationsToggle(true)} plain><Text as="p" fontWeight="regular">Change Configurations</Text></Button>}
            >
                {loading ? <SpinnerCentered /> :
                    testMode ?
                        <Modal.Section>
                            <VerticalStack gap={"3"}>
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
                                            disabled={activeFromTesting}
                                            onChange={(testName) => setTestRun(prev => ({ ...prev, testName: testName }))}
                                        />
                                    </div>
                                    <div className="removeAllButton">
                                        <Button
                                            icon={CancelMajor}
                                            plain
                                            destructive
                                            onClick={handleRemoveAll}
                                            disabled={checkRemoveAll()}><div data-testid="remove_all_tests">Clear selection</div></Button>
                                    </div>
                                </div>
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
                                        <div style={{ padding: !showSearch ? "13px" : "9px", alignItems: "center", justifyContent: 'space-between', display: 'flex' }}>
                                            <HorizontalStack gap={"2"}>
                                                <Checkbox
                                                    checked={allTestsSelectedOfCategory}
                                                    onChange={(val) => toggleTestsSelection(val)}
                                                />
                                                <Text variant="headingMd">Tests</Text>
                                            </HorizontalStack>
                                            <HorizontalStack gap={"2"}>
                                                <Popover
                                                    activator={<Button

                                                        size="slim"
                                                        onClick={() => setShowFiltersOption(!showFiltersOption)}
                                                        plain>More filters</Button>}
                                                    onClose={() => setShowFiltersOption(false)}
                                                    active={showFiltersOption}
                                                >
                                                    <Popover.Pane fixed>
                                                        <OptionList
                                                            onChange={(x) => { setOptionsSelected(x); selectOnlyFilteredTests(x); }}
                                                            allowMultiple
                                                            sections={sectionsForFilters}
                                                            selected={optionsSelected}
                                                        />
                                                    </Popover.Pane>
                                                </Popover>
                                                {showSearch ? <TextField onChange={handleInputValue} value={searchValue} autoFocus
                                                    focused /> : null}
                                                <Tooltip content={"Click to search"} dismissOnMouseOut>
                                                    <Button size="slim" icon={SearchMinor} onClick={() => setShowSearch(!showSearch)} />
                                                </Tooltip>
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
                                {RunTestConfigurationComponent}
                            </VerticalStack>
                            <AdvancedSettingsComponent dispatchConditions={dispatchConditions} conditions={conditions} />

                        </Modal.Section> : 
                        !agenticMode ? <Modal.Section>
                            {!openConfigurations ? <RunTestSuites
                                testRun={testRun}
                                setTestRun={setTestRun}
                                handleRemoveAll={handleRemoveAll}
                                apiCollectionName={apiCollectionName}
                                setTestMode={setTestMode}
                                activeFromTesting={activeFromTesting}
                                checkRemoveAll={checkRemoveAll} handleModifyConfig={handleModifyConfig}
                                setTestSuiteIds={setTestSuiteIds} testSuiteIds={testSuiteIds}
                                testNameSuiteModal={testNameSuiteModal}
                                setTestNameSuiteModal={setTestNameSuiteModal}/> :
                                <>
                                    {RunTestConfigurationComponent}
                                    <AdvancedSettingsComponent dispatchConditions={dispatchConditions} conditions={conditions} />
                                </>
                            }
                        </Modal.Section>
                        : <Modal.Section>
                            <VerticalStack gap={"3"}>
                                <HorizontalStack gap={"3"} align="start" wrap={false}>
                                    <Text variant="headingMd">Name:</Text>
                                    <div style={{ width: "60%" }}>
                                        <TextField
                                            placeholder="Enter test name"
                                            value={testRun.testName}
                                            disabled={activeFromTesting}
                                            onChange={(testName) => setTestRun(prev => ({ ...prev, testName: testName }))}
                                        />
                                    </div>
                                </HorizontalStack>
                                {RunTestConfigurationComponent}
                                <AdvancedSettingsComponent dispatchConditions={dispatchConditions} conditions={conditions} />
                            </VerticalStack>
                        </Modal.Section>
                }
            </Modal>
        </div>
    );
}

export default RunTest