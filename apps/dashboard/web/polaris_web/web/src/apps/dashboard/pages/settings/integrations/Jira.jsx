import React, {useEffect, useReducer} from 'react'
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

const aktoStatusForJira = ["Open","Fixed", "Ignored"]
const intialEmptyMapping = aktoStatusForJira.reduce((acc, status) => {
    acc[status.toUpperCase()] = [];
    return acc;
  }, {});

// Define initial state for the form
const initialState = {
  credentials: {
    baseUrl: '',
    apiToken: '',
    userEmail: ''
  },
  projects: [],
  existingProjectIds: [],
  isAlreadyIntegrated: false,
  isSaving: false,
  initialFormData: null,
  projectIssueMap: {},
  loadingProjectIndex: null
};

// Define action types
const ACTION_TYPES = {
  SET_CREDENTIALS: 'SET_CREDENTIALS',
  SET_PROJECTS: 'SET_PROJECTS',
  ADD_PROJECT: 'ADD_PROJECT',
  REMOVE_PROJECT: 'REMOVE_PROJECT',
  UPDATE_PROJECT: 'UPDATE_PROJECT',
  SET_EXISTING_PROJECT_IDS: 'SET_EXISTING_PROJECT_IDS',
  SET_IS_ALREADY_INTEGRATED: 'SET_IS_ALREADY_INTEGRATED',
  SET_IS_SAVING: 'SET_IS_SAVING',
  SET_INITIAL_FORM_DATA: 'SET_INITIAL_FORM_DATA',
  SET_PROJECT_ISSUE_MAP: 'SET_PROJECT_ISSUE_MAP',
  CLEAR_PROJECTS: 'CLEAR_PROJECTS',
  SET_LOADING_PROJECT_INDEX: 'SET_LOADING_PROJECT_INDEX'
};

// Main reducer function
function jiraReducer(state, action) {
  switch (action.type) {
    case ACTION_TYPES.SET_CREDENTIALS:
      return {
        ...state,
        credentials: {
          ...state.credentials,
          ...action.payload
        }
      };

    case ACTION_TYPES.SET_PROJECTS:
      return {
        ...state,
        projects: action.payload
      };

    case ACTION_TYPES.ADD_PROJECT:
      return {
        ...state,
        projects: [
          ...state.projects,
          {
            projectId: "",
            enableBiDirIntegration: false,
            aktoToJiraStatusMap: JSON.parse(JSON.stringify(intialEmptyMapping)),
            statuses: [],
            jiraStatusLabel: []
          }
        ]
      };

    case ACTION_TYPES.REMOVE_PROJECT:
      return {
        ...state,
        projects: state.projects.filter((_, index) => index !== action.payload)
      };

    case ACTION_TYPES.UPDATE_PROJECT:
      return {
        ...state,
        projects: state.projects.map((project, index) => {
          if (index === action.payload.index) {
            // Handle special case for aktoToJiraStatusMap
            if (action.payload.updates.aktoToJiraStatusMap) {
              return {
                ...project,
                ...action.payload.updates,
                aktoToJiraStatusMap: {
                  ...project.aktoToJiraStatusMap,
                  ...action.payload.updates.aktoToJiraStatusMap
                }
              };
            }
            return {
              ...project,
              ...action.payload.updates
            };
          }
          return project;
        })
      };

    case ACTION_TYPES.SET_EXISTING_PROJECT_IDS:
      return {
        ...state,
        existingProjectIds: action.payload
      };

    case ACTION_TYPES.SET_IS_ALREADY_INTEGRATED:
      return {
        ...state,
        isAlreadyIntegrated: action.payload
      };

    case ACTION_TYPES.SET_IS_SAVING:
      return {
        ...state,
        isSaving: action.payload
      };

    case ACTION_TYPES.SET_INITIAL_FORM_DATA:
      return {
        ...state,
        initialFormData: action.payload
      };

    case ACTION_TYPES.SET_PROJECT_ISSUE_MAP:
      return {
        ...state,
        projectIssueMap: action.payload
      };

    case ACTION_TYPES.CLEAR_PROJECTS:
      return {
        ...state,
        projects: []
      };

    case ACTION_TYPES.SET_LOADING_PROJECT_INDEX:
      return {
        ...state,
        loadingProjectIndex: action.payload
      };

    default:
      return state;
  }
}

