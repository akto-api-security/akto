import React, {useEffect} from 'react'
import {
  Badge,
  Box,
  Button,
  Card,
  Checkbox,
  Divider,
  HorizontalStack,
  LegacyCard,
  Spinner,
  Text,
  TextField,
  VerticalStack
} from '@shopify/polaris';
import settingFunctions from '../module';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import func from "@/util/func"
import api from '../api';
import Dropdown from "../../../components/layouts/Dropdown";
import {
  useAzureAdoReducer,
  initialEmptyMapping,
  aktoStatusForAzureAdo
} from './reducers/useAzureAdoReducer';

function AzureAdo() {
    const { state, actions } = useAzureAdoReducer();
    const {
        credentials: { organizationUrl, personalAccessToken },
        projects,
        existingProjectIds,
        isSaving,
        initialFormData,
        loadingProjectIndex
    } = state;

    async function fetchAzureAdoInteg() {
        let azureAdoInteg = await settingFunctions.fetchAzureAdoIntegration();
        if (azureAdoInteg !== null) {
            actions.setIsAlreadyIntegrated(true);
            actions.setCredentials('organizationUrl', azureAdoInteg.organizationUrl);
            actions.setCredentials('personalAccessToken', azureAdoInteg.personalAccessToken);
            updateProjectMap(azureAdoInteg);
            actions.setInitialFormData({
                organizationUrl: azureAdoInteg.organizationUrl,
                personalAccessToken: azureAdoInteg.personalAccessToken,
                projectMappings: azureAdoInteg.projectMappings || {}
            });
        } else {
            actions.addProject();
        }
    }

    function updateProjectMap(azureAdoInteg){
        actions.clearProjects();
        const projectMappings = azureAdoInteg?.projectMappings ?? {};
        let projectIds = new Set();
        const newProjects = [];

        Object.entries(projectMappings).forEach(([projectId, projectMapping], index) => {
            projectIds.add(projectId);
            const aktoToAzureAdoStatusMap = JSON.parse(JSON.stringify(initialEmptyMapping));
            const states = projectMapping.states || [];
            const azureAdoStateLabel = states.map(x => { return { "label": x?.name ?? "", "value": x?.name ?? "" } });

            if (projectMapping?.biDirectionalSyncSettings?.enabled) {
                const aktoStatusMappings = projectMapping?.biDirectionalSyncSettings?.aktoStatusMappings || {};
                if (aktoStatusMappings) {
                    Object.entries(aktoStatusMappings).forEach(([status, nameList]) => {
                        if (!nameList || !Array.isArray(nameList)) return;
                        aktoToAzureAdoStatusMap[status] = nameList;
                    });
                }
            }

            let isBidirectionalEnabled = projectMapping?.biDirectionalSyncSettings?.enabled || false;
            newProjects.push({
                projectId,
                workItemType: projectMapping.workItemType || 'Bug',
                enableBiDirIntegration: isBidirectionalEnabled,
                aktoToAzureAdoStatusMap,
                states: isBidirectionalEnabled ? states : null,
                azureAdoStateLabel,
            });
        });

        Object.entries(azureAdoInteg?.projectIdsMap||{}).forEach(([projectId, workItemMapping], index) => {
            if(!projectIds.has(projectId)){
                newProjects.push({
                    projectId,
                    workItemType: 'Bug',
                    enableBiDirIntegration: false,
                    aktoToAzureAdoStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
                    states: [],
                    azureAdoStateLabel: []
                });
            }
            projectIds.add(projectId);
        });

        actions.setProjects(newProjects);
        actions.setExistingProjectIds(Array.from(projectIds));
    }

    function fetchAzureAdoStatusMapping(projId, workItemType, index) {
        if (projects[index]?.enableBiDirIntegration) {
            actions.updateProject(index, {
                enableBiDirIntegration: false,
                aktoToAzureAdoStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
            });
            return;
        }

        if (!organizationUrl?.trim() || !personalAccessToken?.trim() || !projId?.trim() || !workItemType?.trim()) {
            func.setToast(true, true, "Please fill all required fields");
            return;
        }

        if (projects[index]?.states?.length > 0) {
            const aktoToAzureAdoStatusMap = projects[index]?.aktoToAzureAdoStatusMap || JSON.parse(JSON.stringify(initialEmptyMapping));
            const azureAdoStateLabel = projects[index]?.azureAdoStateLabel || projects[index]?.states?.map(x => {
                return {"label": x?.name ?? "", "value": x?.name ?? ""}
            }) || [];

            if (azureAdoStateLabel.length > 0) {
                aktoStatusForAzureAdo.forEach((status, idx) => {
                    const upperStatus = status.toUpperCase();
                    if (!aktoToAzureAdoStatusMap[upperStatus] || aktoToAzureAdoStatusMap[upperStatus].length === 0) {
                        const statusIndex = Math.min(idx, azureAdoStateLabel.length - 1);
                        if (azureAdoStateLabel.length > 0) {
                            aktoToAzureAdoStatusMap[upperStatus] = [azureAdoStateLabel[statusIndex].value];
                        }
                    }
                });
            }

            actions.updateProject(index, {
                enableBiDirIntegration: true,
                aktoToAzureAdoStatusMap,
                azureAdoStateLabel
            });
            return;
        }

        actions.setLoadingProjectIndex(index);
        api.fetchAzureAdoStatusMapping(projId, workItemType, organizationUrl, personalAccessToken).then(
            (res) => {
                const azureAdoStateLabel = res[projId]?.states?.map(x => {
                    return {"label": x?.name ?? "", "value": x?.name ?? ""}
                });
                const aktoToAzureAdoStatusMap = JSON.parse(JSON.stringify(initialEmptyMapping));

                if (azureAdoStateLabel.length > 0) {
                    aktoStatusForAzureAdo.forEach((status, index) => {
                        const upperStatus = status.toUpperCase();
                        const statusIndex = Math.min(index, azureAdoStateLabel.length - 1);
                        aktoToAzureAdoStatusMap[upperStatus] = [azureAdoStateLabel[statusIndex].value];
                    });
                }

                actions.updateProject(index, {
                    states: res[projId].states,
                    azureAdoStateLabel,
                    aktoToAzureAdoStatusMap,
                    enableBiDirIntegration: true
                });
                actions.setLoadingProjectIndex(null);
            }).catch(err => {
            func.setToast(true, true, "Failed to fetch Azure DevOps states. Verify Project ID and Work Item Type");
            actions.setLoadingProjectIndex(null);
        });
    }

    useEffect(() => {
        fetchAzureAdoInteg()
    }, []);

    function transformAzureAdoObject() {
        if (!organizationUrl?.trim() || !personalAccessToken?.trim()) {
            func.setToast(true, true, "Please fill all required fields");
            return null;
        }

        if (!projects?.some(project => project?.projectId?.trim())) {
            func.setToast(true, true, "Please add at least one project");
            return null;
        }

        const projectIds = new Set();
        for (const project of projects) {
            if (project?.projectId?.trim()) {
                if (projectIds.has(project.projectId)) {
                    func.setToast(true, true, `Duplicate project key: ${project.projectId}. Each project must have a unique key.`);
                    return null;
                }
                projectIds.add(project.projectId);
            }
        }

        for (const project of projects) {
            if (project?.enableBiDirIntegration) {
                const validation = validateStatusMappings(project);
                if (!validation.isValid) {
                    return null;
                }
            }
        }

        const projectMappings = {};
        projects?.forEach((project) => {
            if (!project?.projectId?.trim()) return;

            const aktoStatusMappings = {};
            if (project?.enableBiDirIntegration) {
                Object.entries(project?.aktoToAzureAdoStatusMap || {}).forEach(([status, nameList]) => {
                    if (!nameList || !Array.isArray(nameList)) {
                        aktoStatusMappings[status] = [];
                        return;
                    }
                    aktoStatusMappings[status] = nameList;
                });
            }

            projectMappings[project?.projectId] = {
                workItemType: project?.workItemType || 'Bug',
                biDirectionalSyncSettings: {
                    enabled: project?.enableBiDirIntegration || false,
                    aktoStatusMappings: project?.enableBiDirIntegration ? aktoStatusMappings : null,
                },
                states: project?.states || []
            };
        })

        return {personalAccessToken, organizationUrl, projectMappings};
    }

    async function addAzureAdoIntegrationV2() {
        const data = transformAzureAdoObject();
        if (!data) return;

        actions.setIsSaving(true);
        api.addAzureAdoIntegrationV2(data).then((res) => {
            actions.setIsAlreadyIntegrated(true);
            updateProjectMap(res);
            actions.setInitialFormData({
                organizationUrl: data.organizationUrl,
                personalAccessToken: data.personalAccessToken,
                projectMappings: data.projectMappings,
            });
            func.setToast(true, false, "Azure DevOps configurations saved successfully");
        }).catch(() => {
            func.setToast(true, true, "Failed to save Azure DevOps configurations check all required fields");
        }).finally(() => {
            actions.setIsSaving(false);
        });
    }

    function getLabel(value, project) {
        if (!value || !Array.isArray(value)) return [];
        return value?.map((x) => {
            const match = project?.azureAdoStateLabel?.find((y) => y.value === x);
            return match ? match.label : x;
        }).filter(Boolean);
    }

    function validateStatusMappings(project) {
        if (!project?.enableBiDirIntegration) {
            return { isValid: true, message: '' };
        }

        const aktoToAzureAdoStatusMap = project?.aktoToAzureAdoStatusMap || {};

        for (const status of aktoStatusForAzureAdo) {
            const upperStatus = status.toUpperCase();
            const mappings = aktoToAzureAdoStatusMap[upperStatus] || [];
            if (mappings.length === 0) {
                return {
                    isValid: false,
                    message: `Status mapping for ${status} is required when bidirectional integration is enabled.`
                };
            }
        }

        const usedStatuses = new Set();
        let hasDuplicates = false;
        let duplicateStatus = '';

        for (const status in aktoToAzureAdoStatusMap) {
            const mappings = aktoToAzureAdoStatusMap[status] || [];
            for (const mapping of mappings) {
                if (mapping && usedStatuses.has(mapping)) {
                    hasDuplicates = true;
                    duplicateStatus = mapping;
                    break;
                }
                if (mapping) usedStatuses.add(mapping);
            }
            if (hasDuplicates) break;
        }

        if (hasDuplicates) {
            return {
                isValid: false,
                message: `Azure DevOps State '${duplicateStatus}' is assigned to multiple Akto statuses. Each state must be unique.`
            };
        }

        return { isValid: true, message: '' };
    }

    function getDisabledOptions(project, currentStatus) {
        if (!project || !project.aktoToAzureAdoStatusMap) return [];

        const disabledOptions = [];
        for (const aktoStatus in project.aktoToAzureAdoStatusMap) {
            if (aktoStatus !== currentStatus) {
                const selectedStatuses = project.aktoToAzureAdoStatusMap[aktoStatus] || [];
                disabledOptions.push(...selectedStatuses);
            }
        }
        return disabledOptions;
    }

    function handleStatusSelection(index, project, aktoStatus, newValues) {
        const upperStatus = aktoStatus.toUpperCase();
        const updatedProject = JSON.parse(JSON.stringify(project));
        updatedProject.aktoToAzureAdoStatusMap[upperStatus] = newValues;
        const newProjects = [...projects];
        newProjects[index] = updatedProject;
        actions.setProjects(newProjects);
    }

    function handleWorkItemTypeSelection(index, selectedValues) {
        let workItemType;
        if (Array.isArray(selectedValues)) {
            workItemType = selectedValues[0] || 'Bug';
        } else if (typeof selectedValues === 'string') {
            workItemType = selectedValues;
        } else {
            workItemType = 'Bug';
        }

        const validWorkItemTypes = ['Bug', 'Task', 'User Story', 'Feature', 'Epic', 'Issue'];
        if (!validWorkItemTypes.includes(workItemType)) {
            workItemType = 'Bug';
        }
        
        actions.updateProject(index, {
            workItemType,
            states: [],
            azureAdoStateLabel: [],
            aktoToAzureAdoStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
            enableBiDirIntegration: false
        });
    }

    async function deleteProject(index) {
        if (loadingProjectIndex !== null || isSaving) {
            func.setToast(true, true, "Please wait for the current operation to complete.");
            return;
        }

        const projectId = projects[index]?.projectId;
        if (!projectId?.trim()) {
            actions.removeProject(index);
            func.setToast(true, false, "Project removed successfully");
            return;
        }

        const isExistingProject = existingProjectIds.includes(projectId);
        const existingProjectsInMap = projects.filter(p => existingProjectIds.includes(p.projectId));

        if (isExistingProject && existingProjectsInMap.length <= 1) {
            func.setToast(true, true, "Cannot delete the last project from the integration. Add another project first.");
            return;
        }

        if (isExistingProject) {
            try {
                actions.setLoadingProjectIndex(index);
                await api.deleteAzureAdoIntegratedProject(projectId).then((res) => {
                    if (initialFormData) {
                        const updatedProjectMappings = { ...initialFormData.projectMappings };
                        delete updatedProjectMappings[projectId];
                        initialFormData.projectMappings = updatedProjectMappings;
                        actions.setInitialFormData({
                            ...initialFormData,
                            projectMappings: updatedProjectMappings
                        });
                    }
                });
                actions.removeProject(index);
                actions.setLoadingProjectIndex(null);
                func.setToast(true, false, "Project removed successfully");
            } catch (error) {
                actions.setLoadingProjectIndex(null);
                func.setToast(true, true, `Failed to delete project: ${error.message || 'Unknown error'}`);
            }
        } else {
            actions.removeProject(index);
            func.setToast(true, false, "Project removed successfully");
        }
    }

    function projectKeyChangeHandler(index, val) {
        if (projects.some((project, i) => i !== index && project.projectId === val)) {
            func.setToast(true, true, "Project key already exists");
            return;
        }

        actions.updateProject(index, {
            projectId: val,
            states: [],
            azureAdoStateLabel: [],
            aktoToAzureAdoStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
            enableBiDirIntegration: false
        });
    }

    const workItemTypeOptions = [
        { label: 'Bug', value: 'Bug' },
        { label: 'Task', value: 'Task' },
        { label: 'User Story', value: 'User Story' },
        { label: 'Feature', value: 'Feature' },
        { label: 'Epic', value: 'Epic' },
        { label: 'Issue', value: 'Issue' }
    ];

    const ProjectsCard = (
        <VerticalStack gap={4}>
            {projects?.map((project, index) => {
                return (
                    <Card key={`project-${index}`} roundedAbove="sm">
                        <VerticalStack gap={4}>
                            <HorizontalStack align='space-between'>
                                <Text fontWeight='semibold' variant='headingSm'>{`Project ${index + 1}`}</Text>
                                <Button plain removeUnderline destructive size='slim' disabled={projects.length <= 1} onClick={() => deleteProject(index)}>Delete Project</Button>
                            </HorizontalStack>
                            <TextField 
                                value={project?.projectId || ""} 
                                label="Project Name" 
                                placeholder={"Project Name"} 
                                requiredIndicator
                                onChange={(val) => projectKeyChangeHandler(index, val)} 
                            />
                            <Box>
                                <Text variant="bodyMd" as="p" fontWeight="medium">Work Item Type</Text>
                                <Box paddingBlockStart="1">
                                    <Dropdown
                                        id={`work-item-type-${index}`}
                                        selected={(value) => handleWorkItemTypeSelection(index, value)}
                                        menuItems={workItemTypeOptions}
                                        placeholder="Select Work Item Type"
                                        showSelectedItemLabels={true}
                                        allowMultiple={false}
                                        preSelected={project?.workItemType ? [project.workItemType] : ['Bug']}
                                        value={project?.workItemType || 'Bug'}
                                        key={`dropdown-${index}-${project?.workItemType || 'Bug'}`}
                                    />
                                </Box>
                            </Box>
                            {loadingProjectIndex === index ? (
                                <div style={{ display: 'flex', alignItems: 'center', margin: '8px 0' }}>
                                    <Spinner size="small" />
                                    <Text variant="bodyMd" as="span" style={{ marginLeft: '8px' }}>&nbsp;&nbsp;Loading state mappings...</Text>
                                </div>
                            ) : (
                                <div style={{ display: 'flex', alignItems: 'center' }}>
                                    <Checkbox
                                        disabled={!project?.projectId?.trim() || !project?.workItemType?.trim()}
                                        checked={project.enableBiDirIntegration}
                                        onChange={() => {
                                            if (project?.projectId?.trim() && project?.workItemType?.trim()) {
                                                fetchAzureAdoStatusMapping(project.projectId, project.workItemType, index);
                                            }
                                        }}
                                        label=""
                                    />
                                    <span style={{ marginLeft: '4px', opacity: (project?.projectId?.trim() && project?.workItemType?.trim()) ? 1 : 0.5 }}>
                                        Enable bi-directional integration
                                    </span>
                                </div>
                            )}
                            {project.enableBiDirIntegration &&
                                <VerticalStack gap={3} align='start'>
                                    <HorizontalStack gap={12}>
                                        <Text fontWeight='semibold' variant='headingXs'>Akto Status</Text>
                                        <HorizontalStack gap={0}>
                                            <Text fontWeight='semibold' variant='headingXs'>Azure DevOps State </Text>
                                            <Text fontWeight='semibold' variant='headingXs' color="critical">*</Text>
                                        </HorizontalStack>
                                    </HorizontalStack>
                                    {
                                        aktoStatusForAzureAdo.map(val => {
                                            const selectedLabels = getLabel(project?.aktoToAzureAdoStatusMap?.[val?.toUpperCase()], project) || [];
                                            return (
                                                <HorizontalStack key={`status-${val}`} gap={8}>
                                                    <Box width='82px'><Badge>{val}</Badge></Box>
                                                    <Dropdown
                                                        id={`akto-status-${project.projectId}-${val}`}
                                                        selected={(value) => {
                                                            handleStatusSelection(index, project, val, value);
                                                        }}
                                                        menuItems={project?.azureAdoStateLabel || []}
                                                        placeholder="Select Azure DevOps State"
                                                        showSelectedItemLabels={true}
                                                        allowMultiple={true}
                                                        preSelected={project?.aktoToAzureAdoStatusMap?.[val?.toUpperCase()] || []}
                                                        value={selectedLabels.length > 0 ? selectedLabels.join(', ') : ''}
                                                        disabledOptions={getDisabledOptions(project, val.toUpperCase())} 
                                                    />
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
        if (!initialFormData) {
            return true;
        }

        if (organizationUrl !== initialFormData.organizationUrl ||
            personalAccessToken !== initialFormData.personalAccessToken) {
            return true;
        }

        const currentData = transformAzureAdoObject();
        if (!currentData) {
            return false;
        }

        const initialProjectIds = Object.keys(initialFormData.projectMappings || {});
        const currentProjectIds = Object.keys(currentData.projectMappings || {});

        if (initialProjectIds.length !== currentProjectIds.length) {
            return true;
        }

        for (const projectId of currentProjectIds) {
            if (!initialFormData.projectMappings[projectId]) {
                return true;
            }
        }

        for (const projectId of initialProjectIds) {
            if (!currentData.projectMappings[projectId]) {
                return true;
            }
        }

        for (const projectId of currentProjectIds) {
            const initialProject = initialFormData.projectMappings[projectId];
            const currentProject = currentData.projectMappings[projectId];

            if (!initialProject) {
                continue;
            }

            if (initialProject.workItemType !== currentProject.workItemType) {
                return true;
            }

            if (initialProject.biDirectionalSyncSettings?.enabled !==
                currentProject.biDirectionalSyncSettings?.enabled) {
                return true;
            }

            if (currentProject.biDirectionalSyncSettings?.enabled) {
                const initialMappings = initialProject.biDirectionalSyncSettings?.aktoStatusMappings || {};
                const currentMappings = currentProject.biDirectionalSyncSettings?.aktoStatusMappings || {};

                const initialStatusKeys = Object.keys(initialMappings);
                const currentStatusKeys = Object.keys(currentMappings);

                if (initialStatusKeys.length !== currentStatusKeys.length) {
                    return true;
                }

                for (const status of currentStatusKeys) {
                    if (!initialMappings.hasOwnProperty(status)) {
                        return true;
                    }
                }

                for (const status of currentStatusKeys) {
                    const initialStatusMappings = initialMappings[status] || [];
                    const currentStatusMappings = currentMappings[status] || [];

                    if (initialStatusMappings.length !== currentStatusMappings.length) {
                        return true;
                    }

                    for (const mapping of currentStatusMappings) {
                        if (!initialStatusMappings.includes(mapping)) {
                            return true;
                        }
                    }

                    for (const mapping of initialStatusMappings) {
                        if (!currentStatusMappings.includes(mapping)) {
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

        if (!organizationUrl?.trim() || !personalAccessToken?.trim()) {
            return true;
        }

        if (projects?.length === 0 || projects?.some(project => !project?.projectId?.trim())) {
            return true;
        }

        for (const project of projects) {
            if (project?.enableBiDirIntegration) {
                const validation = validateStatusMappings(project);
                if (!validation.isValid) {
                    return true;
                }
            }
        }

        return !hasFormChanges();
    }

    const AzureAdoCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addAzureAdoIntegrationV2,
                disabled: isSaveButtonDisabled(),
                loading: isSaving
            }}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Azure DevOps</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField 
                        label="Organization URL" 
                        value={organizationUrl} 
                        helpText="Specify your Azure DevOps organization URL (e.g., https://dev.azure.com/yourorganization)" 
                        placeholder='Organization URL' 
                        requiredIndicator 
                        onChange={(value) => actions.setCredentials('organizationUrl', value)} 
                    />
                    <PasswordTextField 
                        label="Personal Access Token" 
                        helpText="Specify the Personal Access Token (PAT) with Work Items read/write permissions" 
                        field={personalAccessToken} 
                        onFunc={true} 
                        setField={(value) => actions.setCredentials('personalAccessToken', value)} 
                    />
                    <HorizontalStack align='space-between'>
                        <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
                        <Button plain monochrome onClick={() => actions.addProject()}>Add Project</Button>
                    </HorizontalStack>
                    {projects.length !== 0 ? ProjectsCard : null}
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Azure DevOps integration. Create work items for API vulnerability issues and manage them efficiently."

    return (
        <IntegrationsLayout 
            title="Azure DevOps" 
            cardContent={cardContent} 
            component={AzureAdoCard} 
            docsUrl="https://docs.akto.io/traffic-connections/azure-devops" 
        />
    )
}

export default AzureAdo