import { Button, HorizontalStack, Text, ResourceList, Collapsible, ResourceItem, Checkbox } from "@shopify/polaris"
import {
    ChevronDownMinor
} from '@shopify/polaris-icons';

function TestSuiteRow({ category, id, setCategories }) {
    let displayName = category.displayName;
    let subCategories = category.tests;

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

    function selectTest(item) {
        setCategories(prev => {
            const updatedCategories = [...prev];
            updatedCategories.forEach(element => {
                element.tests = element.tests.map(test => {
                    if (item.label === test.label) {
                        return { ...test, selected: !test.selected };
                    }
                    else return { ...test };
                });

            });

            return updatedCategories;
        });
    }

    function checkboxTest(item) {
        let selected = true;
        category.tests.forEach(element => {
            if (element.label === item.label && !element.selected) {
                selected = false;
            }
        });
        return selected;
    }

    function renderTest(item) {
        return (
            <ResourceItem>
                <HorizontalStack key={1} align="start">
                    <Checkbox checked={checkboxTest(item)} onChange={() => { selectTest(item) }} />
                    <Text fontWeight="regular" as="h3">{item.label}</Text>
                </HorizontalStack>
            </ResourceItem>)
    }

    function selectTestCategory() {
        setCategories(prev => {
            const updatedCategories = [...prev];
            updatedCategories.forEach(element => {
                let someSelected = element.tests.some(test => test.selected);
                let countTestSelected = element.tests.filter(test => test.selected).length;

                if (category.displayName === element.displayName) {
                    element.tests = element.tests.map(test => ({
                        ...test,
                        selected: someSelected ? false : true
                    }));

                }
            });
            return updatedCategories;
        });
    }

    function checkboxSelected() {
        let atleastOne = false;
        let allSelected = true;

        category.tests.forEach(element => {
            if (!element.selected) {
                allSelected = false;
            }
            if (element.selected) {
                atleastOne = true;
            }
        });
        if (atleastOne && allSelected) return true;
        else if (atleastOne) return "indeterminate";
        else return false;
    }

    function countSelectedCategoryTest() {
        let count = 0;
        category.tests.forEach(element => {
            if (element.selected) count++;
        });
        return count;
    }



    return (
        <ResourceItem id={id}>
            <HorizontalStack align="space-between">
                <HorizontalStack >
                    <Checkbox checked={checkboxSelected()} onChange={() => selectTestCategory()}></Checkbox>
                    <Text fontWeight="regular" as="h3">{displayName}</Text>
                </HorizontalStack>

                <HorizontalStack gap={4}>
                    <span style={{ color: "#6D7175" }}>{`${countSelectedCategoryTest()}/${category.tests.length}`}</span>
                    <Button onClick={() => { toggleOpen() }} plain size="micro" icon={ChevronDownMinor}></Button>
                </HorizontalStack>
            </HorizontalStack>
            <Collapsible open={category?.selected}>
                <ResourceList items={subCategories} renderItem={renderTest}></ResourceList>
            </Collapsible>
        </ResourceItem>
    )
}


export default TestSuiteRow;