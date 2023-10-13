import React, { useEffect, useState } from 'react'
import { EmptyState, LegacyCard, TextField, Modal, Text } from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';

function GithubSso() {
    
    const [githubClientId, setGithubClientId] = useState("")
    const [githubClientSecret, setGithubClientSecret] = useState("")
    const [showGithubSsoModal, setShowGithubSsoModal] = useState(false)
    const [githubPresent, setGithubPresent] = useState("")

    const deleteText = "Are you sure you want to remove Github SSO Integration? This might take away access from existing Akto users. This action cannot be undone."
    const addText = "Are you sure you want to add Github SSO Integration? This will enable all members of your GitHub account to access Akto dashboard."

    const handleDeleteGithubSso = async () => {
        const response = await settingRequests.deleteGithubSso()
        if (response) {
            func.setToast(true, false, "Github SSO deleted successfully!")
            window.location.reload()
        }
    }

    async function fetchGithubSso() {
        let {githubClientId} = await settingRequests.fetchGithubSso()
        setGithubPresent(!!githubClientId)
        setGithubClientId(githubClientId)
    }

    useEffect(() => {
        fetchGithubSso()
    }, [])

    const handleAddGithubSso = async () => {
        const response = await settingRequests.addGithubSso(githubClientId, githubClientSecret)
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "Github SSO added successfully!")
                window.location.reload()
            }
        }
    }

    const cardContent = "Enable Login via GitHub on  your Akto dashboard"

    const listComponent = (

        <LegacyCard.Section
            title={`Github SSO Settings`}
        >
            <TextField
                label="Github Client Id"
                value={githubClientId}
                onChange={githubPresent ? () => {} : (githubClientId) => setGithubClientId(githubClientId)}
            />

            <TextField
                label="Github Client Secret"
                value={githubPresent ? "********************************": githubClientSecret}
                onChange={(githubClientSecret) => setGithubClientSecret(githubClientSecret)}
            />
        </LegacyCard.Section>

    )

    let modal = (
        <Modal
            open={showGithubSsoModal}
            onClose={() => setShowGithubSsoModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: githubPresent ? 'Delete Github SSO' : 'Add GitHub SSO',
                onAction: githubPresent ? handleDeleteGithubSso : handleAddGithubSso
            }}
        >
            <Modal.Section>

                <Text>{githubPresent ? deleteText : addText}</Text>


            </Modal.Section>
        </Modal>
    )

    const card = (
        <LegacyCard title="GitHub SSO"
        primaryFooterAction={{ content: (githubPresent ? 'Delete GitHub SSO' : 'Add GitHub SSO'), onAction: () => setShowGithubSsoModal(true) }}
        >
            {listComponent}
        </LegacyCard>
    )
    return (
        <>
        <IntegrationsLayout title="GitHub SSO" cardContent={cardContent} component={card} docsUrl="" />
        {modal}
        </>
    )
}

export default GithubSso