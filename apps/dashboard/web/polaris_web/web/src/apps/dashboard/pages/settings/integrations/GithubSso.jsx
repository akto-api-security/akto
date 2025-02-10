import React, { useEffect, useState } from 'react'
import { LegacyCard, TextField, Modal, Text } from '@shopify/polaris';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import CopyCommand from '../../../components/shared/CopyCommand';
import StepsComponent from './components/StepsComponent';

function GithubSso() {
    
    const [githubClientId, setGithubClientId] = useState("")
    const [githubClientSecret, setGithubClientSecret] = useState("")
    const [showGithubSsoModal, setShowGithubSsoModal] = useState(false)
    const [githubPresent, setGithubPresent] = useState("")
    const [componentType, setComponentType] = useState(0) ;
    const [nextButtonActive,setNextButtonActive] = useState(window.DASHBOARD_MODE === "ON_PREM");
    const [githubUrl, setGithubUrl] = useState("https://github.com")
    const [githubApiUrl, setGithubApiUrl] = useState("https://api.github.com")

    const [isModalDisabled, setIsModalDisabled] = useState(false)

    const location = window.location ;
    const hostname = location.origin;

    const homepageUrl = hostname + "/dashboard/onboarding"
    const callBackUrl = hostname + "/signup-github"

    const integrationSteps = [
        {
            text: "Go to developer settings and register an OAuth application named 'Akto'."
        },
        {
            text: "In the 'Homepage URL',fill the below URL below",
            component: <CopyCommand command={homepageUrl} />
        },
        {
            text: "In the 'Authorization callback URL',fill the below URL below",
            component: <CopyCommand command={callBackUrl} />
        },
        {
            text: "Copy the 'Client id' and the 'Client secret' of your Outh App."
        }
    ]

    const deleteText = "Are you sure you want to remove Github SSO Integration? This might take away access from existing Akto users. This action cannot be undone."
    const addText = "Are you sure you want to add Github SSO Integration? This will enable all members of your GitHub account to access Akto dashboard."

    const handleDeleteGithubSso = async () => {
        setIsModalDisabled(true)
        const response = await settingRequests.deleteGithubSso()
        if (response) {
            func.setToast(true, false, "Github SSO deleted successfully!")
            setComponentType(0);
            setShowGithubSsoModal(false)
            setIsModalDisabled(false)
        }
    }

    async function fetchGithubSso() {
        try {
            let {githubClientId, githubApiUrl, githubUrl} = await settingRequests.fetchGithubSso()
            if(githubClientId){
                setComponentType(1);
            }
            setGithubPresent(!!githubClientId)
            setGithubClientId(githubClientId)
            if (githubUrl) setGithubUrl(githubUrl)
            if (githubApiUrl) setGithubApiUrl(githubApiUrl)
        } catch (error) {
            setNextButtonActive(false)
        }
    }

    useEffect(() => {
        fetchGithubSso()
    }, [])

    const handleAddGithubSso = async () => {
        setIsModalDisabled(true)
        const response = await settingRequests.addGithubSso(githubClientId, githubClientSecret, githubUrl, githubApiUrl)
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "Github SSO added successfully!")
                window.location.reload()
            }
            setShowGithubSsoModal(false)
            setIsModalDisabled(false)
        }
    }

    const cardContent = "Enable Login via GitHub on your Akto dashboard"

    const listComponent = (
        componentType === 0 ? <StepsComponent buttonActive={nextButtonActive} integrationSteps={integrationSteps} onClickFunc={() => setComponentType(1)}/>
        :<LegacyCard.Section 
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

            <TextField
                label="Github URL"
                value={githubUrl}
                onChange={ (githubUrl) => setGithubUrl(githubUrl)}
            />

            <TextField
                label="Github API URL"
                value={githubApiUrl}
                onChange={(githubApiUrl) => setGithubApiUrl(githubApiUrl)}
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
                onAction: githubPresent ? handleDeleteGithubSso : handleAddGithubSso,
                disabled: isModalDisabled
            }}
        >
            <Modal.Section>

                <Text>{githubPresent ? deleteText : addText}</Text>


            </Modal.Section>
        </Modal>
    )

    const card = (
        <LegacyCard title="GitHub SSO"
        {...componentType === 0 ? {} : {primaryFooterAction:{ content: (githubPresent ? 'Delete GitHub SSO' : 'Add GitHub SSO'), onAction: () => setShowGithubSsoModal(true) } }} 
        >
            {listComponent}
        </LegacyCard>
    )
    return (
        <>
        <IntegrationsLayout title="GitHub SSO" cardContent={cardContent} component={card} docsUrl="https://docs.akto.io/sso/github-oidc" />
        {modal}
        </>
    )
}

export default GithubSso