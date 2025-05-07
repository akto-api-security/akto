// Constants for Jira integration

export const AKTO_STATUS_FOR_JIRA = ["Open", "Fixed", "Ignored"];

export const INITIAL_EMPTY_MAPPING = AKTO_STATUS_FOR_JIRA.reduce((acc, status) => {
  acc[status.toUpperCase()] = [];
  return acc;
}, {});

// Define action types for reducer
export const ACTION_TYPES = {
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

// Define initial state for the form
export const INITIAL_STATE = {
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
