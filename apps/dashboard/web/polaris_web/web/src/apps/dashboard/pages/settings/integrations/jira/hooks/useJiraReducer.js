import { useReducer } from 'react';
import { ACTION_TYPES, INITIAL_STATE, INITIAL_EMPTY_MAPPING } from '../constants';

/**
 * Main reducer function for Jira integration
 * @param {Object} state - Current state
 * @param {Object} action - Action to perform
 * @returns {Object} - New state
 */
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
            aktoToJiraStatusMap: JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING)),
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
            // Check if we're updating a specific property or the entire project
            if (action.payload.updates) {
              return {
                ...project,
                ...action.payload.updates
              };
            } else {
              // If no updates provided, return the project unchanged
              return project;
            }
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

/**
 * Custom hook for Jira state management
 * @returns {Array} - [state, dispatch functions]
 */
export default function useJiraReducer() {
  const [state, dispatch] = useReducer(jiraReducer, INITIAL_STATE);

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

  return [
    state,
    {
      setCredentials,
      addProject,
      removeProject,
      updateProject,
      clearProjects,
      setProjects,
      setExistingProjectIds,
      setIsAlreadyIntegrated,
      setIsSaving,
      setInitialFormData,
      setLoadingProjectIndex
    }
  ];
}
