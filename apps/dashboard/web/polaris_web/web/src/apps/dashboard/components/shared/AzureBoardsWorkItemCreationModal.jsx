import { Modal, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import DropdownSearch from './DropdownSearch'

const AzureBoardsWorkItemCreationModal = ({  activator, modalActive, setModalActive, handleSaveAction, projectToWorkItemsMap, projectId, setProjectId, workItemType, setWorkItemType, issueId }) => {
    const [loading, setLoading] = useState(false)

    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={() => {setModalActive(false)}}
            size="small"
            title={<Text variant="headingMd">Configure Azure Boards Work Item</Text>}
            primaryAction={{
                content: 'Create Work Item',
                onAction: () => {
                    setLoading(true)
                    handleSaveAction(issueId)
                    setLoading(false)
                },
                disabled: (!projectToWorkItemsMap || loading)
            }}
        >
            <Modal.Section>
                <VerticalStack gap={"3"}>
                    <DropdownSearch
                        disabled={projectToWorkItemsMap == undefined || Object.keys(projectToWorkItemsMap).length === 0}
                        placeholder="Select Azure Boards project"
                        optionsList={projectToWorkItemsMap ? Object.keys(projectToWorkItemsMap).map((x) => {return{label: x, value: x}}): []}
                        setSelected={setProjectId}
                        preSelected={projectId}
                        value={projectId}
                    />

                    <DropdownSearch
                        disabled={projectToWorkItemsMap == undefined || Object.keys(projectToWorkItemsMap).length === 0}
                        placeholder="Select work item type"
                        optionsList={projectToWorkItemsMap[projectId] && projectToWorkItemsMap[projectId].length > 0 ? projectToWorkItemsMap[projectId].map((x) => {return{label: x, value: x}}) : []}
                        setSelected={setWorkItemType}
                        preSelected={workItemType}
                        value={workItemType}
                    />  
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default AzureBoardsWorkItemCreationModal