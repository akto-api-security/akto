import issuesApi from "@/apps/dashboard/pages/issues/api"
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import { TextField } from "@shopify/polaris";

const setCreateJiraIssueFieldMetaData = IssuesStore.getState().setCreateJiraIssueFieldMetaData;
const updateDisplayJiraIssueFieldValues = IssuesStore.getState().updateDisplayJiraIssueFieldValues;

const issuesFunctions = {
    getJiraFieldConfigurations: (customFieldURI) => {
        // Custom field types in JIRA - https://support.atlassian.com/jira-cloud-administration/docs/custom-fields-types-in-company-managed-projects/

        // Handle field value changes
        const handleFieldChange = (fieldId, value) => {
            updateDisplayJiraIssueFieldValues(fieldId, value)
        }

        switch (customFieldURI) {
            case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
                return {
                    initialValue: "",
                    getComponent: ({ field }) => {
                        const displayJiraIssueFieldValues = IssuesStore((state) => state.displayJiraIssueFieldValues)
                    
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
            default:
                return {
                    getComponent: ({ field }) => { return null },
                    initialValue: ""
                }
        }
    },
    fetchCreateIssueFieldMetaData: async () => {
        try {

            const response = await issuesApi.fetchCreateJiraIssueFieldMetaData()
            if (response && response.createIssueFieldMetaData) {
                const metaData = response.createIssueFieldMetaData;
                setCreateJiraIssueFieldMetaData(metaData);
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
    }
}

export default issuesFunctions