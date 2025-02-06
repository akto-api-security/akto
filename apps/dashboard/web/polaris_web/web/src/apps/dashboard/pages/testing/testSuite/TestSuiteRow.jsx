import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox, Box } from "@shopify/polaris"
import {
    ChevronDownMinor,
    ChevronUpMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";
import "./flyLayoutSuite.css"

function TestSuiteRow({ category, id, setCategories }) {
    let displayName = category.displayName;
    let subCategories = category.tests;

    const navigate = useNavigate();

    function toggleOpen() {
        setCategories(prev => {
            const updatedCategories = { ...prev };
            Object.values(updatedCategories).forEach(element => {
                if (element.displayName === displayName) {
                    element.selected = !element.selected;
                }
            });

            return Object.values(updatedCategories);
        });
    }

    function renderTest(item) {
        return (
            <ResourceItem style={{ backgroundColor: "rgba(249, 250, 251, 1)" }} onClick={() => navigate(`/dashboard/test-editor/${item.value}`)}>
                <Box paddingInlineStart={5}>
                    <HorizontalStack key={1} align="start">
                        <Text color="subdued" fontWeight="regular" as="h3">{item.label}</Text>
                    </HorizontalStack>
                </Box>
            </ResourceItem>
        )
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
            <div className="sub-category-list-item">
                <Collapsible open={category?.selected}>
                    <ResourceList items={subCategories} renderItem={renderTest}></ResourceList>
                </Collapsible>
            </div>

        </div>
    )
}


export default TestSuiteRow;