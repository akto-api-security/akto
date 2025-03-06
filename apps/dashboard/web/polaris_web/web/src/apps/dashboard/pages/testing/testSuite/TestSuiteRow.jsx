import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox, Box, Link } from "@shopify/polaris"
import {
    ChevronDownMinor,
    ChevronRightMinor,
    ChevronUpMinor
} from '@shopify/polaris-icons';
import "./flyLayoutSuite.css"

function TestSuiteRow({ category, categories, setCategories, isLast, isEditMode, filteredCategories }) {
    let displayName = category.displayName;
    let subCategories = category.tests;

    function toggleOpen() {
        setCategories(prev => 
            prev.map(cat => 
                cat.displayName === displayName ? { ...cat, selected: !cat.selected } : cat
            )
        );
    }

    function changeTestSelection(subCategory) {
        setCategories(prev => {
            const updatedCategories = [...prev];
            updatedCategories.forEach(element => {
                if (element.displayName === subCategory.categoryName) {
                    element.tests.forEach(test => {
                        if (test.value === subCategory.value) {
                            test.selected = !test.selected;
                        }
                    });
                }
            });
            return updatedCategories
        });
    }

    function checkSubCategorySelected() {
        let atleastOne = false;
        let allSelected = true;

        const updatedCategories = [...filteredCategories];
        updatedCategories.forEach(element => {
            if (element.displayName === displayName) {
                element.tests.forEach(test => {
                    if (!test.selected) {
                        allSelected = false;
                    }
                    if (test.selected) {
                        atleastOne = true;
                    }
                });
            }
        });
        if (atleastOne && allSelected) return true;
        else if (atleastOne) return "indeterminate";
        else return false;
    }

    function changeSubCategorySelection() {
        setCategories(prev => {
            const updatedCategories = [...prev];

            let someSelected = false;
            updatedCategories.forEach(element => {
                if (element.displayName === displayName) {
                    element.tests.some(test => {
                        if (test.selected) {
                            someSelected = true;
                        }
                    });
                }
            });
            
            const selectedFromFilterCategories = new Set();
            filteredCategories.forEach(element => {
                if (element.displayName === displayName) {
                    element.tests.forEach(test => {
                        selectedFromFilterCategories.add(test.value);
                    });
                }
            });

            updatedCategories.forEach(element => {
                if (element.displayName === displayName) {
                    element.tests.forEach(test => {
                        if(selectedFromFilterCategories.has(test.value))
                        test.selected = someSelected ? false : true;
                    });
                }
            });
            return updatedCategories;
        }
        );
    }

    function countSelectedTestForCategory() {
        let count = 0;
        filteredCategories.forEach(element => {
            if (element.displayName === displayName) {
                element.tests.forEach(test => {
                    if (test.selected) {
                        count++;
                    }
                });
            }
        }
        );
        return count;
    }

    return (
        <Box borderRadiusEndEnd={(isLast) ? 2 : 0} borderRadiusEndStart={(isLast) ? 2 : 0} borderColor="border-subdued" borderBlockStartWidth="1" >
            <div className="category-list" style={{ cursor: "pointer", ...(isLast && !category.selected && { borderBottomLeftRadius: "0.5rem", borderBottomRightRadius: "0.5rem" }) }}>
                <Box paddingInlineStart={5} paddingBlockEnd={3} paddingBlockStart={3} paddingInlineEnd={5}>
                    <HorizontalStack wrap="false">
                        {isEditMode?<Checkbox checked={checkSubCategorySelected()} onChange={() => { changeSubCategorySelection() }} />:null}
                        <div onClick={toggleOpen} style={{display:"flex",flex:"1", alignContent:"center" ,justifyContent:"space-between"}} >
                            <HorizontalStack>
                                <Text fontWeight="medium" as="h3">{displayName}</Text>
                            </HorizontalStack>                  
                            <HorizontalStack gap={4}>
                                <span style={{ color: "#6D7175" }}>{isEditMode?`${countSelectedTestForCategory()}/${category.tests.length}`:`${category.tests.length}`}</span>
                                <Button plain monochrome size="micro" icon={category.selected ? ChevronDownMinor : ChevronRightMinor}></Button>
                            </HorizontalStack>
                        </div>
                    </HorizontalStack>
                </Box>
            </div>

            <Collapsible open={category?.selected}>
                {subCategories.map((subCategory, index) => {
                    return (
                        <div style={{ backgroundColor: "#FAFBFB", cursor: "pointer", ...(isLast && subCategories.length - 1 === index && { borderBottomLeftRadius: "0.5rem", borderBottomRightRadius: "0.5rem" }) }} className="category-lists-item" key={index}>
                            <Box borderColor="border-subdued" borderBlockStartWidth="1" paddingInlineStart={10} paddingBlockEnd={2} paddingBlockStart={2} >
                                <HorizontalStack wrap={false} key={1} align="start">
                                    {isEditMode ? <Checkbox checked={subCategory.selected} onChange={() => { changeTestSelection(subCategory) }} /> : null}
                                    <div onClick={() => window.open(`${window.location.origin}/dashboard/test-editor/${subCategory.value}`)} style={{overflow: "hidden", paddingInlineEnd:"1rem"}}>
                                        <Text color="subdued" fontWeight="regular" as="h3" truncate>{subCategory.label}</Text>
                                    </div>
                                </HorizontalStack>
                            </Box>
                        </div>
                    )
                })}
            </Collapsible>
        </Box>
    )
}


export default TestSuiteRow;