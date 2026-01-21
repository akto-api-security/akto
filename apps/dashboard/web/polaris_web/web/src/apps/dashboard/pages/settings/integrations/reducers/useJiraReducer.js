import { useReducer } from 'react';

export const aktoStatusForJira = ["Open", "Fixed", "Ignored"];
export const initialEmptyMapping = aktoStatusForJira.reduce((acc, status) => {
  acc[status.toUpperCase()] = [];
  return acc;
}, {});

export const aktoSeverities = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

const initialState = {
  credentials: {
    baseUrl: '',
    apiToken: '',
    userEmail: ''
  },
  jiraType: 'CLOUD', // 'CLOUD' or 'DATA_CENTER'
  projects: [],
  existingProjectIds: [],
  isAlreadyIntegrated: false,
  isSaving: false,
  initialFormData: null,
  loadingProjectIndex: null,
  jiraPriorities: [],
  severityToPriorityMap: {},
  initialSeverityMapping: {},
  isLoadingPriorities: false,
  isSavingSeverityMapping: false
};

const ACTION_TYPES = {
  SET_CREDENTIALS: 'SET_CREDENTIALS',
  SET_JIRA_TYPE: 'SET_JIRA_TYPE',
  SET_PROJECTS: 'SET_PROJECTS',
  ADD_PROJECT: 'ADD_PROJECT',
  REMOVE_PROJECT: 'REMOVE_PROJECT',
  UPDATE_PROJECT: 'UPDATE_PROJECT',
  SET_EXISTING_PROJECT_IDS: 'SET_EXISTING_PROJECT_IDS',
  SET_IS_ALREADY_INTEGRATED: 'SET_IS_ALREADY_INTEGRATED',
  SET_IS_SAVING: 'SET_IS_SAVING',
  SET_INITIAL_FORM_DATA: 'SET_INITIAL_FORM_DATA',
  CLEAR_PROJECTS: 'CLEAR_PROJECTS',
  SET_LOADING_PROJECT_INDEX: 'SET_LOADING_PROJECT_INDEX',
  SET_JIRA_PRIORITIES: 'SET_JIRA_PRIORITIES',
  SET_SEVERITY_TO_PRIORITY_MAP: 'SET_SEVERITY_TO_PRIORITY_MAP',
  SET_INITIAL_SEVERITY_MAPPING: 'SET_INITIAL_SEVERITY_MAPPING',
  UPDATE_SEVERITY_MAPPING: 'UPDATE_SEVERITY_MAPPING',
  SET_IS_LOADING_PRIORITIES: 'SET_IS_LOADING_PRIORITIES',
  SET_IS_SAVING_SEVERITY_MAPPING: 'SET_IS_SAVING_SEVERITY_MAPPING'
};

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

    case ACTION_TYPES.SET_JIRA_TYPE:
      return {
        ...state,
        jiraType: action.payload
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
            aktoToJiraStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
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

    case ACTION_TYPES.SET_JIRA_PRIORITIES:
      return {
        ...state,
        jiraPriorities: action.payload
      };

    case ACTION_TYPES.SET_SEVERITY_TO_PRIORITY_MAP:
      return {
        ...state,
        severityToPriorityMap: action.payload
      };

    case ACTION_TYPES.SET_INITIAL_SEVERITY_MAPPING:
      return {
        ...state,
        initialSeverityMapping: action.payload
      };

    case ACTION_TYPES.UPDATE_SEVERITY_MAPPING:
      return {
        ...state,
        severityToPriorityMap: {
          ...state.severityToPriorityMap,
          ...action.payload
        }
      };

    case ACTION_TYPES.SET_IS_LOADING_PRIORITIES:
      return {
        ...state,
        isLoadingPriorities: action.payload
      };

    case ACTION_TYPES.SET_IS_SAVING_SEVERITY_MAPPING:
      return {
        ...state,
        isSavingSeverityMapping: action.payload
      };

    default:
      return state;
  }
}

export function useJiraReducer() {
  const [state, dispatch] = useReducer(jiraReducer, initialState);

  const actions = {
    setCredentials: (field, value) => {
      dispatch({
        type: ACTION_TYPES.SET_CREDENTIALS,
        payload: { [field]: value }
      });
    },

    addProject: () => {
      dispatch({ type: ACTION_TYPES.ADD_PROJECT });
    },

    removeProject: (index) => {
      dispatch({
        type: ACTION_TYPES.REMOVE_PROJECT,
        payload: index
      });
    },

    updateProject: (index, updates) => {
      dispatch({
        type: ACTION_TYPES.UPDATE_PROJECT,
        payload: { index, updates }
      });
    },

    clearProjects: () => {
      dispatch({ type: ACTION_TYPES.CLEAR_PROJECTS });
    },

    setProjects: (projects) => {
      dispatch({
        type: ACTION_TYPES.SET_PROJECTS,
        payload: projects
      });
    },

    setExistingProjectIds: (ids) => {
      dispatch({
        type: ACTION_TYPES.SET_EXISTING_PROJECT_IDS,
        payload: ids
      });
    },

    setIsAlreadyIntegrated: (value) => {
      dispatch({
        type: ACTION_TYPES.SET_IS_ALREADY_INTEGRATED,
        payload: value
      });
    },

    setIsSaving: (value) => {
      dispatch({
        type: ACTION_TYPES.SET_IS_SAVING,
        payload: value
      });
    },

    setInitialFormData: (data) => {
      dispatch({
        type: ACTION_TYPES.SET_INITIAL_FORM_DATA,
        payload: data
      });
    },

    setLoadingProjectIndex: (index) => {
      dispatch({
        type: ACTION_TYPES.SET_LOADING_PROJECT_INDEX,
        payload: index
      });
    },

    setJiraPriorities: (priorities) => {
      dispatch({
        type: ACTION_TYPES.SET_JIRA_PRIORITIES,
        payload: priorities
      });
    },

    setSeverityToPriorityMap: (mapping) => {
      dispatch({
        type: ACTION_TYPES.SET_SEVERITY_TO_PRIORITY_MAP,
        payload: mapping
      });
    },

    setInitialSeverityMapping: (mapping) => {
      dispatch({
        type: ACTION_TYPES.SET_INITIAL_SEVERITY_MAPPING,
        payload: mapping
      });
    },

    updateSeverityMapping: (severity, priorityId) => {
      dispatch({
        type: ACTION_TYPES.UPDATE_SEVERITY_MAPPING,
        payload: { [severity]: priorityId }
      });
    },

    setIsLoadingPriorities: (value) => {
      dispatch({
        type: ACTION_TYPES.SET_IS_LOADING_PRIORITIES,
        payload: value
      });
    },

    setIsSavingSeverityMapping: (value) => {
      dispatch({
        type: ACTION_TYPES.SET_IS_SAVING_SEVERITY_MAPPING,
        payload: value
      });
    },

    setJiraType: (value) => {
      dispatch({
        type: ACTION_TYPES.SET_JIRA_TYPE,
        payload: value
      });
    }
  };

  return { state, actions };
}
