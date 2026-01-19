import React, {useEffect, useState} from 'react'
import {
  Badge,
  Box,
  Button,
  Card,
  Checkbox,
  Divider,
  HorizontalStack,
  LegacyCard,
  RadioButton,
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
  useJiraReducer,
  initialEmptyMapping,
  aktoStatusForJira,
  aktoSeverities
} from './reducers/useJiraReducer';

function Jira() {
    const { state, actions } = useJiraReducer();

    const {
        credentials: { baseUrl, apiToken, userEmail },
        jiraType,
        projects,
        existingProjectIds,
        isSaving,
        initialFormData,
        loadingProjectIndex,
        jiraPriorities,
        severityToPriorityMap,
        initialSeverityMapping,
        isLoadingPriorities,
        isSavingSeverityMapping,
        isAlreadyIntegrated
    } = state;


    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        if (jiraInteg !== null) {
            actions.setIsAlreadyIntegrated(true);

            actions.setCredentials('baseUrl', jiraInteg.baseUrl);
            actions.setCredentials('apiToken', jiraInteg.apiToken);
            actions.setCredentials('userEmail', jiraInteg.userEmail);
            
            // Set jiraType from backend
            if (jiraInteg.jiraType) {
                actions.setJiraType(jiraInteg.jiraType);
            }

            updateProjectMap(jiraInteg);

            actions.setInitialFormData({
                baseUrl: jiraInteg.baseUrl,
                apiToken: jiraInteg.apiToken,
                userEmail: jiraInteg.userEmail,
                projectMappings: jiraInteg.projectMappings || {},
                jiraType: jiraInteg.jiraType || 'CLOUD'
            });
        } else {
            actions.addProject();
        }
    }

    function updateProjectMap(jiraInteg){
        actions.clearProjects();
        const projectMappings = jiraInteg?.projectMappings ?? {};
        let projectIds = new Set();
        const newProjects = [];

        Object.entries(projectMappings).forEach(([projectId, projectMapping], index) => {
            projectIds.add(projectId);

            const aktoToJiraStatusMap = JSON.parse(JSON.stringify(initialEmptyMapping));
            const statuses = projectMapping.statuses || [];
            const jiraStatusLabel = statuses.map(x => { return { "label": x?.name ?? "", "value": x?.name ?? "" } });

            if (projectMapping?.biDirectionalSyncSettings?.enabled) {
                const aktoStatusMappings = projectMapping?.biDirectionalSyncSettings?.aktoStatusMappings || {};

                if (aktoStatusMappings) {
                    Object.entries(aktoStatusMappings).forEach(([status, nameList]) => {
                        if (!nameList || !Array.isArray(nameList)) return;
                        aktoToJiraStatusMap[status] = nameList;
                    });
                }
            }

            let isBidirectionalEnabled = projectMapping?.biDirectionalSyncSettings?.enabled || false;

            newProjects.push({
                projectId,
                enableBiDirIntegration: isBidirectionalEnabled,
                aktoToJiraStatusMap,
                statuses: isBidirectionalEnabled ? statuses : null,
                jiraStatusLabel,
            });
        });

        Object.entries(jiraInteg?.projectIdsMap||{}).forEach(([projectId, issueMapping], index) => {
            if(!projectIds.has(projectId)){
                newProjects.push({
                    projectId,
                    enableBiDirIntegration: false,
                    aktoToJiraStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
                    statuses: [],
                    jiraStatusLabel: []
                });
            }
            projectIds.add(projectId);
        });

        actions.setProjects(newProjects);
        actions.setExistingProjectIds(Array.from(projectIds));
    }


    function fetchJiraStatusMapping(projId, index) {
      if (projects[index]?.enableBiDirIntegration) {
        actions.updateProject(index, {
          enableBiDirIntegration: false,
          aktoToJiraStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
        });
        return;
      }

      if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()
          || !projId?.trim()) {
        func.setToast(true, true, "Please fill all required fields");
        return;
      }

      if (projects[index]?.statuses?.length > 0) {
        const aktoToJiraStatusMap = projects[index]?.aktoToJiraStatusMap
            || JSON.parse(JSON.stringify(initialEmptyMapping));
        const jiraStatusLabel = projects[index]?.jiraStatusLabel ||
            projects[index]?.statuses?.map(x => {
              return {"label": x?.name ?? "", "value": x?.name ?? ""}
            }) || [];
        if (jiraStatusLabel.length > 0) {
          aktoStatusForJira.forEach((status, idx) => {
            const upperStatus = status.toUpperCase();
            if (!aktoToJiraStatusMap[upperStatus]
                || aktoToJiraStatusMap[upperStatus].length === 0) {
              const statusIndex = Math.min(idx, jiraStatusLabel.length - 1);
              if (jiraStatusLabel.length > 0) {
                aktoToJiraStatusMap[upperStatus] = [jiraStatusLabel[statusIndex].value];
              }
            }
          });
        }

        actions.updateProject(index, {
          enableBiDirIntegration: true,
          aktoToJiraStatusMap,
          jiraStatusLabel
        });
        return;
      }

      actions.setLoadingProjectIndex(index);

      api.fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken).then(
          (res) => {
            const jiraStatusLabel = res[projId]?.statuses?.map(x => {
              return {"label": x?.name ?? "", "value": x?.name ?? ""}
            });
            const aktoToJiraStatusMap = JSON.parse(
                JSON.stringify(initialEmptyMapping));

            if (jiraStatusLabel.length > 0) {
              aktoStatusForJira.forEach((status, index) => {
                const upperStatus = status.toUpperCase();
                const statusIndex = Math.min(index, jiraStatusLabel.length - 1);
                aktoToJiraStatusMap[upperStatus] = [jiraStatusLabel[statusIndex].value];
              });
            }

            actions.updateProject(index, {
              statuses: res[projId].statuses,
              jiraStatusLabel,
              aktoToJiraStatusMap,
              enableBiDirIntegration: true
            });

            actions.setLoadingProjectIndex(null);
          }).catch(err => {
        func.setToast(true, true,
            "Failed to fetch Jira statuses. Verify Project ID");
        actions.setLoadingProjectIndex(null);
      });
    }

    async function fetchSeverityMapping() {
        try {
            const response = await api.fetchSeverityMapping();

            // Handle response - it might be the map directly or wrapped
            let mapping = {};
            if (response && typeof response === 'object' && !Array.isArray(response)) {
                if (response.severityToPriorityMap) {
                    mapping = response.severityToPriorityMap;
                } else {
                    mapping = response;
                }
            }

            actions.setSeverityToPriorityMap(mapping);
            actions.setInitialSeverityMapping(mapping);
        } catch (err) {
            actions.setSeverityToPriorityMap({});
            actions.setInitialSeverityMapping({});
        }
    }

    async function fetchPriorities() {
        // Backend always fetches credentials from database
        if (!isAlreadyIntegrated) {
            return;
        }

        actions.setIsLoadingPriorities(true);
        try {
            const response = await api.fetchJiraPriorities();

            // Handle response - it might be an array directly or wrapped in an object
            let priorities = [];
            if (Array.isArray(response)) {
                priorities = response;
            } else if (response && Array.isArray(response.jiraPriorities)) {
                priorities = response.jiraPriorities;
            } else if (response && typeof response === 'object') {
                // If response is an object, try to get the array from it
                const values = Object.values(response);
                if (values.length > 0 && values[0] && typeof values[0] === 'object' && values[0].id && values[0].name) {
                    priorities = values;
                }
            }

            actions.setJiraPriorities(priorities);
        } catch (err) {
            func.setToast(true, true, "Failed to fetch Jira priorities");
            actions.setJiraPriorities([]);
        } finally {
            actions.setIsLoadingPriorities(false);
        }
    }

    async function saveSeverityMapping() {
        actions.setIsSavingSeverityMapping(true);
        try {
            await api.saveSeverityMapping(severityToPriorityMap);
            func.setToast(true, false, "Severity mapping saved successfully");
            // Update initial mapping to match current state after successful save
            actions.setInitialSeverityMapping({...severityToPriorityMap});
        } catch (err) {
            func.setToast(true, true, "Failed to save severity mapping");
        } finally {
            actions.setIsSavingSeverityMapping(false);
        }
    }

    function handleSeverityMappingChange(severity, priorityId) {
        actions.updateSeverityMapping(severity, priorityId);
    }

    function getSeverityBadgeColor(severity) {
        switch(severity) {
            case 'CRITICAL': return 'critical';
            case 'HIGH': return 'warning';
            case 'MEDIUM': return 'attention';
            case 'LOW': return 'info';
            default: return 'default';
        }
    }

    function hasSeverityMappingChanged() {
        // Compare current mapping with initial mapping
        const currentKeys = Object.keys(severityToPriorityMap || {});
        const initialKeys = Object.keys(initialSeverityMapping || {});

        // If different number of keys, there's a change
        if (currentKeys.length !== initialKeys.length) {
            return true;
        }

        // Check if any value is different
        for (const key of currentKeys) {
            if (severityToPriorityMap[key] !== initialSeverityMapping[key]) {
                return true;
            }
        }

        return false;
    }

    useEffect(() => {
        fetchJiraInteg();
        fetchSeverityMapping();
    }, []);

    useEffect(() => {
        // Fetch priorities from database when Jira is already integrated
        if (isAlreadyIntegrated) {
            fetchPriorities();
        }
    }, [isAlreadyIntegrated]);


    function transformJiraObject() {
        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
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
                Object.entries(project?.aktoToJiraStatusMap || {}).forEach(([status, nameList]) => {
                    if (!nameList || !Array.isArray(nameList)) {
                        aktoStatusMappings[status] = [];
                        return;
                    }

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
        return {apiToken, userEmail, baseUrl, projectMappings, jiraType};
    }


    async function addJiraIntegrationV2() {
        const data = transformJiraObject();
        if (!data) return;

        actions.setIsSaving(true);
        api.addJiraIntegrationV2(data).then((res) => {
            actions.setIsAlreadyIntegrated(true);
            updateProjectMap(res);

            actions.setInitialFormData({
                baseUrl: data.baseUrl,
                apiToken: data.apiToken,
                userEmail: data.userEmail,
                projectMappings: data.projectMappings,
                jiraType: data.jiraType
            });

            func.setToast(true, false, "Jira configurations saved successfully");
        }).catch(() => {
            func.setToast(true, true, "Failed to save Jira configurations check all required fields");
        }).finally(() => {
            actions.setIsSaving(false);
        });

    }

    function getLabel(value, project) {
        if (!value || !Array.isArray(value)) return [];

        return value?.map((x) => {
            const match = project?.jiraStatusLabel?.find((y) => y.value === x);
            return match ? match.label : x;
        }).filter(Boolean);
    }

    function validateStatusMappings(project) {
        if (!project?.enableBiDirIntegration) {
            return { isValid: true, message: '' };
        }

        const aktoToJiraStatusMap = project?.aktoToJiraStatusMap || {};

        // Check if all required statuses have values
        for (const status of aktoStatusForJira) {
            const upperStatus = status.toUpperCase();
            const mappings = aktoToJiraStatusMap[upperStatus] || [];

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

        for (const status in aktoToJiraStatusMap) {
            const mappings = aktoToJiraStatusMap[status] || [];
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
                message: `Jira Status '${duplicateStatus}' is assigned to multiple Akto statuses. Each Jira status must be unique.`
            };
        }

        return { isValid: true, message: '' };
    }

    function getDisabledOptions(project, currentStatus) {
        if (!project || !project.aktoToJiraStatusMap) return [];

        const disabledOptions = [];

        for (const aktoStatus in project.aktoToJiraStatusMap) {
            if (aktoStatus !== currentStatus) {
                const selectedStatuses = project.aktoToJiraStatusMap[aktoStatus] || [];
                disabledOptions.push(...selectedStatuses);
            }
        }

        return disabledOptions;
    }

    function handleStatusSelection(index, project, aktoStatus, newValues) {
        const upperStatus = aktoStatus.toUpperCase();
        const updatedProject = JSON.parse(JSON.stringify(project));

        updatedProject.aktoToJiraStatusMap[upperStatus] = newValues;

        const newProjects = [...projects];
        newProjects[index] = updatedProject;

        actions.setProjects(newProjects);
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
                await api.deleteJiraIntegratedProject(projectId).then((res) => {
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
      if (val && !/^[A-Z0-9]+$/.test(val)) {
        func.setToast(true, true, "Project key must contain only capital letters and numbers");
        return;
      }

      if (projects.some((project, i) => i !== index && project.projectId === val)) {
        func.setToast(true, true, "Project key already exists");
        return;
      }

      actions.updateProject(index, {
        projectId: val,
        statuses: [],
        jiraStatusLabel: [],
        aktoToJiraStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
        enableBiDirIntegration: false
      });
    }

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
                            <TextField maxLength={10} showCharacterCount value={project?.projectId || ""} label="Project key" placeholder={"Project Key"} requiredIndicator
                                onChange={(val)=> projectKeyChangeHandler(index,val)} />
                            {loadingProjectIndex === index ? (
                                <div style={{ display: 'flex', alignItems: 'center', margin: '8px 0' }}>
                                    <Spinner size="small" />
                                    <Text variant="bodyMd" as="span" style={{ marginLeft: '8px' }}>&nbsp;&nbsp;Loading status mappings...</Text>
                                    </div>
                            ) : (
                                <div style={{ display: 'flex', alignItems: 'center' }}>
                                    <Checkbox
                                        disabled={!project?.projectId?.trim()}
                                        checked={project.enableBiDirIntegration}
                                        onChange={() => {
                                            if (project?.projectId?.trim()) {
                                                fetchJiraStatusMapping(project.projectId, index);
                                            }
                                        }}
                                        label=""
                                    />
                                    <span style={{ marginLeft: '4px', opacity: project?.projectId?.trim() ? 1 : 0.5 }}>
                                        Enable bi-directional integration
                                    </span>
                                    </div>
                            )}
                            {project.enableBiDirIntegration &&
                                <VerticalStack gap={3} align='start'>
                                    <HorizontalStack gap={12}>
                                        <Text fontWeight='semibold' variant='headingXs'>Akto Status</Text>
                                        <HorizontalStack gap={0}>
                                            <Text fontWeight='semibold' variant='headingXs'>Jira Status </Text>
                                            <Text fontWeight='semibold' variant='headingXs' color="critical">*</Text>
                                        </HorizontalStack>
                                    </HorizontalStack>
                                    {
                                        aktoStatusForJira.map(val => {
                                            return (
                                                <HorizontalStack key={`status-${val}`} gap={8}>
                                                    <Box width='82px'><Badge >{val}</Badge></Box>
                                                    <Dropdown
                                                        id={`akto-status-${project.projectId}-${val}`}
                                                        selected={(value) => {
                                                            handleStatusSelection(index, project, val, value);
                                                        }}
                                                        menuItems={project?.jiraStatusLabel || []}
                                                        placeholder="Select Jira Status"
                                                        showSelectedItemLabels={true}
                                                        allowMultiple={true}
                                                        preSelected={project?.aktoToJiraStatusMap?.[val?.toUpperCase()] || []}
                                                        value={func.getSelectedItemsText(getLabel(project?.aktoToJiraStatusMap?.[val?.toUpperCase()], project) || [])}
                                                        disabledOptions={getDisabledOptions(project, val.toUpperCase())} />
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

      if (baseUrl !== initialFormData.baseUrl ||
          apiToken !== initialFormData.apiToken ||
          userEmail !== initialFormData.userEmail) {
        return true;
      }

      const currentData = transformJiraObject();
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

        if (initialProject.biDirectionalSyncSettings?.enabled !==
            currentProject.biDirectionalSyncSettings?.enabled) {
          return true;
        }

        if (currentProject.biDirectionalSyncSettings?.enabled) {
          const initialMappings = initialProject.biDirectionalSyncSettings?.aktoStatusMappings
              || {};
          const currentMappings = currentProject.biDirectionalSyncSettings?.aktoStatusMappings
              || {};

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

        if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim()) {
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

    const SeverityMappingCard = (
        <Card roundedAbove="sm">
            <VerticalStack gap={4}>
                <HorizontalStack align='space-between'>
                    <Text fontWeight='semibold' variant='headingMd'>Map Severity</Text>
                    <Button
                        size='slim'
                        variant={hasSeverityMappingChanged() ? 'primary' : undefined}
                        onClick={saveSeverityMapping}
                        disabled={isSavingSeverityMapping || isLoadingPriorities || !Array.isArray(jiraPriorities) || jiraPriorities.length === 0 || !hasSeverityMappingChanged()}
                        loading={isSavingSeverityMapping}
                    >
                        {isSavingSeverityMapping ? 'Saving...' : 'Save Mapping'}
                    </Button>
                </HorizontalStack>
                {isLoadingPriorities ? (
                    <HorizontalStack gap="2" align="center">
                        <Spinner size="small" />
                        <Text variant="bodyMd" as="span">Loading Jira priorities...</Text>
                    </HorizontalStack>
                ) : !Array.isArray(jiraPriorities) || jiraPriorities.length === 0 ? (
                    <Text variant="bodyMd" color="subdued">
                        Please configure Jira credentials above to load priorities
                    </Text>
                ) : (
                    <VerticalStack gap={3} align='start'>
                        <HorizontalStack gap={12}>
                            <Text fontWeight='semibold' variant='headingXs'>Akto Severity</Text>
                            <Text fontWeight='semibold' variant='headingXs'>Jira Priority</Text>
                        </HorizontalStack>
                        {aktoSeverities.map(severity => {
                            const priorityOptions = (Array.isArray(jiraPriorities) ? jiraPriorities : []).map(p => ({
                                label: p.name,
                                value: p.id
                            }));
                            const selectedPriorityId = severityToPriorityMap[severity];
                            const selectedPriority = (Array.isArray(jiraPriorities) ? jiraPriorities : []).find(p => p.id === selectedPriorityId);

                            // Build display value for the dropdown
                            let displayValue = "Select Priority";
                            if (selectedPriority && selectedPriority.name) {
                                displayValue = selectedPriority.name;
                            } else if (selectedPriorityId) {
                                // If we have an ID but can't find the priority, show the ID
                                displayValue = `Priority (${selectedPriorityId})`;
                            }

                            return (
                                <HorizontalStack key={`severity-${severity}`} gap={8}>
                                    <Box width='82px'>
                                        <Badge status={getSeverityBadgeColor(severity)}>
                                            {severity.charAt(0) + severity.slice(1).toLowerCase()}
                                        </Badge>
                                    </Box>
                                    <Dropdown
                                        id={`severity-${severity}`}
                                        selected={(value) => {
                                            handleSeverityMappingChange(severity, value[0]);
                                        }}
                                        menuItems={priorityOptions}
                                        placeholder="Select Jira Priority"
                                        showSelectedItemLabels={true}
                                        allowMultiple={false}
                                        preSelected={selectedPriorityId ? [selectedPriorityId] : []}
                                        value={displayValue}
                                    />
                                </HorizontalStack>
                            );
                        })}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Card>
    );

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
                    {/* Simple Jira Type Selector */}
                    <VerticalStack gap={2}>
                        <Text variant="bodyMd" fontWeight="semibold">Deployment Type</Text>
                        <HorizontalStack gap={4}>
                            <RadioButton
                                label="Jira Cloud (API v3)"
                                checked={jiraType === 'CLOUD' || !jiraType}
                                id="jira-cloud"
                                name="jiraType"
                                onChange={() => actions.setJiraType('CLOUD')}
                            />
                            <RadioButton
                                label="Jira Data Center (API v2)"
                                checked={jiraType === 'DATA_CENTER'}
                                id="jira-datacenter"
                                name="jiraType"
                                onChange={() => actions.setJiraType('DATA_CENTER')}
                            />
                        </HorizontalStack>
                        <Text variant="bodySm" color="subdued">
                            {jiraType === 'DATA_CENTER' 
                                ? 'For self-hosted Jira (e.g., http://localhost:8081)' 
                                : 'For Jira Cloud (e.g., https://your-domain.atlassian.net)'}
                        </Text>
                    </VerticalStack>
                    
                    <TextField 
                        label="Base Url" 
                        value={baseUrl} 
                        helpText="Specify the base url of your jira project" 
                        placeholder={jiraType === 'DATA_CENTER' ? 'http://localhost:8081' : 'https://your-domain.atlassian.net'} 
                        requiredIndicator 
                        onChange={(value) => actions.setCredentials('baseUrl', value)} 
                    />
                    <TextField 
                        label="Email" 
                        value={userEmail} 
                        helpText="Specify your email id for which api token will be generated" 
                        placeholder='Email' 
                        requiredIndicator 
                        onChange={(value) => actions.setCredentials('userEmail', value)} 
                    />
                    <PasswordTextField 
                        label="Api Token" 
                        helpText="Specify the api token created for your user email" 
                        field={apiToken} 
                        onFunc={true} 
                        setField={(value) => actions.setCredentials('apiToken', value)} 
                    />
                    <HorizontalStack align='space-between'>
                        <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
                        <Button plain monochrome onClick={() => actions.addProject()}>Add Project</Button>
                    </HorizontalStack>
                    {projects.length !== 0 ? ProjectsCard : null}
                    <Divider />
                    {SeverityMappingCard}
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
