import React, { useEffect, useState } from 'react'
import { EmptyState, LegacyCard, TextField, Modal, Text } from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import UploadFile from '../../../components/shared/UploadFile';

function GithubSso() {
    
    const [githubClientId, setGithubClientId] = useState("")
    const [githubClientSecret, setGithubClientSecret] = useState("")
    const [showGithubSsoModal, setShowGithubSsoModal] = useState(false)
    const [showGithubAppModal, setShowGithubAppModal] = useState(false)
    const [githubPresent, setGithubPresent] = useState("")
    const [githubAppId, setGithubAppId] = useState("")
    const [githubAppSecretKey, setGithubAppSecretKey] = useState("")
    const [githubAppIdPresent, setGithubAppIdPresent] = useState("")

    const deleteText = "Are you sure you want to remove Github SSO Integration? This might take away access from existing Akto users. This action cannot be undone."
    const addText = "Are you sure you want to add Github SSO Integration? This will enable all members of your GitHub account to access Akto dashboard."

    const deleteAppSettingText = "Are you sure you want to remove Github App settings? This will remove akto messages and checks from github pull request."
    const addAppSettingText = "Are you sure you want to add Github App settings? This will enable akto messages and checks in github pull request."

    const handleDeleteGithubSso = async () => {
        const response = await settingRequests.deleteGithubSso()
        if (response) {
            func.setToast(true, false, "Github SSO deleted successfully!")
            window.location.reload()
        }
    }

    async function fetchGithubSso() {
        let { githubClientId } = await settingRequests.fetchGithubSso()
        setGithubPresent(!!githubClientId)
        setGithubClientId(githubClientId)
    }

    async function fetchGithubAppId() {
        let { githubAppId } = await settingRequests.fetchGithubAppId()
        setGithubAppIdPresent(!!githubAppId)
        setGithubAppId(githubAppId)
    }

    useEffect(() => {
        fetchGithubSso()
        fetchGithubAppId()
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
                onChange={githubPresent ? () => { } : (githubClientId) => setGithubClientId(githubClientId)}
            />

            <TextField
                label="Github Client Secret"
                value={githubPresent ? "********************************" : githubClientSecret}
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

    const handleAddGithubAppSettings = async () => {
        const response = await settingRequests.addGithubAppSecretKey(githubAppSecretKey, githubAppId)
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "Github App settings added successfully!")
                window.location.reload()
            }
        }
    }


    function handleFileChange(file) {
        if (file) {
            const reader = new FileReader();
            let isPem = file.name.endsWith(".pem")
            if (isPem) {
                reader.readAsText(file)
            } 
            reader.onload = () => {
                setGithubAppSecretKey(reader.result)
            }
        }
    }

    let githubAppModal = (
        <Modal
            open={showGithubAppModal}
            onClose={() => setShowGithubAppModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: githubAppIdPresent ? 'Delete Github App settings' : 'Add GitHub App settings',
                onAction: githubAppIdPresent ? handleDeleteGithubSso : handleAddGithubAppSettings
            }}
        >
            <Modal.Section>

                <Text>{githubAppIdPresent ? deleteAppSettingText : addAppSettingText}</Text>


            </Modal.Section>
        </Modal>
    )

    const GithubAppComponent = (

        <LegacyCard.Section
            title={`Github App Settings`}
        >
            <TextField
                label="Github App Id"
                value={githubAppId}
                onChange={githubAppIdPresent ? () => { } : (githubAppId) => setGithubAppId(githubAppId)}
            />

            {githubAppIdPresent ? 
            (<TextField
                label="Github Secret Key"
                value={"********************************"}
            />) : 
            <UploadFile
                fileFormat=".pem"
                fileChanged={file => handleFileChange(file)}
                tooltipText="Upload github app secret(.pem)"
                label="Upload github app secret"
                primary={false} />

            }

        </LegacyCard.Section>

    )

    const GithubAppSecret = (
        <LegacyCard
            title="GitHub App settings"
            primaryFooterAction={{ content: (githubPresent ? 'Delete GitHub App settings' : 'Add GitHub App settings'), onAction: () => setShowGithubAppModal(true) }}
            >
            {GithubAppComponent}
        </LegacyCard>
    )
    return (
        <>
            <IntegrationsLayout title="GitHub SSO" cardContent={cardContent} component={card} secondaryComponent={GithubAppSecret} docsUrl="" />
            {modal}
            {githubAppModal}
        </>
    )
}

export default GithubSso