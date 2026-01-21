import { VerticalStack, TextField, Button, Text, Scrollable, } from "@shopify/polaris";
import {  CancelMajor } from "@shopify/polaris-icons";
import { useEffect, useState } from "react";
import "./run_test_suites.css"
import RunTestSuiteRow from "./RunTestSuiteRow";
import testingApi from "../../testing/api";
import func from "@/util/func";
import { getDashboardCategory, mapLabel, isAgenticSecurityCategory } from "../../../../main/labelHelper";

function RunTestSuites({ apiCollectionName, activeFromTesting, setTestSuiteIds, testSuiteIds,setTestNameSuiteModal,testNameSuiteModal }) {

    const [data, setData] = useState({ agenticSecurity: {}, owaspTop10List: {}, testingMethods:{}, custom : {}, severity: {}, duration: {} });
    const [testSuiteIdsNameMap, setTestSuiteIdsNameMap] = useState({});


    async function fetchData() {

        let idsNameMap = {};

        // Fetch Custom Test Suite
        const fetchedTestSuite = await testingApi.fetchAllTestSuites();
        const testSuitesFromBackend = fetchedTestSuite == null ? [] : [...fetchedTestSuite.defaultTestSuites]

        const newOwaspTop10TestSuites = testSuitesFromBackend?.filter(testSuiteItem => testSuiteItem.suiteType === "OWASP").map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }
        })

        const newTestingMethodsTestSuites = testSuitesFromBackend?.filter(testSuiteItem => testSuiteItem.suiteType === "TESTING_METHODS").map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }
        })

        const newDurationTestSuites = testSuitesFromBackend?.filter(testSuiteItem => testSuiteItem.suiteType === "DURATION").map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }
        })

        const severityOrder = {"Critical": 0,"High": 1,"Medium": 2,"Low": 3}
        const severityTestSuites = testSuitesFromBackend?.filter(testSuiteItem => testSuiteItem.suiteType === "SEVERITY").sort((a, b) => severityOrder[a.name] - severityOrder[b.name]).map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }
        })

        const agenticSecurityTestSuites = testSuitesFromBackend?.filter(testSuiteItem => testSuiteItem.suiteType === "AI_AGENT_SECURITY").map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }
        })

        const fetchedData = fetchedTestSuite?.testSuiteList?.map((testSuiteItem) => {
            idsNameMap[testSuiteItem.hexId] = testSuiteItem.name;
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }

        });
        setData(prev => {
            if (
                !func.deepArrayComparison(prev?.agenticSecurity?.testSuite||[], agenticSecurityTestSuites) ||
                !func.deepArrayComparison(prev?.owaspTop10List?.testSuite||[],newOwaspTop10TestSuites) ||
                !func.deepArrayComparison(prev?.testingMethods?.testSuite||[], newTestingMethodsTestSuites) ||
                !func.deepArrayComparison(prev?.custom?.testSuite||[], fetchedData) ||
                !func.deepArrayComparison(prev?.severity?.testSuite||[], severityTestSuites) ||
                !func.deepArrayComparison(prev?.duration?.testSuite||[], newDurationTestSuites)
            ) {
                return {
                    ...prev,
                    agenticSecurity: { rowName: "Agentic Security", testSuite: agenticSecurityTestSuites },
                    owaspTop10List: { rowName: "OWASP top 10", testSuite: newOwaspTop10TestSuites },
                    testingMethods: { rowName: "Testing Methods", testSuite: newTestingMethodsTestSuites },
                    custom: { rowName: "Custom", testSuite: fetchedData },
                    severity: { rowName: "Severity", testSuite: severityTestSuites },
                    duration: { rowName: "Duration", testSuite: newDurationTestSuites }
                };
            }
            return prev;
        });

        setTestSuiteIdsNameMap(idsNameMap);

    }
    
    useEffect(() => {
        fetchData();
    }, []);

    function createTestName(testSuiteIds) {
        if (activeFromTesting) return;

        let copyTestSuiteIds = [...testSuiteIds];
        let testName = apiCollectionName;
        copyTestSuiteIds.forEach(ele => {
            if (testSuiteIdsNameMap[ele]) {
                testName += "_" + testSuiteIdsNameMap[ele];
            }
        });
        setTestNameSuiteModal(testName);
    }



    function handleTestSuiteSelection(data) {
        setTestSuiteIds((prev) => {
            let updated;
            if (!prev.includes(data.id)) {
                updated = [...prev, data.id];
            } else {
                updated = prev.filter((id) => id !== data.id);
            }

            createTestName(updated);
            return updated;
        });
    }


    function countTestSuitesTests(data) {
        return data?.tests?.length || 0;
    }

    function checkedSelected(data) {
        if (testSuiteIds.includes(data.id)) return true
        return false
    }

    function checkifSelected(data) {
        if(checkedSelected(data) === true) {
            return `${countTestSuitesTests(data)} ${mapLabel("tests selected", getDashboardCategory())}`
        }
        return `${countTestSuitesTests(data)} tests`;
    }

    function handleRemoveAll() {
        setTestSuiteIds([])
        setTestNameSuiteModal(apiCollectionName)
    }

    return (
        <Scrollable vertical={true} horizontal={false} shadow={false}>
            <div className="runTestSuitesModal" style={{ minHeight: "72vh",paddingBlockEnd:"1rem" }}>
                <VerticalStack gap={5}>
                    <div style={{ display: "grid", gridTemplateColumns: "max-content auto max-content", alignItems: "center", gap: "10px" }}>
                        <Text variant="headingMd">Name:</Text>
                        <div style={{ maxWidth: "75%" }}>
                            <TextField
                                placeholder="Enter test name"
                                value={testNameSuiteModal}
                                disabled={activeFromTesting}
                                onChange={(testName) => setTestNameSuiteModal(testName)}
                            />
                        </div>
                        <div className="removeAllButton">
                            <Button
                                icon={CancelMajor}
                                plain
                                destructive
                                onClick={handleRemoveAll}
                                disabled={testSuiteIds?.length===0}><div data-testid="remove_all_tests">Clear selection</div></Button></div>
                    </div>
                    {
                        Object.entries(data)
                            .filter(([key]) => {
                                if (isAgenticSecurityCategory()) {
                                    return key !== 'owaspTop10List';
                                }
                                return key !== 'agenticSecurity';
                            })
                            .map(([, value]) => {
                                const filteredTestSuite = (value?.testSuite || []).filter(suite => suite?.tests?.length > 0);
                                if (filteredTestSuite.length === 0) return null;
                                return (
                                    <RunTestSuiteRow 
                                        data={{ ...value, testSuite: filteredTestSuite }}
                                        checkifSelected={checkifSelected} 
                                        checkedSelected={checkedSelected} 
                                        handleTestSuiteSelection={handleTestSuiteSelection}
                                    />
                                );
                            })
                            .filter(Boolean)
                    }

                </VerticalStack>
            </div>
        </Scrollable>
    )
}

export default RunTestSuites
