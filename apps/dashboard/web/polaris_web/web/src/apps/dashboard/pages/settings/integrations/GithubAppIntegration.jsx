import React, { useEffect, useState } from 'react'
import {  LegacyCard, TextField, Modal, Text, HorizontalStack, VerticalStack } from '@shopify/polaris';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import UploadFile from '../../../components/shared/UploadFile';

function GithubAppIntegration() {
    
    const [showGithubAppModal, setShowGithubAppModal] = useState(false)
    const [githubAppId, setGithubAppId] = useState("")
    const [githubAppSecretKey, setGithubAppSecretKey] = useState("")
    const [githubAppIdPresent, setGithubAppIdPresent] = useState("")

    const deleteAppSettingText = "Are you sure you want to remove GitHub App settings? This will remove Akto messages and checks from GitHub pull request."
    const addAppSettingText = "Are you sure you want to add GitHub App settings? This will enable Akto messages and checks in GitHub pull request."

    const cardContent = "Enable vulnerability reporting vulnerability via GitHub app"

    async function fetchGithubAppId() {
        let { githubAppId } = await settingRequests.fetchGithubAppId()
        setGithubAppIdPresent(!!githubAppId)
        setGithubAppId(githubAppId)
    }

    useEffect(() => {
        fetchGithubAppId()
    }, [])

    const handleAddGithubAppSettings = async () => {
        const response = await settingRequests.addGithubAppSecretKey(githubAppSecretKey, githubAppId)
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "GitHub App settings added successfully!")
                window.location.reload()
            }
        }
    }

    const handleDeleteGithubAppSettings = async () => {
        const response = await settingRequests.deleteGithubAppSettings()
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "Github App settings deleted successfully!")
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
                func.setToast(true, false, "Github secret key uploaded successfully")
            }
        }
    }

    const githubAppModal = (
        <Modal
            open={showGithubAppModal}
            onClose={() => setShowGithubAppModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: githubAppIdPresent ? 'Delete Github App settings' : 'Add GitHub App settings',
                onAction: githubAppIdPresent ? handleDeleteGithubAppSettings : handleAddGithubAppSettings
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
            <VerticalStack gap={2}>
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
                plain={false}
                fileChanged={file => handleFileChange(file)}
                tooltipText="Upload github app secret(.pem)"
                label="Upload github app secret"
                primary={false} />

            }
            </VerticalStack>

        </LegacyCard.Section>

    )

    const GithubAppSecret = (
        <LegacyCard
            title="GitHub App settings"
            primaryFooterAction={{ content: (githubAppIdPresent ? 'Delete GitHub App settings' : 'Add GitHub App settings'), onAction: () => setShowGithubAppModal(true) }}
            >
            {GithubAppComponent}
        </LegacyCard>
    )
    return (
        <>
            <IntegrationsLayout title="GitHub App Integration" cardContent={cardContent} component={GithubAppSecret} docsUrl="" />
            {githubAppModal}
        </>
    )
}

export default GithubAppIntegration