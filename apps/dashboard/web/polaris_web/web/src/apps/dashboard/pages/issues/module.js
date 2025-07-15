import issuesApi from "@/apps/dashboard/pages/issues/api"
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import { TextField } from "@shopify/polaris";
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch";

const setCreateJiraIssueFieldMetaData = IssuesStore.getState().setCreateJiraIssueFieldMetaData;
const updateDisplayJiraIssueFieldValues = IssuesStore.getState().updateDisplayJiraIssueFieldValues;

const issuesFunctions = {
    getJiraFieldConfigurations: (field) => {
        const customFieldURI = field?.schema?.custom || "";
        const allowedValues = field?.allowedValues || [];
        const fieldId = field?.fieldId || "";
        const fieldName = field?.name || "";

        const handleFieldChange = (fieldId, value) => {
            updateDisplayJiraIssueFieldValues(fieldId, value)
        } 
       

        switch (customFieldURI) {
            case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
                return {
                    initialValue: "",
                    getComponent: () => {
                        const displayJiraIssueFieldValues = IssuesStore(state => state.displayJiraIssueFieldValues);
                        return (
                            <TextField
                                key={fieldId}
                                label={fieldName}
                                value={displayJiraIssueFieldValues[fieldId] || ""}
                                onChange={(value) => handleFieldChange(fieldId, value)}
                                maxLength={255}
                                showCharacterCount
                                requiredIndicator
                            />)
                    }
                }
            case "com.atlassian.jira.plugin.system.customfieldtypes:select":
            case "com.atlassian.jira.plugin.system.customfieldtypes:multiselect":
            case "com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons": {
                const isMultiSelect = customFieldURI.includes("multiselect");

                const firstAllowedOption = {
                    value: allowedValues.length > 0 ? allowedValues[0].value :  "",
                }
                
                const initialFieldState = isMultiSelect ? [ firstAllowedOption ] : firstAllowedOption;
                const preSelected = [ firstAllowedOption.value ];
              
                return {
                    initialValue: initialFieldState,
                    getComponent: () => {

                        return (
                            <DropdownSearch
                                allowMultiple={customFieldURI.includes("multiselect")}
                                optionsList={allowedValues.map((option) => ({
                                    label: option?.value,
                                    value: option?.value
                                }))}
                                setSelected={(selectedOption) => {
                                    if (customFieldURI.includes("multiselect")) {
                                        const updateFieldValue = selectedOption.map((option) => ({ value: option }));
                                        handleFieldChange(fieldId, updateFieldValue);
                                     } else {
                                        const updateFieldValue = { value: selectedOption };
                                        handleFieldChange(fieldId, updateFieldValue);
                                    }
                                }}
                                value={""}
                                preSelected={preSelected}
                                label={fieldName}
                                placeholder={`Select an option for the field ${fieldName}`}
                                textfieldRequiredIndicator={true}
                            />
                        )
                    } 
                }
            }
            default:
                return {
                    getComponent: () => { return null }
                }
        }
    },
    fetchCreateIssueFieldMetaData: async () => {
        try {
            if(IssuesStore.getState().createJiraIssueFieldMetaData && Object.keys(IssuesStore.getState().createJiraIssueFieldMetaData).length === 0) {
                const response = await issuesApi.fetchCreateJiraIssueFieldMetaData()
                if (response && Object.keys(response).length > 0) {
                    setCreateJiraIssueFieldMetaData(response);
                }
            }else{
                return IssuesStore.getState().createJiraIssueFieldMetaData;
            }
        } catch (error) {
        } 
    },
    prepareAdditionalIssueFields: () => {
        const mandatoryCreateJiraIssueFields = IssuesStore.getState().displayJiraIssueFieldValues;
        const mandatoryCreateJiraIssueFieldsList = Object.keys(mandatoryCreateJiraIssueFields).reduce((acc, fieldId) => {
            acc.push({
                fieldId: fieldId,
                fieldValue: mandatoryCreateJiraIssueFields[fieldId]
            });
            return acc;
        }, []);

        const additionalIssueFields = { mandatoryCreateJiraIssueFields: mandatoryCreateJiraIssueFieldsList }
        return additionalIssueFields;
    },
    prepareAdditionalIssueFieldsJiraMetaData: () => {
        const additionalIssueFields = issuesFunctions.prepareAdditionalIssueFields();
        const jiraMetaData = { additionalIssueFields: additionalIssueFields };
        return jiraMetaData;
    }
}

export default issuesFunctions