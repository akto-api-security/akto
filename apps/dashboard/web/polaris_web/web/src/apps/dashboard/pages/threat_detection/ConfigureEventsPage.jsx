import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import FilterComponent from "./components/FilterComponent";
import {HorizontalGrid, Button, Modal, Text} from "@shopify/polaris";
import jsYaml from 'js-yaml';
import { useState } from 'react';
import trafficFiltersRequest from '../settings/traffic-conditions/api';

function ConfigureEventsPage({
    categoryName,
    pageTitle,
    pageTooltip,
    componentTitle
}) {
    const [topLevelActive, setTopLevelActive] = useState(false);

    const handleRetrospectiveApply = async() => {
        if(window.USER_NAME.includes("akto")) {
            if(topLevelActive) {
                await trafficFiltersRequest.cleanUpInventory(shouldDelete).then((res) => {
                    console.log("Apply policy response", res)
                })
            }
        }
    }

    const validateCategory = (data) => {
        try {
            // Parse the YAML content
            const yamlContent = jsYaml.load(data);

            // Check if the category name matches
            const yamlCategoryName = yamlContent?.info?.category?.name;

            if (!yamlCategoryName) {
                return {
                    isValid: false,
                    errorMessage: "Category name is missing. Please ensure 'info.category.name' is defined in the YAML."
                };
            }

            if (yamlCategoryName !== categoryName) {
                return {
                    isValid: false,
                    errorMessage: `Category name must be '${categoryName}' for ${componentTitle.toLowerCase()} configurations. Current value: '${yamlCategoryName}'`
                };
            }

            return { isValid: true };

        } catch (error) {
            return {
                isValid: false,
                errorMessage: `Invalid YAML format: ${error.message}`
            };
        }
    };

    const horizontalComponent = <HorizontalGrid columns={1} gap={2}>
        <FilterComponent
            key={"filter-component"}
            includeCategoryNameEquals={categoryName}
            titleText={componentTitle}
            readOnly={false}
            validateOnSave={validateCategory}
        />
    </HorizontalGrid>

    const components = [
        horizontalComponent,
    ]

    return <>
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={pageTitle}
                    tooltipContent={pageTooltip}
                />
            }
            isFirstPage={true}
            components={components}
            primaryAction={window.USER_NAME && window.USER_NAME.endsWith("@akto.io") ? <Button onClick={() => setTopLevelActive(true)}>Apply Policy</Button> : null}
        />
        <Modal
            open={topLevelActive}
            onClose={() => setTopLevelActive(false)}
            secondaryActions={[{content: 'Apply', onAction: () => handleRetrospectiveApply(true)}]}
            title={"Apply Policy on existing"}
        >
            <Modal.Section>
                <Text variant="bodyMd" color="subdued">
                    This will apply the policy to the existing malicious events.
                    Are you sure you want to proceed?
                </Text>
            </Modal.Section>
        </Modal>
    </>
}

export default ConfigureEventsPage;
