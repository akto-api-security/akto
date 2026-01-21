import React, {useEffect} from 'react'
import {
  Autocomplete,
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
  useJiraReducer,
  initialEmptyMapping,
  aktoStatusForJira,
  aktoSeverities
} from './reducers/useJiraReducer';

function Jira() {
    const { state, actions } = useJiraReducer();

    const {
        credentials: { baseUrl, apiToken, userEmail },
        projects,
        existingProjectIds,
        isSaving,
        initialFormData,
        loadingProjectIndex,
        isAlreadyIntegrated,
        loadingFieldsProjectIndex,
        loadingFieldValuesProjectIndex
    } = state;


    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        if (jiraInteg !== null) {
            actions.setIsAlreadyIntegrated(true);

            actions.setCredentials('baseUrl', jiraInteg.baseUrl);
            actions.setCredentials('apiToken', jiraInteg.apiToken);
            actions.setCredentials('userEmail', jiraInteg.userEmail);

            updateProjectMap(jiraInteg);

            actions.setInitialFormData({
                baseUrl: jiraInteg.baseUrl,
                apiToken: jiraInteg.apiToken,
                userEmail: jiraInteg.userEmail,
                projectMappings: jiraInteg.projectMappings || {}
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

            // Load priority field mapping if exists
            const priorityFieldMapping = projectMapping?.priorityFieldMapping || {
                fieldId: "priority",
                fieldName: "Priority",
                fieldType: "priority",
                severityToValueMap: {},
                severityToDisplayNameMap: {}
            };

            // Ensure severityToDisplayNameMap exists for backwards compatibility
            if (priorityFieldMapping && !priorityFieldMapping.severityToDisplayNameMap) {
                priorityFieldMapping.severityToDisplayNameMap = {};
            }

            const hasValidMapping = priorityFieldMapping?.severityToValueMap &&
                Object.keys(priorityFieldMapping.severityToValueMap).length > 0;

            newProjects.push({
                projectId,
                enableBiDirIntegration: isBidirectionalEnabled,
                aktoToJiraStatusMap,
                statuses: isBidirectionalEnabled ? statuses : null,
                jiraStatusLabel,
                priorityFieldMapping,
                availableFields: [],
                availableFieldValues: [],
                enablePriorityMapping: false,  // Always start in view mode
                hasSavedMapping: hasValidMapping  // Track if mapping exists
            });
        });

        Object.entries(jiraInteg?.projectIdsMap||{}).forEach(([projectId, issueMapping], index) => {
            if(!projectIds.has(projectId)){
                newProjects.push({
                    projectId,
                    enableBiDirIntegration: false,
                    aktoToJiraStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
                    statuses: [],
                    jiraStatusLabel: [],
                    priorityFieldMapping: {
                        fieldId: "priority",
                        fieldName: "Priority",
                        fieldType: "priority",
                        severityToValueMap: {},
                        severityToDisplayNameMap: {}
                    },
                    availableFields: [],
                    availableFieldValues: [],
                    enablePriorityMapping: false,
                    hasSavedMapping: false
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



    function getSeverityBadgeColor(severity) {
        switch(severity) {
            case 'CRITICAL': return 'critical';
            case 'HIGH': return 'warning';
            case 'MEDIUM': return 'attention';
            case 'LOW': return 'info';
            default: return 'default';
        }
    }


    useEffect(() => {
        fetchJiraInteg();
    }, []);


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
                statuses: project?.statuses || [],
                priorityFieldMapping: project?.enablePriorityMapping && project?.priorityFieldMapping
                    ? project.priorityFieldMapping
                    : null
            };
        })
        return {apiToken, userEmail, baseUrl, projectMappings};
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

    // Priority field mapping handlers
    async function fetchAvailableFields(projectKey, index) {
        if (loadingFieldsProjectIndex !== null) {
            return;
        }

        try {
            actions.setLoadingFieldsProjectIndex(index);
            const response = await api.fetchAvailableFieldsForMapping(projectKey);

            // Handle both wrapped and unwrapped responses
            const fields = Array.isArray(response) ? response : response?.availableFields;

            if (fields && fields.length > 0) {
                actions.updateProject(index, {
                    availableFields: fields
                });
            }
        } catch (error) {
            func.setToast(true, true, `Failed to fetch fields: ${error.message || 'Unknown error'}`);
        } finally {
            actions.setLoadingFieldsProjectIndex(null);
        }
    }

    async function fetchFieldValues(projectKey, fieldId, index) {
        if (loadingFieldValuesProjectIndex !== null) {
            return;
        }

        try {
            actions.setLoadingFieldValuesProjectIndex(index);
            const response = await api.fetchFieldValues(projectKey, fieldId);

            // Handle both wrapped and unwrapped responses
            const values = Array.isArray(response) ? response : response?.availableFieldValues;

            if (values && values.length > 0) {
                actions.updateProject(index, {
                    availableFieldValues: values
                });
            }
        } catch (error) {
            func.setToast(true, true, `Failed to fetch field values: ${error.message || 'Unknown error'}`);
        } finally {
            actions.setLoadingFieldValuesProjectIndex(null);
        }
    }

    async function handleStartPriorityMapping(index, project) {
        if (!project.projectId) {
            func.setToast(true, true, "Please enter project key first");
            return;
        }

        // Enable the mapping section
        actions.updateProject(index, {
            enablePriorityMapping: true
        });

        // Fetch available fields (priority, custom fields)
        await fetchAvailableFields(project.projectId, index);

        // Auto-fetch values for the default "priority" field
        const defaultFieldId = project.priorityFieldMapping?.fieldId || "priority";
        fetchFieldValues(project.projectId, defaultFieldId, index);
    }

    function handleCancelPriorityMapping(index, project) {
        // If there's a saved mapping, just close edit mode
        // Otherwise, reset to default
        if (project.hasSavedMapping) {
            actions.updateProject(index, {
                enablePriorityMapping: false,
                availableFields: [],
                availableFieldValues: []
            });
        } else {
            actions.updateProject(index, {
                enablePriorityMapping: false,
                availableFields: [],
                availableFieldValues: [],
                priorityFieldMapping: {
                    fieldId: "priority",
                    fieldName: "Priority",
                    fieldType: "priority",
                    severityToValueMap: {},
                    severityToDisplayNameMap: {}
                }
            });
        }
    }

    function handleFieldSelection(index, project, fieldId) {
        const selectedField = project.availableFields.find(f => f.id === fieldId);

        actions.updateProject(index, {
            priorityFieldMapping: {
                ...project.priorityFieldMapping,
                fieldId: fieldId,
                fieldName: selectedField?.name || fieldId,
                fieldType: selectedField?.type || "unknown"
            },
            fieldSearchQuery: undefined
        });

        // Fetch values for the selected field
        if (project.projectId) {
            fetchFieldValues(project.projectId, fieldId, index);
        }
    }

    async function handleSavePriorityMapping(index, project) {
        try {
            const { projectId, priorityFieldMapping } = project;

            if (!projectId || !priorityFieldMapping?.fieldId) {
                func.setToast(true, true, "Please select a priority field");
                return;
            }

            // Check if at least one severity is mapped
            const hasMappings = priorityFieldMapping.severityToValueMap &&
                Object.keys(priorityFieldMapping.severityToValueMap).length > 0;

            if (!hasMappings) {
                func.setToast(true, true, "Please map at least one severity level");
                return;
            }

            await api.savePriorityFieldMapping(projectId, priorityFieldMapping);

            // Update the project to mark it as saved and close edit mode
            actions.updateProject(index, {
                enablePriorityMapping: false,
                hasSavedMapping: true,
                availableFields: [],
                availableFieldValues: []
            });

            func.setToast(true, false, "Priority field mapping saved successfully");
        } catch (error) {
            func.setToast(true, true, "Failed to save priority field mapping");
        }
    }

    function handlePriorityValueSelection(index, project, severity, value) {
        // Find the display name for this value
        const selectedValue = (project.availableFieldValues || []).find(v =>
            (v.id && v.id === value) || v.name === value
        );
        const displayName = selectedValue?.name || value;

        actions.updateProject(index, {
            priorityFieldMapping: {
                ...project.priorityFieldMapping,
                severityToValueMap: {
                    ...project.priorityFieldMapping.severityToValueMap,
                    [severity]: value
                },
                severityToDisplayNameMap: {
                    ...project.priorityFieldMapping.severityToDisplayNameMap,
                    [severity]: displayName
                }
            }
        });
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

                            {/* Priority Field Mapping Section */}
                            <Divider />
                            <VerticalStack gap={3} align='start'>
                                <HorizontalStack align='space-between'>
                                    <VerticalStack gap={1}>
                                        <Text fontWeight='semibold' variant='headingSm'>Priority Field Mapping (Optional)</Text>
                                        {!project.enablePriorityMapping && !project.hasSavedMapping && (
                                            <Text variant='bodyMd' color='subdued'>
                                                Map Akto severity to Jira priority values
                                            </Text>
                                        )}
                                    </VerticalStack>
                                    {project.enablePriorityMapping ? (
                                        <Button plain size='slim' onClick={() => handleCancelPriorityMapping(index, project)}>
                                            Cancel
                                        </Button>
                                    ) : project.hasSavedMapping ? (
                                        <Button
                                            size='slim'
                                            onClick={() => handleStartPriorityMapping(index, project)}
                                        >
                                            Edit Mapping
                                        </Button>
                                    ) : (
                                        <Button
                                            size='slim'
                                            disabled={!project?.projectId?.trim()}
                                            onClick={() => handleStartPriorityMapping(index, project)}
                                        >
                                            Configure Mapping
                                        </Button>
                                    )}
                                </HorizontalStack>

                                {/* Show saved mapping in view mode */}
                                {!project.enablePriorityMapping && project.hasSavedMapping && (
                                    <VerticalStack gap={2} align='start'>
                                        <HorizontalStack gap={2} align='center'>
                                            <Text variant='bodyMd' color='subdued'>Mapped to Jira field:</Text>
                                            <Text variant='bodyMd' fontWeight='semibold'>{project.priorityFieldMapping?.fieldName || project.priorityFieldMapping?.fieldId}</Text>
                                        </HorizontalStack>
                                        <Box width="100%">
                                            {/* Table Header */}
                                            <div style={{ display: 'flex', width: '100%', marginBottom: '12px' }}>
                                                <Box
                                                    padding="4"
                                                    background="bg-surface-secondary"
                                                    borderWidth="1"
                                                    borderColor="border"
                                                    borderRadius="2"
                                                    style={{ textAlign: 'center', flex: 1, marginRight: '4px' }}
                                                >
                                                    <Text variant='bodyMd' fontWeight='semibold' alignment='center'>Akto Severity</Text>
                                                </Box>
                                                <Box
                                                    padding="4"
                                                    background="bg-surface-secondary"
                                                    borderWidth="1"
                                                    borderColor="border"
                                                    borderRadius="2"
                                                    style={{ textAlign: 'center', flex: 1, marginLeft: '4px' }}
                                                >
                                                    <Text variant='bodyMd' fontWeight='semibold' alignment='center'>Jira Priority</Text>
                                                </Box>
                                            </div>

                                            {/* Table Rows */}
                                            {(() => {
                                                const mappedSeverities = aktoSeverities.filter(severity => {
                                                    const val = project.priorityFieldMapping?.severityToDisplayNameMap?.[severity] ||
                                                        project.priorityFieldMapping?.severityToValueMap?.[severity];
                                                    return !!val;
                                                });

                                                return mappedSeverities.map((severity, idx) => {
                                                    const mappedDisplayName = project.priorityFieldMapping?.severityToDisplayNameMap?.[severity] ||
                                                        project.priorityFieldMapping?.severityToValueMap?.[severity];
                                                    const isLastRow = idx === mappedSeverities.length - 1;

                                                    return (
                                                        <div key={`view-${severity}-${index}`} style={{ display: 'flex', width: '100%', marginBottom: isLastRow ? '0' : '8px' }}>
                                                            <Box
                                                                padding="4"
                                                                borderWidth="1"
                                                                borderColor="border"
                                                                borderRadius="2"
                                                                style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1, marginRight: '4px' }}
                                                            >
                                                                <Badge status={getSeverityBadgeColor(severity)}>
                                                                    {severity.charAt(0) + severity.slice(1).toLowerCase()}
                                                                </Badge>
                                                            </Box>
                                                            <Box
                                                                padding="4"
                                                                borderWidth="1"
                                                                borderColor="border"
                                                                borderRadius="2"
                                                                style={{ textAlign: 'center', flex: 1, marginLeft: '4px' }}
                                                            >
                                                                <Text variant='bodyMd' alignment='center'>{mappedDisplayName}</Text>
                                                            </Box>
                                                        </div>
                                                    );
                                                });
                                            })()}
                                        </Box>
                                    </VerticalStack>
                                )}

                                {loadingFieldsProjectIndex === index || loadingFieldValuesProjectIndex === index ? (
                                    <div style={{ display: 'flex', alignItems: 'center', margin: '8px 0' }}>
                                        <Spinner size="small" />
                                        <Text variant="bodyMd" as="span" style={{ marginLeft: '8px' }}>
                                            &nbsp;&nbsp;{loadingFieldsProjectIndex === index ? 'Loading fields...' : 'Loading values...'}
                                        </Text>
                                    </div>
                                ) : project.enablePriorityMapping && (
                                    <VerticalStack gap={3} align='start'>
                                        <Text variant='bodyMd' color='subdued'>
                                            Map Akto severity levels to Jira field values for automatic ticket creation
                                        </Text>

                                        {/* Field Selection - show if custom fields available */}
                                        {project.availableFields && project.availableFields.length > 1 && (
                                            <VerticalStack gap="2">
                                                <HorizontalStack gap="5" align="start">
                                                    <Box minWidth="110px" paddingBlockStart="1">
                                                        <Text variant='bodyMd' fontWeight='medium' alignment="start">Priority Field:</Text>
                                                    </Box>
                                                    <Box width="500px">
                                                        <Autocomplete
                                                            options={(project.availableFields || [])
                                                                .filter(f => {
                                                                    const searchQuery = project.fieldSearchQuery || '';
                                                                    if (!searchQuery) return true;
                                                                    const query = searchQuery.toLowerCase();
                                                                    return f.name.toLowerCase().includes(query) ||
                                                                           f.id.toLowerCase().includes(query) ||
                                                                           f.type.toLowerCase().includes(query);
                                                                })
                                                                .map(f => ({
                                                                    label: `${f.name} (${f.id})`,
                                                                    value: f.id
                                                                }))
                                                            }
                                                            selected={project.priorityFieldMapping?.fieldId ? [project.priorityFieldMapping.fieldId] : []}
                                                            onSelect={(selected) => {
                                                                if (selected.length > 0) {
                                                                    handleFieldSelection(index, project, selected[0]);
                                                                }
                                                            }}
                                                            textField={
                                                                <Autocomplete.TextField
                                                                    label=""
                                                                    value={project.fieldSearchQuery !== undefined ? project.fieldSearchQuery : (project.priorityFieldMapping?.fieldName || '')}
                                                                    onChange={(value) => {
                                                                        actions.updateProject(index, {
                                                                            fieldSearchQuery: value
                                                                        });
                                                                    }}
                                                                    placeholder="Type to search fields..."
                                                                    autoComplete="off"
                                                                    helpText="Click to change or type to search"
                                                                />
                                                            }
                                                        />
                                                    </Box>
                                                </HorizontalStack>
                                                <Box paddingInlineStart="32">
                                                    <Text variant='bodySm' color='subdued'>
                                                        Search by field name, ID, or type
                                                    </Text>
                                                </Box>
                                            </VerticalStack>
                                        )}

                                        {/* Severity Value Mapping */}
                                        {project.availableFieldValues && project.availableFieldValues.length > 0 && (
                                            <>
                                                <HorizontalStack gap={12}>
                                                    <Text fontWeight='semibold' variant='headingXs'>Akto Severity</Text>
                                                    <Text fontWeight='semibold' variant='headingXs'>Jira Value</Text>
                                                </HorizontalStack>
                                                {aktoSeverities.map(severity => {
                                                    const valueOptions = project.availableFieldValues.map(v => ({
                                                        label: v.name,
                                                        value: v.id || v.name
                                                    }));
                                                    const selectedValueId = project.priorityFieldMapping?.severityToValueMap?.[severity];
                                                    const selectedValue = project.availableFieldValues.find(v =>
                                                        (v.id && v.id === selectedValueId) || v.name === selectedValueId
                                                    );

                                                    return (
                                                        <HorizontalStack key={`severity-${severity}-${index}`} gap={8}>
                                                            <Box width='82px'>
                                                                <Badge status={getSeverityBadgeColor(severity)}>
                                                                    {severity.charAt(0) + severity.slice(1).toLowerCase()}
                                                                </Badge>
                                                            </Box>
                                                            <Dropdown
                                                                id={`severity-value-${project.projectId}-${severity}`}
                                                                selected={(value) => {
                                                                    const valueId = Array.isArray(value) ? value[0] : value;
                                                                    handlePriorityValueSelection(index, project, severity, valueId);
                                                                }}
                                                                menuItems={valueOptions}
                                                                placeholder="Select Value"
                                                                showSelectedItemLabels={true}
                                                                allowMultiple={false}
                                                                preSelected={selectedValueId ? [selectedValueId] : []}
                                                                value={selectedValue?.name || "Select Value"}
                                                            />
                                                        </HorizontalStack>
                                                    );
                                                })}
                                            </>
                                        )}

                                        {/* Save Button */}
                                        {project.availableFieldValues && project.availableFieldValues.length > 0 && (
                                            <HorizontalStack gap={2}>
                                                <Button
                                                    primary
                                                    onClick={() => handleSavePriorityMapping(index, project)}
                                                >
                                                    Save Mapping
                                                </Button>
                                            </HorizontalStack>
                                        )}
                                    </VerticalStack>
                                )}
                            </VerticalStack>
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
                    <TextField label="Base Url" value={baseUrl} helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)"  placeholder='Base Url' requiredIndicator onChange={(value) => actions.setCredentials('baseUrl', value)} />
                    <TextField label="Email" value={userEmail} helpText="Specify your email id for which api token will be generated" placeholder='Email' requiredIndicator onChange={(value) => actions.setCredentials('userEmail', value)} />
                    <PasswordTextField label="Api Token" helpText="Specify the api token created for your user email" field={apiToken} onFunc={true} setField={(value) => actions.setCredentials('apiToken', value)} />
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

    let cardContent = "Seamlessly enhance your web application security with Jira integration. Create jira tickets for api vulnerability issues and view them on the tap of a button"
    return (
        <IntegrationsLayout title="Jira" cardContent={cardContent} component={JCard} docsUrl="https://docs.akto.io/traffic-connections/postman" />
    )
}

export default Jira
