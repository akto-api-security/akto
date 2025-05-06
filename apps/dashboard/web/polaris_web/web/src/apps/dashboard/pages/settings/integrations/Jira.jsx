import React, {useEffect, useReducer, useRef, useState} from 'react'
import {
    Badge,
    Box,
    Button,
    Card,
    Checkbox,
    Divider,
    HorizontalStack,
    LegacyCard,
    Text,
    TextField,
    VerticalStack
} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import func from "@/util/func"
import DropdownSearch from '../../../components/shared/DropdownSearch';
import {produce} from "immer"
import api from '../api';

const aktoStatusForJira = ["Open","Fixed", "Ignored"]
const intialEmptyMapping = aktoStatusForJira.reduce((acc, status) => {
    acc[status.toUpperCase()] = [];
    return acc;
  }, {});

function Jira() {

    const [baseUrl, setBaseUrl] = useState('');
    const [projId, setProjId] = useState('');
    const [apiToken, setApiToken] = useState('');
    const [userEmail, setUserEmail] = useState('');
    const [projectIssueMap, setProjectIssuesMap] = useState({})
    const [projectMap, setProjectMap] = useReducer(produce((draft, action) => { projectMapReducer(draft, action) }), []);
    const [isAlreadyIntegrated, setIsAlreadyIntegrated] = useState(false)
    const [existingProjectIds, setExistingProjectIds] = useState([])
    const [isSaving, setIsSaving] = useState(false)
    const [initialFormData, setInitialFormData] = useState(null)


    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        if (jiraInteg !== null) {
            setIsAlreadyIntegrated(true)
            setBaseUrl(jiraInteg.baseUrl)
            setProjId(jiraInteg.projId)
            setApiToken(jiraInteg.apiToken)
            setUserEmail(jiraInteg.userEmail)
            updateProjectMap(jiraInteg)

            // Store initial form data for change detection
            setInitialFormData({
                baseUrl: jiraInteg.baseUrl,
                apiToken: jiraInteg.apiToken,
                userEmail: jiraInteg.userEmail,
                projectMappings: jiraInteg.projectMappings || {}
            });
        } else {
            // If integration is not present, add a default empty project
            setProjectMap({ type: 'ADD' });
        }
    }

    const prevInitialFormDataRef = useRef();

    useEffect(() => {
        console.log("Initial form data changed");
        if (prevInitialFormDataRef.current !== undefined) {
            console.log("Previous initialFormData:", prevInitialFormDataRef.current);
            console.log("Current initialFormData:", initialFormData);
        }
        prevInitialFormDataRef.current = initialFormData;
    }, [initialFormData]);

    const prevProjectMapRef = useRef();

    useEffect(() => {
        console.log("Project map changed");
        if (prevProjectMapRef.current !== undefined) {
            console.log("Previous projectMap:", prevProjectMapRef.current);
            console.log("Current projectMap:", projectMap);
        }
        prevProjectMapRef.current = projectMap;
    }, [projectMap]);

    function updateProjectMap(jiraInteg){
        setProjectMap({ type: 'CLEAR' })
        const projectMappings = jiraInteg?.projectMappings ?? {};
        let projectIds = new Set();
        Object.entries(projectMappings).forEach(([projectId, projectMapping], index) => {
            projectIds.add(projectId);

            // Use status names directly for the UI
            const aktoToJiraStatusMap = JSON.parse(JSON.stringify(intialEmptyMapping));
            const statuses = projectMapping.statuses || [];
            const jiraStatusLabel = statuses.map(x => { return { "label": x?.name ?? "", "value": x?.name ?? "" } });

            // If bidirectional integration is enabled, use names directly
            if (projectMapping?.biDirectionalSyncSettings?.enabled) {
                const aktoStatusMappings = projectMapping?.biDirectionalSyncSettings?.aktoStatusMappings || {};

                if (aktoStatusMappings) {
                    Object.entries(aktoStatusMappings).forEach(([status, nameList]) => {
                        if (!nameList || !Array.isArray(nameList)) return;

                        // Use the names directly
                        aktoToJiraStatusMap[status] = nameList;
                    });
                }
            }

            let isBidirectionalEnabled = projectMapping?.biDirectionalSyncSettings?.enabled || false;

            setProjectMap({
                type: 'APPEND',
                payload: {
                    projectId,
                    enableBiDirIntegration: isBidirectionalEnabled,
                    aktoToJiraStatusMap,
                    statuses: isBidirectionalEnabled ? statuses : null,
                    jiraStatusLabel,
                }
            })
        })
        Object.entries(jiraInteg?.projectIdsMap||{}).forEach(([projectId, issueMapping], index) => {
            if(!projectIds.has(projectId)){
                setProjectMap({
                    type: 'APPEND',
                    payload: {
                        projectId,
                        enableBiDirIntegration: false,
                        aktoToJiraStatusMap: JSON.parse(
                            JSON.stringify(intialEmptyMapping)),
                        statuses: [],
                        jiraStatusLabel: []
                    }
                })
            }
            projectIds.add(projectId)
        })
        setExistingProjectIds(Array.from(projectIds))
    }


    function toggleCheckbox(index){
        setProjectMap({
            type: 'UPDATE',
            payload: {
                index: index,
                updates: {
                    enableBiDirIntegration: !projectMap[index]?.enableBiDirIntegration || false
                }
            }
        })
    }

    async function fetchJiraStatusMapping(projId, index) {
        if (projectMap[index]?.enableBiDirIntegration) {
            toggleCheckbox(index);
            console.log("1");
            return;
        }
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim() || !projId?.trim()) {
            func.setToast(true, true, "Please fill all required fields");
            console.log("2");
            return;
        }


        const existingProject = projectMap?.find(project => project?.projectId === projId);
        if (existingProject?.statuses?.length > 0) {
            toggleCheckbox(index);
            console.log("3");
            return;
        }

        console.log("4");


        api.fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken).then((res) => {
            const jiraStatusLabel = res[projId]?.statuses?.map(x => { return { "label": x?.name ?? "", "value": x?.name ?? "" } });
            // for preSelected
            const intialIssue = res[projId]?.statuses?.map(x => x?.name) || [];
            let aktoToJiraStatusMap = JSON.parse(JSON.stringify(intialEmptyMapping))
            aktoStatusForJira?.forEach((x, i) => {
                if(!aktoToJiraStatusMap.hasOwnProperty(x?.toUpperCase())){
                    aktoToJiraStatusMap[x?.toUpperCase()] = [];
                }
                if (i >= intialIssue.length) return;
                aktoToJiraStatusMap[x?.toUpperCase()].push(intialIssue[i])
            })

            setProjectMap({
                type: 'UPDATE',
                payload: {
                    index: index,
                    updates: {
                        statuses: res[projId].statuses,
                        jiraStatusLabel,
                        aktoToJiraStatusMap
                    }
                }
            })
            toggleCheckbox(index)
        }).catch(err => {
            func.setToast(true, true, "Failed to fetch Jira statuses. Verify Project ID");
            return;
        })

    }

    useEffect(() => {
        fetchJiraInteg()
    }, []);


    function transformJiraObject() {
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
            func.setToast(true, true, "Please fill all required fields");
            return null;
        }
        if (!projectMap?.some(project => project?.projectId?.trim())) {
            func.setToast(true, true, "Please add at least one project");
            return null;
        }

        // Validate that all projects with bidirectional enabled have status mappings
        for (const project of projectMap) {
            if (project?.enableBiDirIntegration) {
                let hasAnyMapping = false;

                // Check if any status has mappings
                for (const status in project?.aktoToJiraStatusMap || {}) {
                    const mappings = project?.aktoToJiraStatusMap[status];
                    if (Array.isArray(mappings) && mappings.length > 0) {
                        hasAnyMapping = true;
                        break;
                    }
                }

                if (!hasAnyMapping) {
                    func.setToast(true, true, `Project ${project.projectId}: Status mappings are required when bidirectional integration is enabled`);
                    return null;
                }
            }
        }

        const projectMappings = {};
        projectMap?.forEach((project) => {
            if (!project?.projectId?.trim()) return;

            // Use status names directly in aktoStatusMappings
            const aktoStatusMappings = {};
            if (project?.enableBiDirIntegration) {
                // Just use the aktoToJiraStatusMap directly, as it already contains names
                Object.entries(project?.aktoToJiraStatusMap || {}).forEach(([status, nameList]) => {
                    if (!nameList || !Array.isArray(nameList)) {
                        aktoStatusMappings[status] = [];
                        return;
                    }

                    // Use the names directly
                    aktoStatusMappings[status] = nameList;
                });
            }

            projectMappings[project?.projectId] = {
                biDirectionalSyncSettings: {
                    enabled: project?.enableBiDirIntegration || false,
                    aktoStatusMappings: project?.enableBiDirIntegration
                        ? aktoStatusMappings : null,
                },
                statuses: project?.statuses || []
            };
        })
        return {apiToken, userEmail, baseUrl, projectMappings};
    }


    async function addJiraIntegrationV2() {
        const data = transformJiraObject();
        if (!data) return;

        setIsSaving(true);
        api.addJiraIntegrationV2(data).then((res) => {
            setIsAlreadyIntegrated(true);
            updateProjectMap(res);

            // Update initial form data after successful save
            // Use the transformed data to ensure consistency
            const currentProjectMap = [];
            projectMap.forEach(project => {
                currentProjectMap.push({
                    ...project
                });
            });

            setInitialFormData({
                baseUrl: data.baseUrl,
                apiToken: data.apiToken,
                userEmail: data.userEmail,
                projectMappings: data.projectMappings,
                currentProjectMap // Store the current project map for comparison
            });

            func.setToast(true, false, "Jira configurations saved successfully");
        }).catch(() => {
            func.setToast(true, true, "Failed to save Jira configurations check all required fields");
        }).finally(() => {
            setIsSaving(false);
        });

    }


    function projectMapReducer(draft, action) {

        switch (action.type) {
            case 'ADD':
                draft.push({
                    projectId: "",
                    enableBiDirIntegration: false,
                    aktoToJiraStatusMap: {
                        FIXED: [],
                        IGNORED: [],
                        OPEN: []
                    },
                    statuses: [],
                    jiraStatusLabel: [],
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
            case 'CLEAR':
                draft.length = 0;
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
        func.setToast(true, false, "Jira integration saved successfully")
        fetchJiraInteg()
    }

    function getLabel(value, project) {
        if (!value || !Array.isArray(value)) return [];

        // Since we're using names directly, we can just return the values
        // But we'll still check if they exist in the jiraStatusLabel array
        return value?.map((x) => {
            const match = project?.jiraStatusLabel?.find((y) => y.value === x);
            return match ? match.label : x; // Return the name directly if not found in jiraStatusLabel
        }).filter(Boolean);
    }

    // Function to check if a status is already selected for another Akto status
    function isStatusAlreadySelected(project, currentStatus, statusValue) {
        if (!project || !project.aktoToJiraStatusMap) return false;

        // Check all Akto statuses except the current one
        for (const aktoStatus in project.aktoToJiraStatusMap) {
            if (aktoStatus !== currentStatus) {
                const selectedStatuses = project.aktoToJiraStatusMap[aktoStatus] || [];
                if (selectedStatuses.includes(statusValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    function deleteProject(index) {
        const projectId = projectMap[index]?.projectId;
        const isExistingProject = existingProjectIds.includes(projectId);

        // Check if this is the last existing project from backend
        const existingProjectsInMap = projectMap.filter(p => existingProjectIds.includes(p.projectId));
        if (isExistingProject && existingProjectsInMap.length <= 1) {
            func.setToast(true, true, "Cannot delete the last project from the integration. Add another project first.");
            return;
        }

        setProjectMap({ type: 'REMOVE', index });
        if (isExistingProject && projectId) {
            api.deleteJiraIntegratedProject(projectId);
        }
        func.setToast(true, false, "Project removed successfully");
    }

    function projectKeyChangeHandler(index, val) {
        if (projectMap.some((project, i) => project.projectId === val)) {
            func.setToast(true, true, "Project key already exists")
        }
        setProjectMap({
            type: 'UPDATE',
            payload: {
                index,
                updates: {
                    projectId: val,
                    statuses: [],
                    jiraStatusLabel: [],
                    aktoToJiraStatusMap: JSON.parse(
                        JSON.stringify(intialEmptyMapping)),
                    enableBiDirIntegration: false
                }
            }
        })
    }

    const ProjectsCard = (
        <VerticalStack gap={4}>
            {projectMap?.map((project, index) => {
                return (
                    <Card roundedAbove="sm">
                        <VerticalStack gap={4}>
                            <HorizontalStack align='space-between'>
                                <Text fontWeight='semibold' variant='headingSm'>{`Project ${index + 1}`}</Text>
                                <Button plain removeUnderline destructive size='slim' disabled={projectMap.length <= 1} onClick={() => deleteProject(index)}>Delete Project</Button>
                            </HorizontalStack>
                            <TextField maxLength={10} showCharacterCount value={project?.projectId || ""} label="Project key" placeholder={"Project Key"}
                                onChange={(val)=> projectKeyChangeHandler(index,val)} />
                            <Checkbox label="Enable bi-directional integration"
                                disabled={!project?.projectId?.trim()}
                                checked={project.enableBiDirIntegration}
                                onChange={() => {
                                    fetchJiraStatusMapping(project.projectId, index)
                                }}
                            />
                            {project.enableBiDirIntegration &&
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
                                                        preSelected={project?.aktoToJiraStatusMap?.[val?.toUpperCase()] || []}
                                                        value={func.getSelectedItemsText(getLabel(project?.aktoToJiraStatusMap?.[val?.toUpperCase()], project) || [])} />
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

    function hasFormChanges() {
        // If there's no initial data, it's a new integration, so there are changes
        if (!initialFormData) return true;

        // Check if basic fields have changed
        if (baseUrl !== initialFormData.baseUrl ||
            apiToken !== initialFormData.apiToken ||
            userEmail !== initialFormData.userEmail) {
            return true;
        }

        // Check if project mappings have changed
        const currentData = transformJiraObject();
        if (!currentData) return false;

        // Compare project mappings
        const initialProjectIds = Object.keys(initialFormData.projectMappings || {});
        const currentProjectIds = Object.keys(currentData.projectMappings || {});

        // If the number of projects has changed
        if (initialProjectIds.length !== currentProjectIds.length) {
            return true;
        }

        // Check if any project has been added or removed
        for (const projectId of currentProjectIds) {
            if (!initialFormData.projectMappings[projectId]) {
                return true;
            }
        }

        // Check if any project settings have changed
        for (const projectId of currentProjectIds) {
            const initialProject = initialFormData.projectMappings[projectId];
            const currentProject = currentData.projectMappings[projectId];

            if (!initialProject) continue;

            // Check if bi-directional integration setting has changed
            if (initialProject.biDirectionalSyncSettings?.enabled !==
                currentProject.biDirectionalSyncSettings?.enabled) {
                return true;
            }

            // If bi-directional integration is enabled, check if mappings have changed
            if (currentProject.biDirectionalSyncSettings?.enabled) {
                const initialMappings = initialProject.biDirectionalSyncSettings?.aktoStatusMappings || {};
                const currentMappings = currentProject.biDirectionalSyncSettings?.aktoStatusMappings || {};

                // Check if the mappings are different
                for (const status of Object.keys(currentMappings)) {
                    const initialStatusMappings = initialMappings[status] || [];
                    const currentStatusMappings = currentMappings[status] || [];

                    // Compare arrays
                    if (initialStatusMappings.length !== currentStatusMappings.length) {
                        return true;
                    }

                    for (let i = 0; i < currentStatusMappings.length; i++) {
                        if (!initialStatusMappings.includes(currentStatusMappings[i])) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    function isSaveButtonDisabled() {
        if (isSaving) {
            return true;
        }

        // Check if required fields are filled
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
            return true;
        }

        // Check if ALL projects have a valid project ID
        if (projectMap?.length === 0 || projectMap?.some(project => !project?.projectId?.trim())) {
            return true;
        }

        // Check if projects with bidirectional integration have status mappings
        for (const project of projectMap) {
            if (project?.enableBiDirIntegration) {
                let hasAnyMapping = false;

                // Check if any status has mappings
                for (const status in project?.aktoToJiraStatusMap || {}) {
                    const mappings = project?.aktoToJiraStatusMap[status];
                    if (Array.isArray(mappings) && mappings.length > 0) {
                        hasAnyMapping = true;
                        break;
                    }
                }

                if (!hasAnyMapping) {
                    return true;
                }
            }
        }

        // Disable save button if there are no changes
        if (!hasFormChanges()) {
            return true;
        }

        return false;
    }

    const JCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addJiraIntegrationV2,
                disabled: isSaveButtonDisabled(),
                loading: isSaving
            }}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Jira</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField label="Base Url" value={baseUrl} helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)"  placeholder='Base Url' requiredIndicator onChange={setBaseUrl} />
                    <TextField label="Email" value={userEmail} helpText="Specify your email id for which api token will be generated" placeholder='Email' requiredIndicator onChange={setUserEmail} />
                    <PasswordTextField label="Api Token" helpText="Specify the api token created for your user email" field={apiToken} onFunc={true} setField={setApiToken} />
                    {/* <TextField label="Add project ids" helpText="Specify the projects ids in comma separated string" value={projId} placeholder='Project Names' requiredIndicator onChange={setProjId} /> */}
                    <HorizontalStack align='space-between'>
                        <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
                        <Button plain monochrome onClick={() => setProjectMap({ type: 'ADD' })}>Add Project</Button>
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
