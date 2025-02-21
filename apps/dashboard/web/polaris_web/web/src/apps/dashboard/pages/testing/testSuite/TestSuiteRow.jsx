import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox, Box, Link } from "@shopify/polaris"
import {
    ChevronDownMinor,
    ChevronUpMinor
} from '@shopify/polaris-icons';
import "./flyLayoutSuite.css"

function TestSuiteRow({ category, setFilteredCategories, isLast }) {
    let displayName = category.displayName;
    let subCategories = category.tests;

    function toggleOpen() {
        setFilteredCategories(prev => {
            const updatedCategories = { ...prev };
            Object.values(updatedCategories).forEach(element => {
                if (element.displayName === displayName) {
                    element.selected = !element.selected;
                }
            });
            return Object.values(updatedCategories);
        });
    }


    return (
        <Box borderRadiusEndEnd={(isLast) ? 2 : 0} borderRadiusEndStart={(isLast) ? 2 : 0} borderColor="border-subdued" borderBlockStartWidth="1" >
            <div className="category-list" style={{ cursor: "pointer", ...(isLast && !category.selected && { borderBottomLeftRadius: "0.5rem", borderBottomRightRadius: "0.5rem" }) }}>
                <Box onClick={() => { toggleOpen() }} paddingInlineStart={5} paddingBlockEnd={3} paddingBlockStart={3} paddingInlineEnd={5}>

                    <HorizontalStack align="space-between">
                        <HorizontalStack >
                            <Text fontWeight="medium" as="h3">{displayName}</Text>
                        </HorizontalStack>
                        <HorizontalStack gap={4}>
                            <span style={{ color: "#6D7175" }}>{`${category.tests.length}`}</span>
                            <Button plain monochrome size="micro" icon={category.selected ? ChevronUpMinor : ChevronDownMinor}></Button>
                        </HorizontalStack>
                    </HorizontalStack>

                </Box>
            </div>
            <div className="sub-category-lists">
                <Collapsible open={category?.selected}>
                    {subCategories.map((subCategory, index) => {
                        return (

                            <Box background="bg-subdued" borderRadiusEndEnd={(isLast && subCategories.length - 1 === index) ? 2 : 0} borderRadiusEndStart={(isLast && subCategories.length - 1 === index) ? 2 : 0} borderColor="border-subdued" borderBlockStartWidth="1" paddingInlineStart={10} paddingBlockEnd={2} paddingBlockStart={2} >
                                <HorizontalStack key={1} align="start">
                                    <Link onClick={() => window.open(`${window.location.origin}/dashboard/test-editor/${subCategory.value}`)} monochrome removeUnderline >
                                        <Text color="subdued" fontWeight="regular" as="h3">{subCategory.label}</Text>
                                    </Link>
                                </HorizontalStack>
                            </Box>
                        )
                    })}
                </Collapsible>
            </div>
        </Box>
    )
}


export default TestSuiteRow;