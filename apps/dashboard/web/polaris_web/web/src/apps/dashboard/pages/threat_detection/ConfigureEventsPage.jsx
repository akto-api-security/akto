import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import FilterComponent from "./components/FilterComponent";
import {HorizontalGrid} from "@shopify/polaris";
import jsYaml from 'js-yaml';

function ConfigureEventsPage({ 
    categoryName, 
    pageTitle, 
    pageTooltip, 
    componentTitle 
}) {
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

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={pageTitle}
                tooltipContent={pageTooltip}
            />
        }
        isFirstPage={true}
        components={components}
    />
}

export default ConfigureEventsPage;
