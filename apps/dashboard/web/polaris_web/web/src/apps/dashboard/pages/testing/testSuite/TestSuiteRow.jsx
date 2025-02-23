import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox, Box, Link } from "@shopify/polaris"
import {
    ChevronDownMinor,
    ChevronUpMinor
} from '@shopify/polaris-icons';
import "./flyLayoutSuite.css"

function TestSuiteRow({ category, setFilteredCategories, categories, setCategories, isLast, isEditMode, filteredCategories }) {
    let displayName = category.displayName;
    let subCategories = category.tests;

    function toggleOpen() {
        setFilteredCategories(prev => 
            prev.map(cat => 
                cat.displayName === displayName ? { ...cat, selected: !cat.selected } : cat
            )
        );
    }

    function changeTestSelection(subCategory) {
        setCategories(prev => {
            const updatedCategories = [...prev];
            const helperMap = {};
            filteredCategories.forEach(element => {
                helperMap[element.displayName] = element.selected;
            });
            updatedCategories.forEach(element => {
                if (element.displayName === subCategory.categoryName) {
                    element.tests.forEach(test => {
                        if (test.value === subCategory.value) {
                            test.selected = !test.selected;
                        }
                    });
                }
                element.selected = helperMap[element.displayName];
            });
            console.log(updatedCategories);
            return updatedCategories
        });
    }

    function checkSubCategorySelected() {
        let atleastOne = false;
        let allSelected = true;

        const updatedCategories = [...categories];
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

    function ChangeSubCategorySelection() {
        setCategories(prev => {
            const updatedCategories = [...prev];

            const helperMap = {};
            filteredCategories.forEach(element => {
                helperMap[element.displayName] = element.selected;
            });

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
            updatedCategories.forEach(element => {
                if (element.displayName === displayName) {
                    element.tests.forEach(test => {
                        test.selected = someSelected ? false : true;
                    });
                }
                element.selected = helperMap[element.displayName];
            });
            return updatedCategories;
        }
        );
    }

    return (
        <Box borderRadiusEndEnd={(isLast) ? 2 : 0} borderRadiusEndStart={(isLast) ? 2 : 0} borderColor="border-subdued" borderBlockStartWidth="1" >
            <div className="category-list" style={{ cursor: "pointer", ...(isLast && !category.selected && { borderBottomLeftRadius: "0.5rem", borderBottomRightRadius: "0.5rem" }) }}>
                <Box paddingInlineStart={5} paddingBlockEnd={3} paddingBlockStart={3} paddingInlineEnd={5}>
                    <div style={{ display: "flex" }}>
                        {isEditMode?<Checkbox checked={checkSubCategorySelected()} onChange={() => { ChangeSubCategorySelection() }} />:null}
                        <div onClick={toggleOpen} style={{display:"flex", alignContent:"center" ,justifyContent:"space-between", ...(isEditMode?{minWidth:"96%"}:{minWidth:"100%"})}} >
                            <HorizontalStack>
                                <Text fontWeight="medium" as="h3">{displayName}</Text>
                            </HorizontalStack>                  
                            <HorizontalStack gap={4}>
                                <span style={{ color: "#6D7175" }}>{`${category.tests.length}`}</span>
                                <Button plain monochrome size="micro" icon={category.selected ? ChevronUpMinor : ChevronDownMinor}></Button>
                            </HorizontalStack>
                        </div>
                    </div>
                </Box>
            </div>

            <Collapsible open={category?.selected}>
                {subCategories.map((subCategory, index) => {
                    return (
                        <div style={{ backgroundColor: "#FAFBFB", cursor: "pointer", ...(isLast && subCategories.length - 1 === index && { borderBottomLeftRadius: "0.5rem", borderBottomRightRadius: "0.5rem" }) }} className="category-lists-item" key={index}>
                            <Box borderColor="border-subdued" borderBlockStartWidth="1" paddingInlineStart={10} paddingBlockEnd={2} paddingBlockStart={2} >
                                <HorizontalStack key={1} align="start">
                                    {isEditMode ? <Checkbox checked={subCategory.selected} onChange={() => { changeTestSelection(subCategory) }} /> : null}
                                    <div onClick={() => window.open(`${window.location.origin}/dashboard/test-editor/${subCategory.value}`)}>
                                        <Text color="subdued" fontWeight="regular" as="h3">{subCategory.label}</Text>
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