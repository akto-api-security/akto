import { Modal, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import DropdownSearch from './DropdownSearch'

const LinearTicketCreationModal = ({ activator, modalActive, setModalActive, handleSaveAction, teams, teamId, setTeamId, issueId }) => {

    const selectedTeam = teams.find(team => team.id === teamId)

    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={() => setModalActive(false)}
            size="small"
            title={<Text variant="headingMd">Configure Linear ticket details</Text>}
            primaryAction={{
                content: 'Create ticket',
                onAction: () => {
                    handleSaveAction(issueId)
                    setModalActive(false)
                },
                disabled: !teamId
            }}
        >
            <Modal.Section>
                <VerticalStack gap="3">
                    <DropdownSearch
                        disabled={teams.length === 0}
                        placeholder="Select Linear team"
                        optionsList={teams.map(team => ({ label: team.name, value: team.id }))}
                        setSelected={setTeamId}
                        preSelected={teamId}
                        value={selectedTeam ? selectedTeam.name : ''}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default LinearTicketCreationModal
