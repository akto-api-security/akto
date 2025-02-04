import { Button, HorizontalStack, Text, VerticalStack, Box, Spinner, Divider, Scrollable, Icon, TextField, IndexFiltersMode, ResourceList, Collapsible, ResourceItem, Checkbox } from "@shopify/polaris"
import {
    CancelMajor, ChevronDownMinor, SearchMinor
} from '@shopify/polaris-icons';
import { useEffect, useState } from "react";
import transform from "../../testing/transform";
import LocalStore from "../../../../main/LocalStorageStore";
import TestSuiteRow from "./TestSuiteRow";
import func from "../../../../../util/func";


function FlyLayoutSuite(props) {
    const {show, setShow, width, selectedTestSuite,setSelectedTestSuite} = props;
    const [testSuiteName, setTestSuiteName] = useState("");
    const [testSearchValue, setTestSearchValue] = useState("");
    const [categories, setCategories] = useState([]);

    const handleExit = () => {
        setSelectedTestSuite(null)
        setShow(false);
    }
    useEffect(() => {
        if (selectedTestSuite) {
            setCategories(prev => {
                const selectedTestSuiteTests = new Set(selectedTestSuite.tests)
                const updatedCategories = prev.map(category => ({
                    ...category,
                    selected: false, 
                    tests: category.tests.map(test => ({
                        ...test, 
                        selected: selectedTestSuiteTests.has(test.value)? true : false 
                    }))
                }));
                return updatedCategories;
            });
            setTestSuiteName(selectedTestSuite.name);
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


    const fetchData = async () => {
        console.log("fetching data")
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



    const handleTestSuiteSave = () => {
        if(!testSuiteName || testSuiteName === "") {
            func.setToast(true, true, "Test Suite Name cannot be empty");
            return ;
        }
        const selectedTestSuiteTests = [];
        const updatedCategories = [...categories];
        updatedCategories.forEach(element => {
            element.tests.forEach(test => {
                if (test.selected === true) {
                    selectedTestSuiteTests.push(test.value);
                }
            });

        });

        let newSuite = {
            name: testSuiteName,
            id: testSuiteName,
            tests: selectedTestSuiteTests,
            testCount: selectedTestSuiteTests.length
        }
        console.log("done", newSuite)
        LocalStore.getState().addCustomTestSuite(newSuite);
        handleExit();
        // window.location.reload();

    }

    function handleSearch(val) {
        setTestSearchValue(val);
    }

    let filteredCategories = [...categories];
    if (testSearchValue.length > 0) {
        filteredCategories = categories.filter(category => {
            if (category.displayName.toLowerCase().includes(testSearchValue.toLowerCase())) return true;
            else {
                let tests = category.value.tests.filter(test => test.label.toLowerCase().includes(testSearchValue.toLowerCase()));
                if (tests.length > 0) return true;
                else return false;
            }
        })
    }


    const headingComponents = (
        <HorizontalStack align="space-between">
            <div style={{ width: "40%" }}>
                <TextField value={testSuiteName} onChange={(val) => setTestSuiteName(val)} label="Test Suite Name" placeholder="Test_suite_name" />
            </div>
            <div style={{ width: "57%", paddingTop: "1.5rem" }}>
                <TextField value={testSearchValue} onChange={(val) => { handleSearch(val) }} prefix={<Icon source={SearchMinor} />} placeholder="Search" />
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
            let check = false;
            Object.values(updatedCategories).forEach(element => {
                if (element.selected) check = true;
            });
            Object.values(updatedCategories).forEach(element => {
                element.selected = check ? false : true;
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

    function totalTestCountCheck() {
        let total = totalTestsCount();
        let selected = totalSelectedTestsCount();
        if (selected === 0) return false;
        else if (total === selected) return true;
        else return "indeterminate";
    }

    function handleTotalSelected() {
        setCategories(prev => {
            const updatedCategories = [...prev];
            let someSelected = false;
            updatedCategories.some(element => {
                element.tests.some(test => test.selected) ? someSelected = true : someSelected = false;
            })

            updatedCategories.forEach(element => {
                element.tests = element.tests.map(test => ({
                    ...test,
                    selected: someSelected ? false : true
                }));

            });

            return updatedCategories;
        });
    }

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: divWidth }}>
            <div className="innerFlyLayout">
                <Box borderColor="border-subdued" borderWidth="1" background="bg" width={divWidth} minHeight="100%">
                    <div style={{ position: "absolute", right: "25vw", top: "50vh" }}></div> :
                        <VerticalStack gap={"5"}>
                            <Box padding={"4"} paddingBlockEnd={"0"} >
                                <HorizontalStack align="space-between">

                                    <Text variant="headingMd">
                                        {"New Test Suite"}
                                    </Text>

                                    <Button icon={CancelMajor} onClick={() => { handleExit() }} plain></Button>
                                </HorizontalStack>
                            </Box>
                            <Box minHeight="82vh">
                                <Scrollable style={{ maxHeight: "82vh" }} shadow>

                                    <VerticalStack>
                                        <Box borderColor="border-subdued" borderBlockStartWidth="1" borderBlockEndWidth="1" background="bg-subdued" padding={4}>
                                            {headingComponents}
                                        </Box>
                                        <Box borderColor="border-subdued" borderBlockEndWidth="1" paddingBlockEnd={3} paddingBlockStart={3} paddingInlineStart={5} paddingInlineEnd={5}>
                                            <HorizontalStack align="space-between">
                                                <HorizontalStack align="start">
                                                    <Checkbox checked={totalTestCountCheck()} onChange={() => { handleTotalSelected() }} />
                                                    <Text fontWeight="semibold" as="h3">{`${totalSelectedTestsCount()} tests selected`}</Text>
                                                </HorizontalStack>
                                                <Button onClick={() => { extendAllHandler() }} plain><Text>Extend All</Text></Button>
                                            </HorizontalStack>
                                        </Box>
                                        <ResourceList items={filteredCategories} renderItem={renderItem} >
                                        </ResourceList>
                                    </VerticalStack>

                                </Scrollable>
                            </Box>
                            <HorizontalStack align="start">

                                <Button primary onClick={() => { handleTestSuiteSave();}}>Save</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    
                </Box>

            </div>
        </div>
    )
}


export default FlyLayoutSuite;