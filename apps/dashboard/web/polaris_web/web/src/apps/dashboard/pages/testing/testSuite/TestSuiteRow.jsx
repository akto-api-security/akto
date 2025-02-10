import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox, Box } from "@shopify/polaris"
import {
    ChevronDownMinor,
    ChevronUpMinor
} from '@shopify/polaris-icons';
import "./flyLayoutSuite.css"

function TestSuiteRow({ category, id, setCategories, setFilteredCategories }) {
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
        <div className="category-list-item " style={{ borderTop: "0.5px solid rgba(221, 224, 228, 1)" }}>
            <ResourceItem onClick={() => { toggleOpen() }} id={id}>
                <HorizontalStack align="space-between">
                    <HorizontalStack >
                        <Text fontWeight="medium" as="h3">{displayName}</Text>
                    </HorizontalStack>

                    <HorizontalStack gap={4}>
                        <span style={{ color: "#6D7175" }}>{`${category.tests.length}`}</span>
                        <Button plain size="micro" icon={category.selected ? ChevronUpMinor : ChevronDownMinor}></Button>
                    </HorizontalStack>
                </HorizontalStack>
            </ResourceItem>
            <div className="sub-category-lists">
            <Collapsible open={category?.selected}>
                {subCategories.map((subCategory) => {
                    return (
                        <div style={{backgroundColor:" #FAFBFB"}} className="sub-category-lists-item" onClick={() => window.open(`${window.location.origin}/dashboard/test-editor/${subCategory.value}`)}>
                        <Box borderColor="border-subdued" borderBlockStartWidth="1" paddingInlineStart={10} paddingBlockEnd={2} paddingBlockStart={2} >
                            <HorizontalStack key={1} align="start">
                                <Text color="subdued" fontWeight="regular" as="h3">{subCategory.label}</Text>
                            </HorizontalStack>
                        </Box>
                        </div>
                    )
                })}
                </Collapsible>
            </div>

        </div>
    )
}


export default TestSuiteRow;