import { VerticalStack, Modal, TextField, Button, Text, HorizontalStack, Collapsible, Badge, Pagination, TextContainer, Icon, Scrollable, Checkbox, Box, Tooltip, Card, MediaCard } from "@shopify/polaris";
import { TickMinor, CancelMajor, SearchMinor } from "@shopify/polaris-icons";
import { useEffect, useRef, useState } from "react";
import "./run_test_suites.css"


function RunTestSuites({  testRun, setTestRun, handleRun, checkRemoveAll, handleRemoveAll,handleModifyConfig }) {

    const [owaspTop10, owaspTop10Toggle] = useState(true);

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
            let someSelected = true
            data.forEach(category => {
                if (updatedTests[category]) {
                    someSelected = updatedTests[category].some(test => test.selected);
                    updatedTests[category] = updatedTests[category].map(test => ({
                        ...test,
                        selected: someSelected ? false : true
                    }));
                }
            });

            let updatedTestName = prev.testName;
            const acronym = createAcronym(key);
            if (someSelected) {
                const regex = new RegExp(`_${acronym}`, 'g');
                updatedTestName = updatedTestName.replace(regex, "");
            } else {
                updatedTestName += `_${acronym}`;
            }

            return {
                ...prev,
                tests: updatedTests,
                testName: updatedTestName
            };
        });
    }

    function countTestSuitesTests(data) {
        if (testRun === undefined) return ;
        let count = 0;
        const test = { ...testRun?.tests };
        data.forEach(category => {
            if (test[category]) {
                count += test[category].length;
            }
        });
        return count;
    }

    function countAllSelectedTests() {
        if (testRun === undefined) return ;
        let count = 0;
        const test = { ...testRun.tests };
        Object.keys(test).forEach(category => {
            count += test[category].filter(test => test.selected).length;
        });
        return count;
    }

    function checkedSelected(data) {
        if (testRun === undefined) return ;
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

    function renderAktoTestSuites(data) {
        return (
            <div className="testSuiteCard" style={{marginLeft: "0.15rem"}}>
                <Box minWidth="300px" maxWidth="300px" minHeight="152px" borderRadius={2} borderStyle="solid" paddingBlockEnd={4} insetInlineEnd={1}>
                    <VerticalStack>
                        <div style={{ height: "80px", backgroundColor: "#ECEBFF", borderTopLeftRadius: "0.5rem", borderTopRightRadius: "0.5rem" }}>
                            <img src="/public/test_suite.svg"/>
                        </div>
                        <div >
                            <Box paddingBlockStart={2} paddingBlockEnd={2} paddingInlineStart={4} paddingInlineEnd={4} borderRadiusEndStart={2} borderRadiusEndEnd="2" borderColor="border">

                                <Checkbox
                                    label={
                                        <Tooltip content={data?.key}>
                                            <Text variant="headingMd" fontWeight="regular" truncate={true}>{data?.key}</Text>
                                        </Tooltip>
                                    }
                                    helpText={`${countTestSuitesTests(data?.value)} tests`}
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

    function filterTestSuites() {
        if (!searchValue || searchValue === "") return owaspTop10List;
        const filtered = {};
        Object.entries(owaspTop10List).forEach(([key, value]) => {
            if (key.toLowerCase().includes(searchValue.toLowerCase())) {
                filtered[key] = value;
            }
        });
        return filtered;
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
                            onChange={(testName) => setTestRun(prev => ({ ...prev, testName: testName }))}
                        />
                    </div>

                    <Button
                        icon={CancelMajor}
                        destructive
                        onClick={handleRemoveAll}
                        disabled={checkRemoveAll()}><div data-testid="remove_all_tests">Clear selection</div></Button>
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
                        <div className="testSuiteHorizontalScroll" style={{ display: "flex" }}>
                            <HorizontalStack gap={2}>

                                {

                                    Object.entries(owaspTop10List).map(([key, value]) => (
                                        renderAktoTestSuites({ key, value })
                                    ))
                                }


                            </HorizontalStack>
                        </div>
                    </Collapsible>

                </VerticalStack>
            </VerticalStack>




        </div>
        </Scrollable>
    )
}

export default RunTestSuites