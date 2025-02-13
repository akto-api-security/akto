import { VerticalStack, Modal, TextField, Button, Text, HorizontalStack, Collapsible, Badge, Pagination, TextContainer, Icon, Scrollable, Checkbox, Box, Tooltip, Card, MediaCard } from "@shopify/polaris";
import { TickMinor, CancelMajor, SearchMinor } from "@shopify/polaris-icons";
import { useEffect, useRef, useState } from "react";
import "./run_test_suites.css"
import createTestName from "./Utils"

function RunTestSuites({ testRun, setTestRun, apiCollectionName, checkRemoveAll, handleRemoveAll, handleModifyConfig, activeFromTesting }) {

    const [owaspTop10, owaspTop10Toggle] = useState(true);
    const [testingMethods, testingMethodsToggle] = useState(true);

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

    function createAcronym(str) {
        return str.split(/\s+/).map(word => word.charAt(0).toUpperCase()).join('');
    }

    function handleTestSuiteSelection(key, data) {
        setTestRun(prev => {
            const updatedTests = { ...prev.tests };
            const someSelected = data.some(category =>
                updatedTests[category]?.some(test => test.selected)
            );
            data.forEach(category => {
                if (updatedTests[category]) {
                    updatedTests[category] = updatedTests[category].map(test => ({
                        ...test,
                        selected: someSelected ? false : true
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
        const test = { ...testRun?.tests };
        data.forEach(category => {
            if (test[category]) {
                count += test[category].length;
            }
        });
        return count;
    }

    function checkedSelected(data) {
        if (testRun === undefined) return;
        let atleastOne = false;
        let allSelected = true;;

        for (const category of data) {
            if (testRun.tests[category] && testRun.tests[category].length > 0) {
                if (testRun.tests[category].some(test => !test.selected)) {
                    allSelected = false;
                }
                if (testRun.tests[category].some(test => test.selected)) {
                    atleastOne = true;
                }
            }
        }
        if (atleastOne && allSelected) return true;
        else if (atleastOne) return "indeterminate";
        else return false;
    }

    function checkDisableTestSuite(data) {
        if (testRun === undefined) return 0;
        for (const category of data) {
            if (testRun.tests[category] && testRun.tests[category].length > 0) {
                return false;
            }
        }
        return true;
    }

    function checkifSelected(data) {
        let text = `${countTestSuitesTests(data)} tests`;
        let isSomeSelected = false;
        let countSelected = 0;
        for (const category of data) {
            if (testRun.tests[category] && testRun.tests[category].length > 0) {
                if (testRun.tests[category].some(test => test.selected)) {
                    isSomeSelected = true;
                }
                testRun.tests[category]?.forEach(test => {
                    if (test.selected) {
                        countSelected++;
                    }
                });
            }
        }
        if (isSomeSelected === false) return text;
        else return `${countSelected} out of ${countTestSuitesTests(data)} selected`;
    }

    function renderAktoTestSuites(data) {
        return (
            <div className="testSuiteCard" style={{ marginLeft: "0.15rem" }}>
                <Box minWidth="300px" maxWidth="300px" borderRadius={2} borderStyle="solid" insetInlineEnd={1}>
                    <VerticalStack>
                        <div >
                            <Box paddingBlockStart={2} paddingBlockEnd={2} paddingInlineStart={4} paddingInlineEnd={4} borderRadiusEndStart={2} borderRadiusEndEnd="2" borderColor="border">
                                <Checkbox
                                    label={
                                        <Tooltip content={data?.key}>
                                            <Text variant="headingSm" fontWeight="medium" truncate={true}>{data?.key}</Text>
                                        </Tooltip>
                                    }
                                    helpText={checkifSelected(data?.value)}
                                    onChange={() => { handleTestSuiteSelection(data?.key, data?.value) }}
                                    checked={checkedSelected(data?.value)}
                                    disabled={checkDisableTestSuite(data?.value)}
                                />

                            </Box>
                        </div>
                    </VerticalStack>
                </Box>
            </div>
        );
    }

    function handleTestingMethodTestSuiteSelection(data) {
        setTestRun(prev => {
            const updatedTests = { ...prev.tests };
            let someSelected = false;
            Object.keys(updatedTests).forEach(category => {
                if (updatedTests[category]?.some(test => test.nature === data && test.selected)) {
                    someSelected = true;
                }
            });

            Object.keys(updatedTests).forEach(category => {
                if (updatedTests[category]) {
                    updatedTests[category] = updatedTests[category].map(test => ({
                        ...test,
                        selected: test.nature === data ? (someSelected ? false : true) : test.selected
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

    function checkedSelectedTestingMethod(data) {
        if (!testRun) return false;
        let atleastOne = false;
        let allSelected = true;;

        const updatedTests = { ...testRun.tests };
        Object.keys(updatedTests).forEach(category => {
            if (updatedTests[category]?.some(test => !test.selected && test.nature === data)) {
                allSelected = false;
            }
            if (updatedTests[category]?.some(test => test.selected && test.nature === data)) {
                atleastOne = true;
            }
        });
        if (atleastOne && allSelected) return true;
        else if (atleastOne) return "indeterminate";
        else return false;
    }

    function checkDisableTestingMethodTestSuite(data) {
        if (!testRun) return false;
        const updatedTests = { ...testRun.tests };
        Object.keys(updatedTests).forEach(category => {
            if (updatedTests[category]?.some(test => test.nature === data)) {
                return true;
            }
        });

        return false;
    }
    function countTestingMethodTestSuitesTests(data) {
        let count = 0;
        const updatedTests = { ...testRun.tests };
        Object.keys(updatedTests).forEach(category => {
            updatedTests[category]?.forEach(test => {
                if (test.nature === data) {
                    count++;
                }
            });
        });
        return count;
    }

    function checkifTestingMethodSelected(data) {
        let text = `${countTestingMethodTestSuitesTests(data)} tests`;
        let isSomeSelected = false;
        let countSelected = 0;
        const updatedTests = { ...testRun.tests };
        Object.keys(updatedTests).forEach(category => {
            updatedTests[category]?.forEach(test => {
                if (test.nature === data && test.selected === true) {
                    isSomeSelected = true;
                    countSelected++;
                }
            });
        });

        if (isSomeSelected === false) return text;
        else return `${countSelected} out of ${countTestingMethodTestSuitesTests(data)} selected`;
    }

    function renderTestingMethod(data) {
        const formattedData = data.toUpperCase();
        return (
            <div className="testSuiteCard" style={{ marginLeft: "0.15rem" }}>
                <Box minWidth="300px" maxWidth="300px" borderRadius={2} borderStyle="solid" insetInlineEnd={1}>
                    <VerticalStack>
                        <div >
                            <Box paddingBlockStart={2} paddingBlockEnd={2} paddingInlineStart={4} paddingInlineEnd={4} borderRadiusEndStart={2} borderRadiusEndEnd="2" borderColor="border">
                                <Checkbox
                                    label={
                                        <Tooltip content={data}>
                                            <Text variant="headingSm" fontWeight="medium" truncate={true}>{data}</Text>
                                        </Tooltip>
                                    }
                                    helpText={checkifTestingMethodSelected(formattedData)}
                                    onChange={() => { handleTestingMethodTestSuiteSelection(formattedData) }}
                                    checked={checkedSelectedTestingMethod(formattedData)}
                                    disabled={checkDisableTestingMethodTestSuite(formattedData)}
                                />

                            </Box>
                        </div>
                    </VerticalStack>
                </Box>
            </div>
        );
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
                    <VerticalStack>
                        <HorizontalStack align="start">
                            <div className="testSuiteDisclosureButton" style={{ paddingBottom: "0.5rem" }}>
                                <Button
                                    onClick={() => owaspTop10Toggle(!owaspTop10)}
                                    ariaExpanded={open}
                                    ariaControls="basic-collapsible"
                                    plain
                                    disclosure
                                >
                                    <span style={{ fontWeight: "550", color: " #202223" }}>
                                        {"OWASP top 10"} <span style={{ paddingLeft: "0.2rem" }}> </span>
                                    </span>
                                    <Badge>{10}</Badge>
                                </Button>
                            </div>
                        </HorizontalStack>
                        <Collapsible
                            open={owaspTop10}
                            id="basic-collapsible"
                            transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
                            expandOnPrint
                        >
                            {/* <div className="testSuiteHorizontalScroll" style={{ display: "flex" }}> */}
                            <HorizontalStack gap={4} align={"start"} blockAlign={"center"}>

                                {

                                    Object.entries(owaspTop10List).map(([key, value]) => (
                                        renderAktoTestSuites({ key, value })
                                    ))
                                }


                            </HorizontalStack>
                            {/* </div> */}
                        </Collapsible>

                    </VerticalStack>
                    <VerticalStack>
                        <HorizontalStack align="start">
                            <div className="testSuiteDisclosureButton" style={{ paddingBottom: "0.5rem" }}>
                                <Button
                                    onClick={() => testingMethodsToggle(!testingMethods)}
                                    ariaExpanded={open}
                                    ariaControls="basic-collapsible"
                                    plain
                                    disclosure
                                >
                                    <span style={{ fontWeight: "550", color: " #202223" }}>
                                        {"Testing Methods"} <span style={{ paddingLeft: "0.2rem" }}> </span>
                                    </span>
                                    <Badge>{2}</Badge>
                                </Button>
                            </div>
                        </HorizontalStack>
                        <Collapsible
                            open={testingMethods}
                            id="basic-collapsible"
                            transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
                            expandOnPrint
                        >
                            <HorizontalStack gap={4} align={"start"} blockAlign={"center"}>

                                {

                                    [ "Intrusive","Non_intrusive"].map((val) => (
                                        renderTestingMethod(val)
                                    ))
                                }


                            </HorizontalStack>
                        </Collapsible>

                    </VerticalStack>
                </VerticalStack>




            </div>
        </Scrollable>
    )
}

export default RunTestSuites