function Jira() {
    // Use our new reducer for state management
    const [state, dispatch] = useReducer(jiraReducer, initialState);

    // Destructure state for easier access
    const {
        credentials: { baseUrl, apiToken, userEmail },
        projects,
        existingProjectIds,
        isSaving,
        initialFormData,
        loadingProjectIndex
    } = state;

    // Helper functions to dispatch actions
    const setCredentials = (field, value) => {
        dispatch({
            type: ACTION_TYPES.SET_CREDENTIALS,
            payload: { [field]: value }
        });
    };

    const addProject = () => {
        dispatch({ type: ACTION_TYPES.ADD_PROJECT });
    };

    const removeProject = (index) => {
        dispatch({
            type: ACTION_TYPES.REMOVE_PROJECT,
            payload: index
        });
    };

    const updateProject = (index, updates) => {
        dispatch({
            type: ACTION_TYPES.UPDATE_PROJECT,
            payload: { index, updates }
        });
    };

    const clearProjects = () => {
        dispatch({ type: ACTION_TYPES.CLEAR_PROJECTS });
    };

    const setProjects = (projects) => {
        dispatch({
            type: ACTION_TYPES.SET_PROJECTS,
            payload: projects
        });
    };

    const setExistingProjectIds = (ids) => {
        dispatch({
            type: ACTION_TYPES.SET_EXISTING_PROJECT_IDS,
            payload: ids
        });
    };

    const setIsAlreadyIntegrated = (value) => {
        dispatch({
            type: ACTION_TYPES.SET_IS_ALREADY_INTEGRATED,
            payload: value
        });
    };

    const setIsSaving = (value) => {
        dispatch({
            type: ACTION_TYPES.SET_IS_SAVING,
            payload: value
        });
    };

    const setInitialFormData = (data) => {
        dispatch({
            type: ACTION_TYPES.SET_INITIAL_FORM_DATA,
            payload: data
        });
    };
  const setLoadingProjectIndex = (index) => {
        dispatch({
            type: ACTION_TYPES.SET_LOADING_PROJECT_INDEX,
            payload: index
        });
    };


    async function fetchJiraInteg() {
        let jiraInteg = await settingFunctions.fetchJiraIntegration();
        if (jiraInteg !== null) {
            setIsAlreadyIntegrated(true);

            // Update credentials
            setCredentials('baseUrl', jiraInteg.baseUrl);
            setCredentials('apiToken', jiraInteg.apiToken);
            setCredentials('userEmail', jiraInteg.userEmail);

            // Update projects
            updateProjectMap(jiraInteg);

            // Store initial form data for change detection
            setInitialFormData({
                baseUrl: jiraInteg.baseUrl,
                apiToken: jiraInteg.apiToken,
                userEmail: jiraInteg.userEmail,
                projectMappings: jiraInteg.projectMappings || {}
            });
        } else {
            // If integration is not present, add a default empty project
            addProject();
        }
    }

    function updateProjectMap(jiraInteg){
        clearProjects();
        const projectMappings = jiraInteg?.projectMappings ?? {};
        let projectIds = new Set();
        const newProjects = [];

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
                    aktoToJiraStatusMap: JSON.parse(JSON.stringify(intialEmptyMapping)),
                    statuses: [],
                    jiraStatusLabel: []
                });
            }
            projectIds.add(projectId);
        });

        setProjects(newProjects);
        setExistingProjectIds(Array.from(projectIds));
    }


    function fetchJiraStatusMapping(projId, index) {
      if (projects[index]?.enableBiDirIntegration) {
        updateProject(index, {
          enableBiDirIntegration: false,
          aktoToJiraStatusMap: JSON.parse(JSON.stringify(intialEmptyMapping)),
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
            || JSON.parse(JSON.stringify(intialEmptyMapping));
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

        updateProject(index, {
          enableBiDirIntegration: true,
          aktoToJiraStatusMap,
          jiraStatusLabel
        });
        return;
      }

      setLoadingProjectIndex(index);

      api.fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken).then(
          (res) => {
            const jiraStatusLabel = res[projId]?.statuses?.map(x => {
              return {"label": x?.name ?? "", "value": x?.name ?? ""}
            });
            const aktoToJiraStatusMap = JSON.parse(
                JSON.stringify(intialEmptyMapping));

            if (jiraStatusLabel.length > 0) {
              aktoStatusForJira.forEach((status, index) => {
                const upperStatus = status.toUpperCase();
                const statusIndex = Math.min(index, jiraStatusLabel.length - 1);
                aktoToJiraStatusMap[upperStatus] = [jiraStatusLabel[statusIndex].value];
              });
            }

            updateProject(index, {
              statuses: res[projId].statuses,
              jiraStatusLabel,
              aktoToJiraStatusMap,
              enableBiDirIntegration: true
            });

            setLoadingProjectIndex(null);
          }).catch(err => {
        func.setToast(true, true,
            "Failed to fetch Jira statuses. Verify Project ID");
        setLoadingProjectIndex(null);
      });
    }

    useEffect(() => {
        fetchJiraInteg()
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

            setInitialFormData({
                baseUrl: data.baseUrl,
                apiToken: data.apiToken,
                userEmail: data.userEmail,
                projectMappings: data.projectMappings,
            });

            func.setToast(true, false, "Jira configurations saved successfully");
        }).catch(() => {
            func.setToast(true, true, "Failed to save Jira configurations check all required fields");
        }).finally(() => {
            setIsSaving(false);
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

        // Check for duplicate status assignments
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

        // Collect all selected statuses from other dropdowns
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

        // Create a new copy of the project to ensure React detects the change
        const updatedProject = JSON.parse(JSON.stringify(project));

        // Update the status mapping for this Akto status
        updatedProject.aktoToJiraStatusMap[upperStatus] = newValues;

        // Replace the entire project to ensure React re-renders all dropdowns
        const newProjects = [...projects];
        newProjects[index] = updatedProject;

        // Update the state with the new projects array
        setProjects(newProjects);
    }

    async function deleteProject(index) {
        if (loadingProjectIndex !== null || isSaving) {
            func.setToast(true, true, "Please wait for the current operation to complete.");
            return;
        }

        const projectId = projects[index]?.projectId;
        if (!projectId?.trim()) {
            removeProject(index);
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
                setLoadingProjectIndex(index);
                await api.deleteJiraIntegratedProject(projectId).then((res) => {
                  if (initialFormData) {
                    const updatedProjectMappings = { ...initialFormData.projectMappings };
                    delete updatedProjectMappings[projectId];
                    initialFormData.projectMappings = updatedProjectMappings;
                    setInitialFormData({
                      ...initialFormData,
                      projectMappings: updatedProjectMappings
                    });
                  }
                });
                removeProject(index);
                setLoadingProjectIndex(null);
                func.setToast(true, false, "Project removed successfully");
            } catch (error) {
                setLoadingProjectIndex(null);
                func.setToast(true, true, `Failed to delete project: ${error.message || 'Unknown error'}`);
            }
        } else {
            removeProject(index);
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

      updateProject(index, {
        projectId: val,
        statuses: [],
        jiraStatusLabel: [],
        aktoToJiraStatusMap: JSON.parse(JSON.stringify(intialEmptyMapping)),
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
                    <TextField label="Base Url" value={baseUrl} helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)"  placeholder='Base Url' requiredIndicator onChange={(value) => setCredentials('baseUrl', value)} />
                    <TextField label="Email" value={userEmail} helpText="Specify your email id for which api token will be generated" placeholder='Email' requiredIndicator onChange={(value) => setCredentials('userEmail', value)} />
                    <PasswordTextField label="Api Token" helpText="Specify the api token created for your user email" field={apiToken} onFunc={true} setField={(value) => setCredentials('apiToken', value)} />
                    <HorizontalStack align='space-between'>
                        <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
                        <Button plain monochrome onClick={addProject}>Add Project</Button>
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
