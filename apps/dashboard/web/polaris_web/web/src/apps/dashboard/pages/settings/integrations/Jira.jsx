import React, { useEffect, useReducer, useState } from 'react'
import {Badge, Box, Button, Card, Checkbox, Collapsible, Divider, HorizontalStack, Icon, LegacyCard, List, Scrollable, Text, TextField, VerticalStack} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown';
import {produce} from "immer"

const temp = [
    {
        "projectName": "asgsadg", // Original example, typo corrected
        "projectId":1,
        "enableBiDirIntegraion": false,
        "aktoToJiraStatusMap": {
            "fixed": "In Progress", // Corrected "In Prograss"
            "Ignored": "To-do",
            "Open": "Backlog"
        }
    },
    {
        "projectName": "ProjectPhoenix", // New object 
        "projectId":2,
        "enableBiDirIntegraion": true,
        "aktoToJiraStatusMap": {
            "fixed": "Done",
            "Ignored": "Won't Do",
            "Open": "Selected for Development"
        }
    },
    {
        "projectName": "DataSyncModule", 
        "projectId":3,
        "enableBiDirIntegraion": true,
        "aktoToJiraStatusMap": {
            "fixed": "Closed",
            "Ignored": "Backlog", 
            "Open": "Open"       
        }
    }
];

const JiraStaus = [{label:"In Progress & Backlog",value:"In Progress & Backlog"},{label:"To-do",value:"To-do"},{label:"Backlog",value:"Backlog"}];
const aktoStatusForJira = ["Fixed","Ignored","Open"]

function Jira() {
    
    const [baseUrl, setBaseUrl] = useState('');
    const [projId, setProjId] = useState('');
    const [apiToken, setApiToken] = useState('');
    const [userEmail, setUserEmail] = useState('');
    const [projectIssueMap,setProjectIssuesMap] = useState({})
    const [collapsibleOpen, setCollapsibleOpen] = useState(false)
    const [projectMap,setProjectMap] = useReducer(produce((draft,action)=>{projectMapReducer(draft,action)}),temp);
    
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


    function projectMapReducer(draft, action){
        switch(action.type){
            case 'ADD':
                return draft.push({
                    projectName: "New Project",
                    projectId:"-1",
                    enableBiDirIntegraion: false,
                    aktoToJiraStatusMap: {
                        fixed: "In Progress",
                        Ignored: "To-do",
                        Open: "Backlog"
                    }
                });
            case 'REMOVE':
                draft.splice(action.index, 1);
                break
            case 'UPDATE':
                const { projectId: projectIdToUpdate, updates } = action.payload;
                const indexToUpdate = draft.findIndex(item => item.projectId === projectIdToUpdate);
                if (indexToUpdate !== -1 && updates) {
                    draft[indexToUpdate] = { ...draft[indexToUpdate], ...updates };
                    draft[indexToUpdate].projectId = projectIdToUpdate;
                }
                break;
            default:
                return draft;
        }
    }

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

    const ProjectsCard = (
        <VerticalStack gap={4}>
            {projectMap.map((project,index) => {
                return (
                    <Card roundedAbove="sm">
                        <VerticalStack gap={4}>
                            <HorizontalStack align='space-between'>
                                <Text fontWeight='semibold' variant='headingSm'>{`Project ${index+1}`}</Text>
                                <Button plain removeUnderline destructive size='slim' onClick={() => setProjectMap({type: 'REMOVE', index })}>Delete Project</Button>
                            </HorizontalStack>
                            <TextField value={project?.projectName || ""} label="Project name" placeholder={project.projectName}
                                onChange={(val) => setProjectMap({
                                    type: 'UPDATE',
                                    payload: {
                                        projectId: project.projectId,
                                        updates: {
                                            ...project,
                                            projectName: val
                                        }
                                    }
                                })} />
                            <Checkbox label="Enable bi-directional integration" 
                                checked={project.enableBiDirIntegraion}
                                onChange={() => setProjectMap({
                                    type: 'UPDATE',
                                    payload: {
                                        projectId: project.projectId,
                                        updates: {
                                            ...project,
                                            enableBiDirIntegraion: !project.enableBiDirIntegraion
                                        }
                                    }
                                })} 
                                />
                            {project.enableBiDirIntegraion &&
                                <VerticalStack gap={3} align='start'>
                                    <HorizontalStack gap={12}>
                                        <Text fontWeight='semibold' variant='headingXs'>Akto Status</Text>
                                        <Text fontWeight='semibold' variant='headingXs'>Jira Status</Text>
                                    </HorizontalStack>
                                    {
                                        aktoStatusForJira.map(val => {
                                            return (
                                                <HorizontalStack gap={8}>
                                                    <Box width='82px'><Badge >{val}</Badge></Box>
                                                    <Dropdown selected={(value) => {
                                                        setProjectMap({
                                                            type: 'UPDATE',
                                                            payload: {
                                                                projectId: project.projectId,
                                                                updates: {
                                                                    ...project,
                                                                    aktoToJiraStatusMap: {
                                                                        ...project.aktoToJiraStatusMap,
                                                                        val: value
                                                                    }
                                                                }
                                                            }
                                                        })
                                                    }} menuItems={JiraStaus} />
                                                </HorizontalStack>
                                            )
                                        })
                                    }
                                </VerticalStack>
                            }
                        </VerticalStack>
                    </Card>
                )
            })}
        </VerticalStack>
    )
    
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
                    {/* <TextField label="Add project ids" helpText="Specify the projects ids in comma separated string" value={projId} placeholder='Project Names' requiredIndicator onChange={setProjId} /> */}
                    <HorizontalStack align='space-between'>
                        <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
                        <Button plain monochrome onClick={() => setProjectMap({type: 'ADD'})}>Add Project</Button>
                    </HorizontalStack>
                    {projectMap.length !== 0? ProjectsCard : null}
                </VerticalStack>
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