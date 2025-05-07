import { useEffect, useCallback } from 'react';
import settingFunctions from '../../../module';
import api from '../../../api';
import func from "@/util/func";
import { INITIAL_EMPTY_MAPPING, AKTO_STATUS_FOR_JIRA } from '../constants';

/**
 * Custom hook for handling Jira integration API calls
 * @param {Object} state - Current state
 * @param {Object} actions - Action functions
 * @returns {Object} - API functions
 */
export default function useJiraIntegration(state, actions) {
  const {
    credentials: { baseUrl, apiToken, userEmail },
    projects,
    existingProjectIds,
    loadingProjectIndex
  } = state;

  const {
    setCredentials,
    addProject,
    updateProject,
    clearProjects,
    setProjects,
    setExistingProjectIds,
    setIsAlreadyIntegrated,
    setIsSaving,
    setInitialFormData,
    setLoadingProjectIndex,
    removeProject
  } = actions;

  /**
   * Fetches Jira integration data
   */
  const fetchJiraInteg = useCallback(async () => {
    const jiraInteg = await settingFunctions.fetchJiraIntegration();
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
  }, [addProject, setCredentials, setInitialFormData, setIsAlreadyIntegrated]);

  /**
   * Updates the project map based on Jira integration data
   * @param {Object} jiraInteg - Jira integration data
   */
  const updateProjectMap = useCallback((jiraInteg) => {
    clearProjects();
    const projectMappings = jiraInteg?.projectMappings ?? {};
    let projectIds = new Set();
    const newProjects = [];

    Object.entries(projectMappings).forEach(([projectId, projectMapping]) => {
      projectIds.add(projectId);

      // Use status names directly for the UI
      const aktoToJiraStatusMap = JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING));
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

    Object.entries(jiraInteg?.projectIdsMap || {}).forEach(([projectId]) => {
      if (!projectIds.has(projectId)) {
        newProjects.push({
          projectId,
          enableBiDirIntegration: false,
          aktoToJiraStatusMap: JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING)),
          statuses: [],
          jiraStatusLabel: []
        });
      }
      projectIds.add(projectId);
    });

    setProjects(newProjects);
    setExistingProjectIds(Array.from(projectIds));
  }, [clearProjects, setExistingProjectIds, setProjects]);

  /**
   * Fetches Jira status mapping for a project
   * @param {string} projId - Project ID
   * @param {number} index - Project index
   */
  const fetchJiraStatusMapping = useCallback((projId, index) => {
    if (projects[index]?.enableBiDirIntegration) {
      updateProject(index, {
        enableBiDirIntegration: false,
        aktoToJiraStatusMap: JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING)),
      });
      return;
    }

    if (!baseUrl?.trim() || !userEmail?.trim() || !apiToken?.trim() || !projId?.trim()) {
      func.setToast(true, true, "Please fill all required fields");
      return;
    }

    if (projects[index]?.statuses?.length > 0) {
      const aktoToJiraStatusMap = projects[index]?.aktoToJiraStatusMap || JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING));
      const jiraStatusLabel = projects[index]?.jiraStatusLabel ||
        projects[index]?.statuses?.map(x => {
          return { "label": x?.name ?? "", "value": x?.name ?? "" }
        }) || [];

      updateProject(index, {
        enableBiDirIntegration: true,
        aktoToJiraStatusMap,
        jiraStatusLabel
      });
      return;
    }

    setLoadingProjectIndex(index);

    api.fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken)
      .then((res) => {
        const jiraStatusLabel = res[projId]?.statuses?.map(x => {
          return { "label": x?.name ?? "", "value": x?.name ?? "" }
        });
        const aktoToJiraStatusMap = JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING));

        if (jiraStatusLabel.length > 0) {
          AKTO_STATUS_FOR_JIRA.forEach((status, index) => {
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
      })
      .catch(err => {
        func.setToast(true, true, "Failed to fetch Jira statuses. Verify Project ID");
        setLoadingProjectIndex(null);
      });
  }, [apiToken, baseUrl, projects, setLoadingProjectIndex, updateProject, userEmail]);

  /**
   * Adds or updates Jira integration
   * @param {Object} data - Jira integration data
   */
  const addJiraIntegrationV2 = useCallback(async (data) => {
    if (!data) return;

    setIsSaving(true);
    try {
      const res = await api.addJiraIntegrationV2(data);
      setIsAlreadyIntegrated(true);
      updateProjectMap(res);

      setInitialFormData({
        baseUrl: data.baseUrl,
        apiToken: data.apiToken,
        userEmail: data.userEmail,
        projectMappings: data.projectMappings,
      });

      func.setToast(true, false, "Jira configurations saved successfully");
    } catch (error) {
      func.setToast(true, true, "Failed to save Jira configurations check all required fields");
    } finally {
      setIsSaving(false);
    }
  }, [setInitialFormData, setIsAlreadyIntegrated, setIsSaving, updateProjectMap]);

  /**
   * Deletes a Jira project
   * @param {number} index - Project index
   */
  const deleteProject = useCallback(async (index) => {
    if (loadingProjectIndex !== null || state.isSaving) {
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
          if (state.initialFormData) {
            const updatedProjectMappings = { ...state.initialFormData.projectMappings };
            delete updatedProjectMappings[projectId];
            setInitialFormData({
              ...state.initialFormData,
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
  }, [existingProjectIds, loadingProjectIndex, projects, removeProject, setInitialFormData, setLoadingProjectIndex, state.initialFormData, state.isSaving]);

  // Initialize data on component mount
  useEffect(() => {
    fetchJiraInteg();
  }, [fetchJiraInteg]);

  return {
    fetchJiraInteg,
    updateProjectMap,
    fetchJiraStatusMapping,
    addJiraIntegrationV2,
    deleteProject
  };
}
