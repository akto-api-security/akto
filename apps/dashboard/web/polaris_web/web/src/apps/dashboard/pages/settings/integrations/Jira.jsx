import React, { useEffect, useReducer, useState } from 'react'
import { Badge, Box, Button, Card, Checkbox, Collapsible, Divider, HorizontalStack, Icon, LegacyCard, List, Scrollable, Text, TextField, VerticalStack } from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func"
import DropdownSearch from '../../../components/shared/DropdownSearch';
import { produce } from "immer"
import api from '../api';

const JiraStaus = [{ label: "In Progress & Backlog", value: "In Progress & Backlog" }, { label: "To-do", value: "To-do" }, { label: "Backlog", value: "Backlog" }];
const aktoStatusForJira = ["Fixed", "Ignored", "Open"]

function Jira() {

    const [baseUrl, setBaseUrl] = useState('');
    const [projId, setProjId] = useState('');
    const [apiToken, setApiToken] = useState('');
    const [userEmail, setUserEmail] = useState('');
    const [projectIssueMap, setProjectIssuesMap] = useState({})
    const [projectMap, setProjectMap] = useReducer(produce((draft, action) => { projectMapReducer(draft, action) }), []);
    const [isAlreadyIntegrated, setIsAlreadyIntegrated] = useState(false)

    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        if (jiraInteg !== null) setIsAlreadyIntegrated(true)
        setBaseUrl(jiraInteg != null ? jiraInteg.baseUrl : '')
        setProjId(jiraInteg != null ? jiraInteg.projId : '')
        setApiToken(jiraInteg != null ? jiraInteg.apiToken : '')
        setUserEmail(jiraInteg != null ? jiraInteg.userEmail : '')
        const projectMappings = jiraInteg?.projectMappings ?? {};
        Object.entries(projectMappings).forEach(([projectId, projectMapping], index) => {
            setProjectMap({
                type: 'APPEND',
                payload: {
                    projectId,
                    enableBiDirIntegraion: projectMapping?.biDirectionalSyncSettings?.enabled || false,
                    aktoToJiraStatusMap: projectMapping?.biDirectionalSyncSettings?.aktoStatusMappings || {
                        FIXED: [],
                        IGNORED: [],
                        OPEN: []
                    },
                    statuses: projectMapping.statuses,
                    jiraStatusLabel: projectMapping?.statuses?.map(x => { return { "label": x?.name ?? "", "value": x?.id ?? "" } }) ?? {}
                }
            })
        })
    }

    function toggleCheckbox(index){
        setProjectMap({
            type: 'UPDATE',
            payload: {
                index: index,
                updates: {
                    enableBiDirIntegraion: !projectMap[index]?.enableBiDirIntegraion || false
                }
            }
        })
    }

    async function fetchJiraStatusMapping(projId, index) {
        if(projectMap[index]?.enableBiDirIntegraion) {
            toggleCheckbox(index);
            return;
        }
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim() || !projId?.trim()) {
            func.setToast(true, true, "Please fill all fields");
            return;
        }
        
        
        const existingProject = projectMap?.find(project => project?.projectId === projId);
        if (existingProject?.statuses?.length > 0) {
            toggleCheckbox(index);
            return;
        }

        try {
            api.fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken).then((res) => {
                const jiraStatusLabel = res[projId].statuses.map(x => { return { "label": x?.name ?? "", "value": x?.id ?? "" } });
                setProjectMap({
                    type: 'UPDATE',
                    payload: {
                        index: index,
                        updates: {
                            statuses: res[projId].statuses,
                            jiraStatusLabel
                        }
                    }
                })
                toggleCheckbox(index)
            }).catch((err) => {
                func.setToast(true, true, "Error while fetching Jira Project statuses check Project ID");
                return;
            })
        } catch {
            
        }
        
    }

    useEffect(() => {
        fetchJiraInteg()
    }, []);


    function transformJiraObject() {
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
            func.setToast(true, true, "Please fill all fields");
            return null;
        }
        if (!projectMap?.some(project => project?.projectId?.trim())) {
            func.setToast(true, true, "Please add at least one project");
            return null;
        }

        const projectMappings = {};
        projectMap?.forEach((project) => {
            if (!project?.projectId?.trim()) return;
            const object = {
                biDirectionalSyncSettings: {
                    enabled: project?.enableBiDirIntegraion || false,
                    aktoStatusMappings: project?.enableBiDirIntegraion? project?.aktoToJiraStatusMap : {},
                },
                statuses: project?.statuses || []

            };
            projectMappings[project?.projectId] = object;
        })
        const data = { apiToken, userEmail, baseUrl, projectMappings };
        return data;
    }


    async function addJiraIntegrationV2() {
        const data = transformJiraObject();
        if (!data) return;
        try {
            const res = await api.addJiraIntegrationV2(data);
            setIsAlreadyIntegrated(true);
        } catch (err) {
        }

    }


    function projectMapReducer(draft, action) {
        
        switch (action.type) {
            case 'ADD':
                draft.push({
                    projectId: "",
                    enableBiDirIntegraion: false,
                    aktoToJiraStatusMap: {
                        FIXED: [],
                        IGNORED: [],
                        OPEN: []
                    },
                    statuses: [],
                    jiraStatusLabel: []
                });
                break;
            case 'REMOVE':
                draft.splice(action.index, 1);
                break
            case 'UPDATE':
                const { index, updates } = action.payload;
                if (index !== -1 && updates) {
                    const current = draft[index];
                    // If we're updating aktoToJiraStatusMap, merge it properly
                    if (updates.aktoToJiraStatusMap) {
                        current.aktoToJiraStatusMap = {
                            ...current.aktoToJiraStatusMap,
                            ...updates.aktoToJiraStatusMap
                        };
                    }
                    Object.keys(updates).forEach((key) => {
                        if (key !== "aktoToJiraStatusMap") {
                            current[key] = updates[key];
                        }
                    });
                }
                break;
            case 'APPEND':
                if (action.payload && typeof action.payload === 'object') {
                    draft.push({ ...action.payload });
                }
                break;
            default:
                return draft;
        }
    }

    async function testJiraIntegration() {
        func.setToast(true, false, "Testing Jira Integration")
        let issueTypeMap = await settingFunctions.testJiraIntegration(userEmail, apiToken, baseUrl, projId)
        setProjectIssuesMap(issueTypeMap)
        func.setToast(true, false, "Fetched project maps")
    }

    async function addJiraIntegration() {
        await settingFunctions.addJiraIntegration(userEmail, apiToken, baseUrl, projId, projectIssueMap)
        func.setToast(true, false, "Successfully added Jira Integration")
        fetchJiraInteg()
    }

    function getLabel(value, project) {
        if (!value) return [];
        return value.map((x) => {
            const match = project?.jiraStatusLabel?.find((y) => y.value === x);
            return match ? match.label : null;
        }).filter(Boolean);
    }

    const ProjectsCard = (
        <VerticalStack gap={4}>
            {projectMap?.map((project, index) => {
                return (
                    <Card roundedAbove="sm">
                        <VerticalStack gap={4}>
                            <HorizontalStack align='space-between'>
                                <Text fontWeight='semibold' variant='headingSm'>{`Project ${index + 1}`}</Text>
                                <Button plain removeUnderline destructive size='slim' onClick={() => setProjectMap({ type: 'REMOVE', index })}>Delete Project</Button>
                            </HorizontalStack>
                            <TextField requiredIndicator={index == 0} value={project?.projectId || ""} label="Project key" placeholder={project.projectId}
                                onChange={(val) => setProjectMap({
                                    type: 'UPDATE',
                                    payload: {
                                        index,
                                        updates: {
                                            projectId: val,
                                            statuses: [],
                                            jiraStatusLabel: [],
                                            aktoToJiraStatusMap: {
                                                FIXED: [],
                                                IGNORED: [],
                                                OPEN: []
                                            },
                                            enableBiDirIntegraion: false
                                        }
                                    }
                                })} />
                            <Checkbox label="Enable bi-directional integration"
                                checked={project.enableBiDirIntegraion}
                                onChange={() => {
                                    fetchJiraStatusMapping(project.projectId, index)
                                }}
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
                                                    <DropdownSearch setSelected={(value) => {
                                                        const label = val.toUpperCase();
                                                        setProjectMap({
                                                            type: "UPDATE",
                                                            payload: {
                                                                index,
                                                                updates: {
                                                                    aktoToJiraStatusMap: {
                                                                        [label]: value
                                                                    }
                                                                }
                                                            }
                                                        })
                                                    }}
                                                        optionsList={project?.jiraStatusLabel || []}
                                                        placeholder="Select Jira Status"
                                                        searchDisable={true}
                                                        showSelectedItemLabels={true}
                                                        allowMultiple={true}
                                                        preSelected={project?.aktoToJiraStatusMap[val?.toUpperCase()] || []}
                                                        value={func.getSelectedItemsText(getLabel(project?.aktoToJiraStatusMap[val?.toUpperCase()], project) || [])} />
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

    function checkSaveButton() {
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
            return true;
        }
        if (!projectMap?.some(project => project?.projectId?.trim())) {
            return true;
        }
        return false;
    }

    const JCard = (
        <LegacyCard
            // secondaryFooterActions={[{content: 'Test Integration',onAction: testJiraIntegration}]}
            primaryFooterAction={{ content: 'Save', onAction: addJiraIntegrationV2, disabled:checkSaveButton() }}
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
                        <Button disabled={projectMap.length === 1 && !isAlreadyIntegrated} plain monochrome onClick={() => setProjectMap({ type: 'ADD' })}>Add Project</Button>
                    </HorizontalStack>
                    {projectMap.length !== 0 ? ProjectsCard : null}
                </VerticalStack>
          </LegacyCard.Section>
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Jira integration. Create jira tickets for api vulnerability issues and view them on the tap of a button"
    return (
        <IntegrationsLayout title="Jira" cardContent={cardContent} component={JCard} docsUrl="https://docs.akto.io/traffic-connections/postman" />
    )
}

export default Jira