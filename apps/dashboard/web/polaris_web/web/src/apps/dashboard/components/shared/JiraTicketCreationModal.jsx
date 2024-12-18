import { Modal, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import DropdownSearch from './DropdownSearch'

const JiraTicketCreationModal = ({ activator, modalActive, setModalActive, handleSaveAction, jiraProjectMaps, setProjId, setIssueType, projId, issueType, issueId }) => {
    const [isCreatingTicket, setIsCreatingTicket] = useState(false)

    const getValueFromIssueType = (projId, issueId) => {
        if(Object.keys(jiraProjectMaps).length > 0 && projId.length > 0 && issueId.length > 0){
            const jiraTemp = jiraProjectMaps[projId].filter(x => x.issueId === issueId)
            if(jiraTemp.length > 0){
                return jiraTemp[0].issueType
            }
        }
        return issueType    
    }
    
    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={() => setModalActive(false)}
            size="small"
            title={<Text variant="headingMd">Configure jira ticket details</Text>}
            primaryAction={{
                content: 'Create ticket',
                onAction: () => {
                    setIsCreatingTicket(true)
                    handleSaveAction(issueId)
                    setIsCreatingTicket(false)
                },
                disabled: (!projId || !issueType || isCreatingTicket)
            }}
        >
            <Modal.Section>
                <VerticalStack gap={"3"}>
                    <DropdownSearch
                        disabled={jiraProjectMaps === undefined || Object.keys(jiraProjectMaps).length === 0}
                        placeholder="Select JIRA project"
                        optionsList={jiraProjectMaps ? Object.keys(jiraProjectMaps).map((x) => {return{label: x, value: x}}): []}
                        setSelected={setProjId}
                        preSelected={projId}
                        value={projId}
                    />

                    <DropdownSearch
                        disabled={Object.keys(jiraProjectMaps).length === 0 || projId.length === 0}
                        placeholder="Select JIRA issue type"
                        optionsList={jiraProjectMaps[projId] && jiraProjectMaps[projId].length > 0 ? jiraProjectMaps[projId].map((x) => {return{label: x.issueType, value: x.issueId}}) : []}
                        setSelected={setIssueType}
                        preSelected={issueType}
                        value={getValueFromIssueType(projId, issueType)}
                    />  
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default JiraTicketCreationModal