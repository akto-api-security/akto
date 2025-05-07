import { AKTO_STATUS_FOR_JIRA, INITIAL_EMPTY_MAPPING } from '../constants';
import func from "@/util/func";

/**
 * Validates status mappings for a project
 * @param {Object} project - The project to validate
 * @returns {Object} - Validation result with isValid and message
 */
export function validateStatusMappings(project) {
  if (!project?.enableBiDirIntegration) {
    return { isValid: true, message: '' };
  }

  const aktoToJiraStatusMap = project?.aktoToJiraStatusMap || {};

  // Check if all required statuses have values
  for (const status of AKTO_STATUS_FOR_JIRA) {
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

/**
 * Gets the label for a value
 * @param {Array} value - The value to get the label for
 * @param {Object} project - The project containing the label mapping
 * @returns {Array} - The labels for the value
 */
export function getLabel(value, project) {
  if (!value || !Array.isArray(value)) return [];

  return value?.map((x) => {
    const match = project?.jiraStatusLabel?.find((y) => y.value === x);
    return match ? match.label : x;
  }).filter(Boolean);
}

/**
 * Gets the disabled options for a dropdown
 * @param {Object} project - The project containing the status mappings
 * @param {string} currentStatus - The current status being edited
 * @returns {Array} - The disabled options
 */
export function getDisabledOptions(project, currentStatus) {
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

/**
 * Transforms the Jira object for API submission
 * @param {Object} state - The current state
 * @returns {Object|null} - The transformed object or null if validation fails
 */
export function transformJiraObject(state) {
  const { credentials: { baseUrl, apiToken, userEmail }, projects } = state;

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
        func.setToast(true, true, validation.message);
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
  });
  
  return { apiToken, userEmail, baseUrl, projectMappings };
}

/**
 * Checks if the form has changes compared to the initial data
 * @param {Object} state - The current state
 * @returns {boolean} - Whether the form has changes
 */
export function hasFormChanges(state) {
  const { credentials: { baseUrl, apiToken, userEmail }, initialFormData } = state;
  
  if (!initialFormData) {
    return true;
  }

  if (baseUrl !== initialFormData.baseUrl ||
      apiToken !== initialFormData.apiToken ||
      userEmail !== initialFormData.userEmail) {
    return true;
  }

  const currentData = transformJiraObject(state);
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

/**
 * Checks if the save button should be disabled
 * @param {Object} state - The current state
 * @returns {boolean} - Whether the save button should be disabled
 */
export function isSaveButtonDisabled(state) {
  const { credentials: { baseUrl, apiToken, userEmail }, projects, isSaving } = state;
  
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

  if (!hasFormChanges(state)) {
    return true;
  }

  return false;
}
