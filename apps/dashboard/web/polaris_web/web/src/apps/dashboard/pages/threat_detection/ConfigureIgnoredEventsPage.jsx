import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import FilterComponent from "./components/FilterComponent";
import {HorizontalGrid} from "@shopify/polaris";
import jsYaml from 'js-yaml';

function ConfigureIgnoredEventsPage() {


    const validateIgnoredEventCategory = (data) => {
        try {
            // Parse the YAML content
            const yamlContent = jsYaml.load(data);

            // Check if the category name is IgnoredEvent
            const categoryName = yamlContent?.info?.category?.name;

            if (!categoryName) {
                return {
                    isValid: false,
                    errorMessage: "Category name is missing. Please ensure 'info.category.name' is defined in the YAML."
                };
            }

            if (categoryName !== "IgnoredEvent") {
                return {
                    isValid: false,
                    errorMessage: `Category name must be 'IgnoredEvent' for ignored event configurations. Current value: '${categoryName}'`
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
            includeCategoryNameEquals={"IgnoredEvent"}
            titleText={"Ignored events"}
            readOnly={false}
            validateOnSave={validateIgnoredEventCategory}
        />
    </HorizontalGrid>

    const components = [
        horizontalComponent,
    ]

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"Configure Ignored Events"}
                tooltipContent={"Manage ignored event configurations to filter out non-threatening activities"}
            />
        }
        isFirstPage={true}
        components={components}
    />
}

export default ConfigureIgnoredEventsPage;
