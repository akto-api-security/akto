import React, { useEffect, useState } from 'react'
import {Divider, LegacyCard, Text, TextField} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import Store from '../../../store';

function Jira() {
    
    const [baseUrl, setBaseUrl] = useState('');
    const [projId, setProjId] = useState('');
    const [apiToken, setApiToken] = useState('');
    const [userEmail, setUserEmail] = useState('');
    const [issueType, setIssueType] = useState('');

    const handleSelectChange = (id) =>{
      setSelected(id)
    }

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }
    
    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        setBaseUrl(jiraInteg != null ? jiraInteg.baseUrl: '')
        setProjId(jiraInteg != null ? jiraInteg.projId: '')
        setApiToken(jiraInteg != null ? jiraInteg.apiToken: '')
        setUserEmail(jiraInteg != null ? jiraInteg.userEmail: '')
        setIssueType(jiraInteg != null ? jiraInteg.issueType: '')
    }
    
    useEffect(() => {
        fetchJiraInteg()
    }, []);
    
    const seeWork = () => {
        window.open("https://docs.akto.io/traffic-connections/postman")
    }

    async function testJiraIntegration(){
        setToast(true,false,"Testing Jira Integration")
        let issueType = await settingFunctions.testJiraIntegration(userEmail, apiToken, baseUrl, projId)
            setIssueType(issueType)
        setToast(true,false,"Valid Jira Integration Credentials")
    }

    async function addJiraIntegration(){
        setToast(true,false,"Adding Jira Integration")
        await settingFunctions.addJiraIntegration(userEmail, apiToken, baseUrl, projId, issueType)
        setToast(true,false,"Successfully added Jira Integration")
    }
    
    const JCard = (
        <LegacyCard
            secondaryFooterActions={[{content: 'Test Integration',onAction: testJiraIntegration}]}
            primaryFooterAction={{content: 'Save', onAction: addJiraIntegration, disabled: (issueType==="" ? true : false) }}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Jira</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <TextField label="Base Url" value={baseUrl} helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)"  placeholder='Base Url' requiredIndicator onChange={setBaseUrl} />
                    <br />
                <TextField label="Email" value={userEmail} helpText="Specify your email id for which api token will be generated" placeholder='Email' requiredIndicator onChange={setUserEmail} />
                    <br />
                <PasswordTextField label="Api Token" helpText="Specify the api token created for your user email" field={apiToken} onFunc={true} setField={setApiToken} />
                    <br />
                <TextField label="Project Name" helpText="Specify the project id for your jira project (for ex - KAN)" value={projId} placeholder='Project Name' requiredIndicator onChange={setProjId} />
                    <br />
                
          </LegacyCard.Section> 
          
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Jira integration. Create jira tickets for api vulnerability issues and view them on the tap of a button"
    return (
        <IntegrationsLayout title= "Jira" cardContent={cardContent} component={JCard} docsUrl="https://docs.akto.io/traffic-connections/postman"/> 
    )
}

export default Jira