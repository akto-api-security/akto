import { VerticalStack, Modal, TextField, Button, Text, HorizontalStack, Collapsible, Badge, Pagination, TextContainer, Icon, Scrollable, Checkbox, Box, Tooltip, Card, MediaCard } from "@shopify/polaris";
import { TickMinor, CancelMajor, SearchMinor } from "@shopify/polaris-icons";
import { useEffect, useRef, useState } from "react";
import "./run_test_suites.css"
import { createTestName } from "./Utils"
import RunTestSuiteRow from "./RunTestSuiteRow";
import testingApi from "../../testing/api";
import func from "../../../../../util/func";

const owaspTop10List = {
    "Broken Object Level Authorization": ["BOLA"],
    "Broken Authentication": ["NO_AUTH"],
    "Broken Object Property Level Authorization": ["EDE", "MA"],
    "Unrestricted Resource Consumption": ["RL"],
    "Broken Function Level Authorization": ["BFLA"],
    "Unrestricted Access to Sensitive Business Flows": ["INPUT"],
    "Server Side Request Forgery": ['SSRF'],
    "Security Misconfiguration": ["SM", "UHM", "VEM", "MHH", "SVD", "CORS", "ILM"],
    "Improper Inventory Management": ["IAM", "IIM"],
    "Unsafe Consumption of APIs": ["COMMAND_INJECTION", "INJ", "CRLF", "SSTI", "LFI", "XSS", "INJECT"]
}

function RunTestSuites({ testRun, setTestRun, apiCollectionName, activeFromTesting, setTestSuiteIds, testSuiteIds,setSelectedGeneratedSuiteTests, selectedGeneratedSuiteTests,setTestNameSuiteModal,testNameSuiteModal }) {

    const [data, setData] = useState({ owaspTop10List: {}, testingMethods:{}, custom : {}, severity: {} });


    async function fetchData() {

        // Generate OWASP Top 10 Test Suites
        const newOwaspTop10TestSuites = Object.entries(owaspTop10List).map(([key, value]) => {
            const tests = [];
            value.forEach((cat) => {
                testRun?.tests?.[cat]?.forEach((test) => {
                    tests.push(test.value);
                });
            });
            return { name: key, tests };
        });

        // Generate Testing Methods Test Suites
        const newTestingMethodsTestSuites = ["Intrusive", "Non_intrusive"].map((val) => {
            const tests = [];
            Object.keys(testRun?.tests || {}).forEach((category) => {
                testRun.tests[category]?.forEach((test) => {
                    if (test.nature === val.toUpperCase()) {
                        tests.push(test.value);
                    }
                });
            });
            return { name: val, tests };
        });

        // Fetch Severity Test Suites
        const severityTestSuites = ["Critical", "High", "Medium", "Low"].map((val) => {
            const tests = [];
            Object.keys(testRun?.tests || {}).forEach((category) => {
                testRun.tests[category]?.forEach((test) => {
                    if (test.severity === val.toUpperCase()) {
                        tests.push(test.value);
                    }
                });
            });
            return { name: val, tests };
        });

        // Fetch Custom Test Suite
        const fetchedTestSuite = await testingApi.fetchAllTestSuites();
        const fetchedData = fetchedTestSuite.map((testSuiteItem) => {
            return { name: testSuiteItem.name, tests: testSuiteItem.subCategoryList, id: testSuiteItem.hexId }

        });
        setData(prev => {
            if (
                !func.deepArrayComparison(prev?.owaspTop10List?.testSuite||[],newOwaspTop10TestSuites) ||
                !func.deepArrayComparison(prev?.testingMethods?.testSuite||[], newTestingMethodsTestSuites) ||
                !func.deepArrayComparison(prev?.custom?.testSuite||[], fetchedData) ||
                !func.deepArrayComparison(prev?.severity?.testSuite||[], severityTestSuites)
            ) {
                return {
                    ...prev,
                    owaspTop10List: { rowName: "OWASP top 10", testSuite: newOwaspTop10TestSuites },
                    testingMethods: { rowName: "Testing Methods", testSuite: newTestingMethodsTestSuites },
                    custom: { rowName: "Custom", testSuite: fetchedData },
                    severity: { rowName: "Severity", testSuite: severityTestSuites }
                };
            }
            return prev;
        });
    }
    
    useEffect(() => {
        fetchData();
    }, []);


    function handleTestSuiteSelection(data) {
        if (!data.id) {
            let testName = apiCollectionName + "_" + func.joinWordsWithUnderscores(data.name)
            setTestNameSuiteModal(testName)
            setSelectedGeneratedSuiteTests([...data?.tests])
            setTestSuiteIds([])
            return;
        }

        setTestSuiteIds((prev) => {
            if (!prev.includes(data.id)) {
                return [data.id]
            }
        })
        let testName = apiCollectionName + "_" + data.name;
        setTestNameSuiteModal(testName)
        setSelectedGeneratedSuiteTests([])
    }

    function countTestSuitesTests(data) {
        return data?.tests?.length || 0;
    }

    function checkedSelected(data) {
        if (testRun === undefined) return;
        if (!data.id) {
            let allSelected = func.deepArrayComparison(data?.tests, selectedGeneratedSuiteTests)
            return allSelected;
        }
        if (testSuiteIds.includes(data.id)) return true
        return false
    }

    function checkDisableTestSuite(data) {
        if (testRun === undefined) return false;
        const updatedTests = { ...testRun?.tests };
        const testSet = new Set(data.tests);
        let check = true;
        Object.keys(updatedTests).forEach(category => {
            updatedTests[category]?.forEach(test => {
                if (testSet.has(test.value)) {
                    check = false;
                }
            });
        });
        return check;
    }

    function checkifSelected(data) {
        if(checkedSelected(data) === true) {
            return `${countTestSuitesTests(data)} tests selected`
        }
        return `${countTestSuitesTests(data)} tests`;
    }

    function handleRemoveAll() {
        setTestSuiteIds([])
        setSelectedGeneratedSuiteTests([])
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
                        Object.values(data).map((key) => {
                            return (
                                <RunTestSuiteRow 
                                    data={key} 
                                    checkifSelected={checkifSelected} 
                                    checkedSelected={checkedSelected} 
                                    handleTestSuiteSelection={handleTestSuiteSelection} 
                                    checkDisableTestSuite={checkDisableTestSuite} 
                                />
                            );
                        })   
                    }

                </VerticalStack>
            </div>
        </Scrollable>
    )
}

export default RunTestSuites