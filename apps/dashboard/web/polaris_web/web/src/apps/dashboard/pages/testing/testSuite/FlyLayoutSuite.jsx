import { Button, HorizontalStack, Text, VerticalStack, Box, Spinner, Divider, Scrollable, Icon, TextField, IndexFiltersMode, ResourceList, Collapsible, ResourceItem, Checkbox } from "@shopify/polaris"
import {
    CancelMajor, ChevronDownMinor, SearchMinor
} from '@shopify/polaris-icons';
import { useEffect, useRef, useState } from "react";
import transform from "../../testing/transform";
import TestSuiteRow from "./TestSuiteRow";

function FlyLayoutSuite(props) {
    const { show, setShow, width, selectedTestSuite, setSelectedTestSuite} = props;
    const [testSuiteName, setTestSuiteName] = useState("");
    const [testSearchValue, setTestSearchValue] = useState("");
    const [categories, setCategories] = useState([]);

    const handleExit = () => {
        setShow(false);
        setTestSearchValue("");
    }

    const fetchData = async () => {
        let metaDataObj = {
            categories: [],
            subCategories: [],
            testSourceConfigs: []
        }

        metaDataObj = await transform.getAllSubcategoriesData(true, "runTests")
        const subCategoryMap = {};
        metaDataObj.subCategories.forEach(subCategory => {
            if (!subCategoryMap[subCategory?.superCategory?.displayName]) {
                subCategoryMap[subCategory.superCategory?.displayName] = [];
            }
            let obj = {
                label: subCategory.testName,
                value: subCategory.name,
                author: subCategory.author,
                selected: false
            }
            subCategoryMap[subCategory.superCategory?.displayName].push(obj);
        });
        let cat = [];
        Object.entries(subCategoryMap).forEach(([key, tests]) => { cat.push({ displayName: key, tests, selected: false }) });
        setCategories(cat)
    }

    useEffect(() => {
        fetchData();
    }, []);



    useEffect(() => {
        if (selectedTestSuite) {
            setCategories(prev => {
                const selectedTestSuiteTests = new Set(selectedTestSuite.tests)
                const updatedCategories = prev.map(category => ({
                    ...category,
                    selected: false,
                    tests: category.tests.map(test => ({
                        ...test,
                        selected: selectedTestSuiteTests.has(test.value) ? true : false
                    }))
                }));
                return updatedCategories;
            });

            setTestSuiteName(selectedTestSuite.testSuiteName);
        }
        else {
            setCategories(prev => {
                const updatedCategories = prev.map(category => ({
                    ...category,
                    selected: false,
                    tests: category.tests.map(test => ({
                        ...test,
                        selected: false
                    }))
                }));
                return updatedCategories;
            });

            setTestSuiteName("");
        }
        
    }, [selectedTestSuite]);


    function handleSearch(val) {
        setTestSearchValue(val);
    }

    let filteredCategories = JSON.parse(JSON.stringify(categories));;
    if (testSearchValue.length > 0) {
        filteredCategories = filteredCategories.filter(category => {
            let check = category.tests.some(test => test.selected);
            return check;
        });
        filteredCategories = filteredCategories.filter(category => {
            let tests = category.tests.filter(test => test.label.toLowerCase().includes(testSearchValue.toLowerCase()));
            if (tests.length > 0) {
                category.selected = true;
                category.tests = tests;
                return true;
            }
            else {
                return false;
            }

        })
    }
    else {
        filteredCategories = filteredCategories.filter(category => {
            let check = category.tests.some(test => test.selected);
            return check;
        });
    }

    function checkExpand() {
        let check = false;
        filteredCategories.forEach(category => {
            if (!category.selected) check = true;
        });
        return check;

    }
    const countSearchResults = () => {
        let count = 0;
        filteredCategories.forEach(category => { count += category.tests.length });
        return count;
    }

    const setSearchVal = (val) => { 
        handleSearch(val);
    };
 
    const headingComponents = (
        <HorizontalStack align="space-between">
            <div style={{ width: "40%" }}>
                <TextField disabled={true} value={testSuiteName} onChange={(val) => setTestSuiteName(val)} label="Test Suite Name" placeholder="Test_suite_name" />
            </div>
            <div style={{ width: "58%", paddingTop: "1.5rem" }}>
                <TextField value={testSearchValue} onChange={(val) => { setSearchVal(val) }} prefix={<Icon source={SearchMinor} />} placeholder="Search" />
            </div>
        </HorizontalStack>

    )


    const divWidth = width || "50vw";

    function renderItem(item) {
        let id = 1;
        return (
            <TestSuiteRow category={item} setCategories={setCategories} id={id} />
        );
    }

    function extendAllHandler() {
        setCategories(prev => {
            const updatedCategories = { ...prev };
            Object.values(updatedCategories).forEach(element => {
                element.selected = true;
            });
            return Object.values(updatedCategories);
        });
    }

    function collapseAllHandler() {
        setCategories(prev => {
            const updatedCategories = { ...prev };
            Object.values(updatedCategories).forEach(element => {
                element.selected = false;
            });
            return Object.values(updatedCategories);
        });

    }

    function totalTestsCount() {
        let count = 0;
        if (!categories) return count;
        const updatedCategories = [...categories];
        updatedCategories.forEach(element => {
            count += element.tests.length;
        })
        return count;
    }

    function totalSelectedTestsCount() {
        let count = 0;
        if (!categories) return count;
        const updatedCategories = [...categories];
        updatedCategories.forEach(element => {
            element.tests.forEach(test => {
                if (test?.selected) count++;
            });
        });
        return count;
    }


    return (
        <div className={"flyLayoutSuite " + (show ? "show" : "")} style={{ width: divWidth }}>
            <div className="innerFlyLayout">
                <Box borderColor="border-subdued" borderWidth="1" background="bg" width={divWidth} minHeight="100%">
                    <VerticalStack>
                        <Box borderColor="border-subdued" borderBlockEndWidth="1" paddingBlockStart={4} paddingBlockEnd={4} paddingInlineStart={5} paddingInlineEnd={5}>
                            <HorizontalStack align="space-between">

                                <Text variant="headingMd">
                                    {"Test Suite Details"}
                                </Text>

                                <Button icon={CancelMajor} onClick={() => { handleExit() }} plain></Button>
                            </HorizontalStack>
                        </Box>
                        <Box paddingBlockEnd={5}>
                            <Scrollable style={{ height: "90vh" }}>

                                <VerticalStack gap={4}>
                                    <Box borderColor="border-subdued" borderBlockEndWidth="1" background="bg-subdued" padding={4}>
                                        {headingComponents}
                                    </Box>

                                    <div style={{ margin: "20px", borderRadius: "0.5rem", boxShadow: " 0px 0px 5px 0px #0000000D, 0px 1px 2px 0px #00000026" }}>
                                        <Box borderRadius="2" borderColor="border-subdued" >
                                            <Box borderColor="border-subdued" paddingBlockEnd={3} paddingBlockStart={3} paddingInlineStart={5} paddingInlineEnd={5}>
                                                <HorizontalStack align="space-between">
                                                    <HorizontalStack align="start">
                                                        <Text fontWeight="semibold" as="h3">{testSearchValue.length > 0 ? `Showing ${countSearchResults()} result` : `${totalSelectedTestsCount()} tests & ${filteredCategories.length} category`}</Text>
                                                    </HorizontalStack>
                                                    {testSearchValue.trim().length === 0 ? <Button onClick={() => { checkExpand() ? extendAllHandler() : collapseAllHandler() }} plain><Text>{checkExpand() ? "Expand all" : "Collapse all"}</Text></Button> : <></>}
                                                </HorizontalStack>
                                            </Box>

                                            <ResourceList items={filteredCategories} renderItem={renderItem} />
                                        </Box>
                                    </div>
                                    <div style={{height:"20px"}}></div>
                                </VerticalStack>

                            </Scrollable>
                        </Box>
                    </VerticalStack>
                </Box>

            </div>
        </div>
    )
}


export default FlyLayoutSuite;