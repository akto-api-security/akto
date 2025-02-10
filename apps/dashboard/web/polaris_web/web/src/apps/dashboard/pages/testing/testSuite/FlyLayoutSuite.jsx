import { Button, HorizontalStack, Text, VerticalStack, Box, Scrollable, Icon, TextField, ResourceList} from "@shopify/polaris"
import {
    CancelMajor, ChevronDownMinor, SearchMinor
} from '@shopify/polaris-icons';
import { useEffect, useState } from "react";
import TestSuiteRow from "./TestSuiteRow";

function FlyLayoutSuite(props) {
    const { show, setShow, width, selectedTestSuite} = props;
    const [testSuiteName, setTestSuiteName] = useState("");
    const [testSearchValue, setTestSearchValue] = useState("");
    const [categories, setCategories] = useState([]);
    const [filteredCategories, setFilteredCategories] = useState([]);
    const [prevSearchValue, setPrevSearchValue] = useState("");

    const handleExit = () => {
        setShow(false);
        setTestSearchValue("");
    }


    useEffect(() => {
        if (selectedTestSuite?.allTest?.length) {
            const deepCopy = JSON.parse(JSON.stringify(selectedTestSuite.allTest));
            setCategories(deepCopy);
            setTestSuiteName(selectedTestSuite.testSuiteName || "");
        } else {
            setCategories([]);
            setTestSuiteName("");
        }
    }, [selectedTestSuite]);
    


    function handleSearch(val) {
        setTestSearchValue(val);
    }


    useEffect(() => {
        let deepCopy = [];
        if (categories && Array.isArray(categories)) {
            deepCopy = JSON.parse(JSON.stringify(categories));
        }
        let updatedCategories = [...deepCopy];
    
        if (testSearchValue.length > 0) {
          updatedCategories = updatedCategories.filter(category => {
            const tests = category.tests.filter(test =>
              test.label.toLowerCase().includes(testSearchValue.toLowerCase())
            );
    
            if (tests.length > 0) {
              if (testSearchValue !== prevSearchValue) {
                category.selected = true;
              }
              else {
                category.selected = category.selected || false;
              }
              category.tests = tests;
              return true;
            } else {
              return false;
            }
          });
        }
        setFilteredCategories(updatedCategories);
        setPrevSearchValue(testSearchValue); 
    }, [testSearchValue, categories]);

    function checkExpand() {
        return filteredCategories.some(category => !category.selected);
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

    let id = 1;
    function renderItem(item) {
        return (
            <TestSuiteRow category={item} setCategories={setCategories} id={id++} setFilteredCategories={setFilteredCategories}/>
        );
    }

    function extendAllHandler() {
        setCategories(prev => {
            return prev.map(category => ({ ...category, selected: true }));
        });
    }
    
    function collapseAllHandler() {
        setCategories(prev => {
            return prev.map(category => ({ ...category, selected: false }));
        });
    }
    

    function totalTestsCount() {
        return categories.reduce((count, category) => count + category.tests.length, 0);
    }
    
    function totalSelectedTestsCount() {
        return filteredCategories.reduce((count, category) => count + category.tests.length, 0);
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

                                <VerticalStack>
                                    <Box borderColor="border-subdued" borderBlockEndWidth="1" background="bg-subdued" padding={4}>
                                        {headingComponents}
                                    </Box>

                                    <div style={{ margin: "20px", borderRadius: "0.5rem", boxShadow: " 0px 0px 5px 0px #0000000D, 0px 1px 2px 0px #00000026" }}>
                                        <Box borderRadius="2" borderColor="border-subdued" >
                                            <Box borderColor="border-subdued" paddingBlockEnd={3} paddingBlockStart={3} paddingInlineStart={5} paddingInlineEnd={5}>
                                                <HorizontalStack align="space-between">
                                                    <HorizontalStack align="start">
                                                        <Text fontWeight="semibold" as="h3">{testSearchValue.length > 0 ? `Showing ${countSearchResults()} result` : `${filteredCategories.length} ${filteredCategories.length>1?'categories':'category'} & ${totalSelectedTestsCount()} tests`}</Text>
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