import { FormLayout, Modal, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import DropdownSearch from './DropdownSearch'
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import issuesFunctions from '@/apps/dashboard/pages/issues/module';

const DisplayJiraCreateIssueFields = ({ displayJiraIssueFieldMetadata }) => {
    return (
        <FormLayout>
            {displayJiraIssueFieldMetadata.map((field, idx) => {               
                const customFieldURI = field.schema?.custom || ""
                const allowedValues = field.allowedValues || [];
                const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(customFieldURI, allowedValues)
                const FieldComponent = fieldConfiguration.getComponent
                
                return (
                    <div key={field.fieldId || idx}>
                        <FieldComponent field={field}/>
                    </div>
                )
            })}
        </FormLayout>
    )
}

const JiraTicketCreationModal = ({ activator, modalActive, setModalActive, handleSaveAction, jiraProjectMaps, setProjId, setIssueType, projId, issueType, issueId, isAzureModal }) => {
    const [isCreatingTicket, setIsCreatingTicket] = useState(false)
    const [displayJiraIssueFieldMetadata, setDisplayJiraIssueFieldMetadata] = useState([])

    const createJiraIssueFieldMetaData = IssuesStore((state) => state.createJiraIssueFieldMetaData)
    const setDisplayJiraIssueFieldValues = IssuesStore((state) => state.setDisplayJiraIssueFieldValues)

    const getValueFromIssueType = (projId, issueId) => {
        if(Object.keys(jiraProjectMaps).length > 0 && projId.length > 0 && issueId.length > 0){
            const jiraTemp = jiraProjectMaps[projId].filter(x => x.issueId === issueId)
            if(jiraTemp.length > 0){
                return jiraTemp[0].issueType
            }
        }
        return issueType    
    }

    useEffect(() => {
        if (!isAzureModal && projId && issueType) {
            // Ignore fields that are sent by akto
            const ignoreFields = ["summary", "issuetype", "project", "labels", "description"]

            const initialFieldMetaData = createJiraIssueFieldMetaData?.[projId]?.[issueType] || [];

            const filteredFieldMetaData = initialFieldMetaData.filter(field => {
                const hasNoDefault = field.hasDefaultValue === false; // filter out fields that have default values
                const isNotIgnored = !ignoreFields.includes(field.key.toLowerCase()); // filter out fields that are sent by akto
                return hasNoDefault && isNotIgnored;
            });
            setDisplayJiraIssueFieldMetadata(filteredFieldMetaData);

            const initialValues = filteredFieldMetaData.reduce((acc, field) => {
                const customFieldURI = field.schema?.custom || "";
                const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(customFieldURI, []);

                acc[field.fieldId] = fieldConfiguration.initialValue; //default value for each field
                return acc;
            }, {});
            setDisplayJiraIssueFieldValues(initialValues);
        }
    }, [isAzureModal, projId, issueType, createJiraIssueFieldMetaData])

    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={() => setModalActive(false)}
            size="small"
            title={<Text variant="headingMd">{isAzureModal ? "Configure Azure Boards Work Item" : "Configure jira ticket details"}</Text>}
            primaryAction={{
                content: (isAzureModal ? 'Create work item' : 'Create ticket'),
                onAction: () => {
                    setIsCreatingTicket(true)
                    handleSaveAction(issueId)
                    setIsCreatingTicket(false)
                    setModalActive(false)
                },
                disabled: (!projId || !issueType || isCreatingTicket)
            }}
        >
            <Modal.Section>
                <VerticalStack gap={"3"}>
                    <DropdownSearch
                        disabled={jiraProjectMaps === undefined || Object.keys(jiraProjectMaps).length === 0}
                        placeholder={isAzureModal ? "Select Azure Boards project" : "Select JIRA project"}
                        optionsList={jiraProjectMaps ? Object.keys(jiraProjectMaps).map((x) => {return{label: x, value: x}}): []}
                        setSelected={setProjId}
                        preSelected={projId}
                        value={projId}
                    />

                    <DropdownSearch
                        disabled={jiraProjectMaps == undefined || Object.keys(jiraProjectMaps).length === 0}
                        placeholder={isAzureModal ? "Select work item type" : "Select JIRA issue type"}
                        optionsList={jiraProjectMaps[projId] && jiraProjectMaps[projId].length > 0 ? jiraProjectMaps[projId].map(
                            (x) => {
                                if(isAzureModal){
                                    return {label: x, value: x}
                                } else {
                                    return {label: x.issueType, value: x.issueId}
                                }
                            }
                        ) : []}
                        setSelected={setIssueType}
                        preSelected={issueType}
                        value={isAzureModal ? issueType : getValueFromIssueType(projId, issueType)}
                    />  

                    {!isAzureModal && projId && issueType && displayJiraIssueFieldMetadata.length > 0 && (
                        <DisplayJiraCreateIssueFields 
                            displayJiraIssueFieldMetadata={displayJiraIssueFieldMetadata} 
                        />
                    )}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default JiraTicketCreationModal