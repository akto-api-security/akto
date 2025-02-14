import { VerticalStack, Modal, TextField, Button, Text, HorizontalStack, Collapsible, Badge, Pagination, TextContainer, Icon, Scrollable, Checkbox, Box, Tooltip, Card, MediaCard } from "@shopify/polaris";
import { TickMinor, CancelMajor, SearchMinor } from "@shopify/polaris-icons";
import { useEffect, useRef, useState } from "react";
import "./run_test_suites.css"
import createTestName from "./Utils"
import RunTestSuiteRow from "./RunTestSuiteRow";

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

function RunTestSuites({ testRun, setTestRun, apiCollectionName, checkRemoveAll, handleRemoveAll, handleModifyConfig, activeFromTesting }) {

    const [data, setData] = useState({ owaspTop10List: {}, testingMethods:{} });

    useEffect(() => {
        setData((prev) => {
            const updatedData = { ...prev };
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
            if (
                JSON.stringify(prev.owaspTop10List.testSuite) !== JSON.stringify(newOwaspTop10TestSuites) ||
                JSON.stringify(prev.testingMethods.testSuite) !== JSON.stringify(newTestingMethodsTestSuites)
            ) {
                return {
                    ...updatedData,
                    owaspTop10List: { rowName: "OWASP top 10", testSuite: newOwaspTop10TestSuites },
                    testingMethods: { rowName: "Testing Methods", testSuite: newTestingMethodsTestSuites }
                };
            }
    
            return prev;
        });

    }, []);    



    function handleTestSuiteSelection(data) {
        setTestRun(prev => {
            const updatedTests = { ...prev.tests };
            const testSet = new Set(data.tests);

            const someSelected = Object.keys(updatedTests).some(category =>
                updatedTests[category]?.some(test => testSet.has(test.value) && test.selected)
            );

            Object.keys(updatedTests).forEach(category => {
                if (updatedTests[category]) {
                    updatedTests[category] = updatedTests[category].map(test => ({
                        ...test,
                        selected: testSet.has(test.value)? (someSelected ? false : true): test.selected
                    }));
                }
            });

            let updatedTestName = createTestName(apiCollectionName, updatedTests, activeFromTesting, prev.testName);

            return {
                ...prev,
                tests: updatedTests,
                testName: updatedTestName
            };
        });
    }

    function countTestSuitesTests(data) {
        if (testRun === undefined) return;
        let count = 0;
        const updatedTests = { ...testRun?.tests };
        const testSet = new Set(data.tests);
        Object.keys(updatedTests).forEach(category => {
            updatedTests[category]?.forEach(test => {
                if (testSet.has(test.value)) {
                    count++;
                }
            });
        });
        return count;
    }

    function checkedSelected(data) {
        if (testRun === undefined) return;
        let atleastOne = false;
        let allSelected = true;;
        const updatedTests = { ...testRun?.tests };
        const testSet = new Set(data.tests);
        Object.keys(updatedTests).forEach(category => {
            if (updatedTests[category] && updatedTests[category].length > 0) {
                if (updatedTests[category].some(test => !test.selected && testSet.has(test.value))) {
                    allSelected = false;
                }
                if (updatedTests[category].some(test => test.selected  && testSet.has(test.value))) {
                    atleastOne = true;
                }
            }
        });

        if (atleastOne && allSelected) return true;
        else if (atleastOne) return "indeterminate";
        else return false;
    }

    function checkDisableTestSuite(data) {
        if (testRun === undefined) return 0;
        const updatedTests = { ...testRun?.tests };
        const testSet = new Set(data.tests);
        Object.keys(updatedTests).forEach(category => {
            updatedTests[category]?.forEach(test => {
                if (testSet.has(test.value)) {
                    return true;
                }
            });
        });
        return false;
    }

    function checkifSelected(data) {
        let text = `${countTestSuitesTests(data)} tests`;
        let isSomeSelected = false;
        let countSelected = 0;
        const updatedTests = { ...testRun?.tests };
        const testSet = new Set(data.tests);
        Object.keys(updatedTests).forEach(category => {
            if (updatedTests[category] && updatedTests[category].length > 0) {
                if (updatedTests[category].some(test => test.selected && testSet.has(test.value))) {
                    isSomeSelected = true;
                }
                updatedTests[category]?.forEach(test => {
                    if (test.selected && testSet.has(test.value)) {
                        countSelected++;
                    }
                });
            }
        });
        if (isSomeSelected === false) return text;
        else return `${countSelected} out of ${countTestSuitesTests(data)} selected`;
    }

    return (
        <Scrollable vertical={true} horizontal={false} shadow={false}>
            <div className="runTestSuitesModal" style={{ minHeight: "72vh" }}>
                <VerticalStack gap={5}>
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
                                disabled={checkRemoveAll()}><div data-testid="remove_all_tests">Clear selection</div></Button></div>
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