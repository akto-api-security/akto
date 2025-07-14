import issuesApi from "@/apps/dashboard/pages/issues/api"
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import { TextField } from "@shopify/polaris";
import DropdownSearch from "../../components/shared/DropdownSearch";

const setCreateJiraIssueFieldMetaData = IssuesStore.getState().setCreateJiraIssueFieldMetaData;
const updateDisplayJiraIssueFieldValues = IssuesStore.getState().updateDisplayJiraIssueFieldValues;
const displayJiraIssueFieldValues = IssuesStore.getState().displayJiraIssueFieldValues;

const issuesFunctions = {
    getJiraFieldConfigurations: (customFieldURI, allowedValues) => {
        const handleFieldChange = (fieldId, value) => {
            updateDisplayJiraIssueFieldValues(fieldId, value)
        } 
       

        switch (customFieldURI) {
            case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
                return {
                    initialValue: "",
                    getComponent: ({ field }) => {
                        return (
                            <TextField
                                key={field?.fieldId || ""}
                                label={field?.name || ""}
                                value={displayJiraIssueFieldValues[field?.fieldId] || ""}
                                onChange={(value) => handleFieldChange(field?.fieldId, value)}
                                maxLength={255}
                                showCharacterCount
                                requiredIndicator
                            />)
                    }
                }
            case "com.atlassian.jira.plugin.system.customfieldtypes:select":
            case "com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons":
                return {
                    initialValue: allowedValues.length > 0 ? allowedValues[0].id : "",
                    getComponent: ({field}) => {
                        return (
                            <DropdownSearch
                                allowMultiple={customFieldURI.includes("select")}
                                optionsList={allowedValues.map((option) => ({
                                    label: option?.value,
                                    value: option.id
                                }))}
                                setSelected={(value) => handleFieldChange(field?.fieldId, value)}
                                value={displayJiraIssueFieldValues[field?.fieldId] || ""}
                                preSelected={[]}
                                label={field?.name || ""}
                                placeholder={`Select an option for the field ${field?.name || ""}`}
                            />
                        )
                    } 
                }
            default:
                return {
                    getComponent: ({ field }) => { return null }
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