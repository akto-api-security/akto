import { useReducer } from 'react';

export const aktoStatusForJira = ["Open", "Fixed", "Ignored"];
export const initialEmptyMapping = aktoStatusForJira.reduce((acc, status) => {
  acc[status.toUpperCase()] = [];
  return acc;
}, {});

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
  loadingProjectIndex: null
};

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
  CLEAR_PROJECTS: 'CLEAR_PROJECTS',
  SET_LOADING_PROJECT_INDEX: 'SET_LOADING_PROJECT_INDEX'
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
    }
  };

  return { state, actions };
}
