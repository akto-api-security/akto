import React, { useEffect, useState } from 'react'
import {Box, Button, Collapsible, Divider, HorizontalStack, Icon, LegacyCard, List, Scrollable, Text, TextField, VerticalStack} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func"

function Jira() {
    
    const [baseUrl, setBaseUrl] = useState('');
    const [projId, setProjId] = useState('');
    const [apiToken, setApiToken] = useState('');
    const [userEmail, setUserEmail] = useState('');
    const [projectIssueMap,setProjectIssuesMap] = useState({})
    const [collapsibleOpen, setCollapsibleOpen] = useState(false)
    
    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        setBaseUrl(jiraInteg != null ? jiraInteg.baseUrl: '')
        setProjId(jiraInteg != null ? jiraInteg.projId: '')
        setApiToken(jiraInteg != null ? jiraInteg.apiToken: '')
        setUserEmail(jiraInteg != null ? jiraInteg.userEmail: '')
    }
    
    useEffect(() => {
        fetchJiraInteg()
    }, []);

    const projectsComponent = (
        <Scrollable
            style={{maxHeight: '250px'}}
        >
            <VerticalStack gap={"4"}>
                <Box>
                    <Button plain monochrome removeUnderline onClick={() => setCollapsibleOpen(!collapsibleOpen)}>
                        <HorizontalStack gap={"4"}>
                            <Text variant="headingSm">Found {Object.keys(projectIssueMap).length} projects out of {projId.split(',').length}</Text>
                            <Box><Icon source={collapsibleOpen ? ChevronUpMinor : ChevronDownMinor} /></Box>
                        </HorizontalStack>
                    </Button>
                </Box>
                
                <Collapsible
                    open={collapsibleOpen}
                    transition={{ duration: '200ms', timingFunction: 'ease-in-out' }}
                >
                    <List type="bullet">
                        {Object.keys(projectIssueMap).map((key) => {
                            return(<List.Item key={key}>{key}</List.Item>)
                        })}
                    </List>
                </Collapsible>

            </VerticalStack>
        </Scrollable>
    )

    async function testJiraIntegration(){
        func.setToast(true,false,"Testing Jira Integration")
        let issueTypeMap = await settingFunctions.testJiraIntegration(userEmail, apiToken, baseUrl, projId)
        setProjectIssuesMap(issueTypeMap)
        func.setToast(true,false, "Fetched project maps")
    }

    async function addJiraIntegration(){
        await settingFunctions.addJiraIntegration(userEmail, apiToken, baseUrl, projId, projectIssueMap)
        func.setToast(true,false,"Successfully added Jira Integration")
        fetchJiraInteg()
    }
    
    const JCard = (
        <LegacyCard
            secondaryFooterActions={[{content: 'Test Integration',onAction: testJiraIntegration}]}
            primaryFooterAction={{content: 'Save', onAction: addJiraIntegration, disabled: (Object.keys(projectIssueMap).length === 0 ? true : false) }}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Jira</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField label="Base Url" value={baseUrl} helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)"  placeholder='Base Url' requiredIndicator onChange={setBaseUrl} />
                    <TextField label="Email" value={userEmail} helpText="Specify your email id for which api token will be generated" placeholder='Email' requiredIndicator onChange={setUserEmail} />
                    <PasswordTextField label="Api Token" helpText="Specify the api token created for your user email" field={apiToken} onFunc={true} setField={setApiToken} />
                    <TextField label="Add project ids" helpText="Specify the projects ids in comma separated string" value={projId} placeholder='Project Names' requiredIndicator onChange={setProjId} />
                </VerticalStack>
          </LegacyCard.Section> 
          {Object.keys(projectIssueMap).length > 0 ? <LegacyCard.Section>
              {projectsComponent} 
          </LegacyCard.Section>: null}
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