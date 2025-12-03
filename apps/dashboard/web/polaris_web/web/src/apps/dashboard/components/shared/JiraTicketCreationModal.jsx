import { FormLayout, Modal, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState, useCallback } from 'react'
import DropdownSearch from './DropdownSearch'
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import issuesFunctions from '@/apps/dashboard/pages/issues/module';

const DisplayJiraCreateIssueFields = ({ displayJiraIssueFieldMetadata }) => {
    return (
        <FormLayout>
            {displayJiraIssueFieldMetadata.map((field, idx) => {     
                const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(field)
                const FieldComponent = fieldConfiguration.getComponent
                
                return (
                    <div key={field.fieldId || idx}>
                        <FieldComponent />
                    </div>
                )
            })}
        </FormLayout>
    )
}

const DisplayABCreateWorkItemFields = ({ displayABWorkItemFieldMetadata }) => {
        return (
            <FormLayout>
                {displayABWorkItemFieldMetadata.map((field, idx) => {
                    const fieldReferenceName = field?.organizationFieldDetails?.referenceName;
                    const fieldConfiguration = issuesFunctions.getABFieldConfigurations(field)
                    const FieldComponent = fieldConfiguration.getComponent

                    return (
                        <div key={fieldReferenceName || idx}>
                            <FieldComponent />
                        </div>
                    )
                })}
            </FormLayout>
        )
    }

const JiraTicketCreationModal = ({ activator, modalActive, setModalActive, handleSaveAction, jiraProjectMaps, setProjId, setIssueType, projId, issueType, issueId, isAzureModal, isServiceNowModal, labelsText, setLabelsText }) => {
    const [isCreatingTicket, setIsCreatingTicket] = useState(false)
    const [displayJiraIssueFieldMetadata, setDisplayJiraIssueFieldMetadata] = useState([])
    const [localLabelsText, setLocalLabelsText] = useState(labelsText || "")

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
        if (!isAzureModal && !isServiceNowModal && projId && issueType) {
            const initialFieldMetaData = createJiraIssueFieldMetaData?.[projId]?.[issueType] || [];

            const filteredFieldMetaData = initialFieldMetaData.filter(field => {
                const isCustom = field?.schema?.custom !== undefined ? true : false; // filter out fields that are not custom fields
                return isCustom
            });
            setDisplayJiraIssueFieldMetadata(filteredFieldMetaData);

            const initialValues = filteredFieldMetaData.reduce((acc, field) => {
                const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(field);

                acc[field.fieldId] = fieldConfiguration.initialValue; //default value for each field
                return acc;
            }, {});
            setDisplayJiraIssueFieldValues(initialValues);
        }
    }, [isAzureModal, isServiceNowModal, projId, issueType, createJiraIssueFieldMetaData])

    // Reset local state when modal opens
    useEffect(() => {
        if (modalActive) {
            setLocalLabelsText(labelsText || "");
        }
    }, [modalActive, labelsText])

    const handleLabelsChange = useCallback((val) => {
        setLocalLabelsText(val);
    }, [])

    // Azure boards work item custom fields state and effects
    const createABWorkItemFieldMetaData = IssuesStore((state) => state.createABWorkItemFieldMetaData)
    const setDisplayABWorkItemFieldValues = IssuesStore((state) => state.setDisplayABWorkItemFieldValues)
    const [ displayABWorkItemFieldMetadata, setDisplayABWorkItemFieldMetadata ] = useState([])

    useEffect(() => {
        if (isAzureModal && projId && issueType) {
            const initialFieldMetaData = createABWorkItemFieldMetaData?.[projId]?.[issueType] || [];

            const filteredFieldMetaData = initialFieldMetaData.filter(field => {
                const fieldReferenceName = field?.organizationFieldDetails?.referenceName;
                const isCustomField = (typeof fieldReferenceName === "string") && (fieldReferenceName.startsWith("Custom."));
                return isCustomField;
            });

            setDisplayABWorkItemFieldMetadata(filteredFieldMetaData);

            const initialValues = filteredFieldMetaData.reduce((acc, field) => {
                const fieldConfiguration = issuesFunctions.getABFieldConfigurations(field);
                const fieldReferenceName = field?.organizationFieldDetails?.referenceName;

                if (typeof fieldReferenceName === "string") {
                    acc[fieldReferenceName] = fieldConfiguration.initialValue;
                }
                return acc;
            }, {});
            
            setDisplayABWorkItemFieldValues(initialValues);
        }
    }, [isAzureModal, projId, issueType, createABWorkItemFieldMetaData]);

    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={() => setModalActive(false)}
            size="small"
            title={<Text variant="headingMd">{isServiceNowModal ? "Configure ServiceNow ticket details" : isAzureModal ? "Configure Azure Boards Work Item" : "Configure jira ticket details"}</Text>}
            primaryAction={{
                content: (isAzureModal ? 'Create work item' : 'Create ticket'),
                onAction: () => {
                    // Sync local labels to parent before saving
                    if (setLabelsText) {
                        setLabelsText(localLabelsText);
                    }
                    setIsCreatingTicket(true)
                    // Pass labels directly to handleSaveAction to avoid async state issues
                    handleSaveAction(issueId, localLabelsText)
                    setIsCreatingTicket(false)
                    setModalActive(false)
                },
                disabled: (isServiceNowModal ? (!projId || isCreatingTicket) : (!projId || !issueType || isCreatingTicket))
            }}
        >
            <Modal.Section>
                <VerticalStack gap={"3"}>
                    {isServiceNowModal ? (
                        <DropdownSearch
                            disabled={!jiraProjectMaps || jiraProjectMaps.length === 0}
                            placeholder="Select ServiceNow table"
                            optionsList={jiraProjectMaps ? jiraProjectMaps.map((x) => {return{label: x, value: x}}): []}
                            setSelected={setProjId}
                            preSelected={projId}
                            value={projId}
                        />
                    ) : (
                        <>
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
                            {!isAzureModal && setLabelsText &&
                                <TextField 
                                    onChange={handleLabelsChange} 
                                    value={localLabelsText} 
                                    placeholder={"Labels (comma separated)"}
                                    autoComplete="off"
                                />
                            }

                            {isAzureModal && projId && issueType && displayABWorkItemFieldMetadata.length > 0 && (
                                <DisplayABCreateWorkItemFields
                                    displayABWorkItemFieldMetadata={displayABWorkItemFieldMetadata}
                                />
                            )}
                        </>
                    )}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default JiraTicketCreationModal