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
        const isFieldRequired = field?.required || false;
        const hasDefaultValue = field?.hasDefaultValue || false;
        const defaultValue = field?.defaultValue || null;

        const handleFieldChange = (fieldId, value) => {
            updateDisplayJiraIssueFieldValues(fieldId, value)
        } 

        switch (customFieldURI) {
            case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
                return {
                    initialValue: hasDefaultValue ? defaultValue : "",
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
                                requiredIndicator={isFieldRequired}
                            />)
                    }
                }
            case "com.atlassian.jira.plugin.system.customfieldtypes:select":
            case "com.atlassian.jira.plugin.system.customfieldtypes:multiselect":
            case "com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes":
            case "com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons": {
                const processDefaults = (defaultValue, isMultiSelect, emptyInitialValue) => { 
                    if (isMultiSelect) {
                        return Array.isArray(defaultValue)
                            ? defaultValue.map(item => ({ value: item.value }))
                            : emptyInitialValue;
                    } else {
                        return defaultValue?.value ? { value: defaultValue.value } : emptyInitialValue
                    }
                }
                
                const isMultiSelect = customFieldURI.includes("multiselect") || customFieldURI.includes("multicheckboxes");
                const emptyInitialValue = isMultiSelect ? [] : null;
                const initialFieldState = hasDefaultValue ? processDefaults(defaultValue, isMultiSelect, emptyInitialValue) : emptyInitialValue;
                const preSelected = Array.isArray(initialFieldState) ? initialFieldState.map(item => item.value) : [ initialFieldState?.value ]
                
                return {
                    initialValue: initialFieldState,
                    getComponent: () => {

                        return (
                            <DropdownSearch
                                allowMultiple={isMultiSelect}
                                optionsList={allowedValues.map((option) => ({
                                    label: option?.value,
                                    value: option?.value
                                }))}
                                setSelected={(selectedOption) => {
                                    if (isMultiSelect) {
                                        const updateFieldValue = selectedOption.map((option) => ({ value: option }));
                                        handleFieldChange(fieldId, updateFieldValue);
                                     } else {
                                        const updateFieldValue = { value: selectedOption };
                                        handleFieldChange(fieldId, updateFieldValue);
                                    }
                                }}
                                preSelected={preSelected}
                                label={fieldName}
                                placeholder={`Select an option for the field ${fieldName}`}
                                textfieldRequiredIndicator={isFieldRequired}
                            />
                        )
                    } 
                }
            }
            default:
                return {
                    initialValue: null,
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
    prepareCustomIssueFields: (projId, issueType) => {
        const createJiraIssueFieldMetaData = IssuesStore.getState().createJiraIssueFieldMetaData;

        const issueTypeFieldMetaDataList = createJiraIssueFieldMetaData?.[projId]?.[issueType] || [];
        const issueTypeFieldMetaDataMap = issueTypeFieldMetaDataList.reduce((acc, field) => {
            acc[field.fieldId] = field
            return acc;
        }, {});

        const displayJiraIssueFieldValues = IssuesStore.getState().displayJiraIssueFieldValues;
        const customIssueFields = Object.keys(displayJiraIssueFieldValues).reduce((acc, fieldId) => {
            const fieldMetaData = issueTypeFieldMetaDataMap[fieldId];
            const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(fieldMetaData);
            const fieldInitialValue = fieldConfiguration.initialValue;
            const fieldCurrentValue = displayJiraIssueFieldValues[fieldId];

            if (fieldMetaData) {
                const isRequired = fieldMetaData.required || false;
                const hasDefaultValue = fieldMetaData.hasDefaultValue || false;
                
                // Fail validation if the field is required but has no value
                if (isRequired && !hasDefaultValue && (fieldCurrentValue === fieldInitialValue)) {
                    throw new Error();
                }
            }

            acc.push({
                fieldId: fieldId,
                fieldValue: fieldCurrentValue
            });
            return acc;
        }, []);

        return customIssueFields;
    },
    prepareAdditionalIssueFieldsJiraMetaData: (projId, issueType) => {
        const customIssueFields = issuesFunctions.prepareCustomIssueFields(projId, issueType);
        const additionalIssueFields = { customIssueFields: customIssueFields };
        const jiraMetaData = { additionalIssueFields: additionalIssueFields };
        return jiraMetaData;
    }
}

export default issuesFunctions