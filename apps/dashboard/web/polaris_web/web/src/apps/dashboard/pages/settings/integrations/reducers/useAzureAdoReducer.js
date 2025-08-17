import { useReducer } from 'react';

export const aktoStatusForAzureAdo = ['Open', 'In Progress', 'Resolved', 'Closed'];

export const initialEmptyMapping = {
    'OPEN': [],
    'IN PROGRESS': [],
    'RESOLVED': [],
    'CLOSED': []
};

const initialState = {
    credentials: {
        organizationUrl: '',
        personalAccessToken: ''
    },
    projects: [],
    existingProjectIds: [],
    isAlreadyIntegrated: false,
    isSaving: false,
    initialFormData: null,
    loadingProjectIndex: null
};

function azureAdoReducer(state, action) {
    switch (action.type) {
        case 'SET_CREDENTIALS':
            return {
                ...state,
                credentials: {
                    ...state.credentials,
                    [action.field]: action.value
                }
            };
        case 'SET_PROJECTS':
            return {
                ...state,
                projects: action.projects
            };
        case 'ADD_PROJECT':
            return {
                ...state,
                projects: [
                    ...state.projects,
                    {
                        projectId: '',
                        workItemType: 'Bug',
                        enableBiDirIntegration: false,
                        aktoToAzureAdoStatusMap: JSON.parse(JSON.stringify(initialEmptyMapping)),
                        states: [],
                        azureAdoStateLabel: []
                    }
                ]
            };
        case 'REMOVE_PROJECT':
            return {
                ...state,
                projects: state.projects.filter((_, index) => index !== action.index)
            };
        case 'UPDATE_PROJECT':
            const updatedProjects = [...state.projects];
            updatedProjects[action.index] = {
                ...updatedProjects[action.index],
                ...action.updates
            };
            return {
                ...state,
                projects: updatedProjects
            };
        case 'CLEAR_PROJECTS':
            return {
                ...state,
                projects: []
            };
        case 'SET_EXISTING_PROJECT_IDS':
            return {
                ...state,
                existingProjectIds: action.projectIds
            };
        case 'SET_IS_ALREADY_INTEGRATED':
            return {
                ...state,
                isAlreadyIntegrated: action.value
            };
        case 'SET_IS_SAVING':
            return {
                ...state,
                isSaving: action.value
            };
        case 'SET_INITIAL_FORM_DATA':
            return {
                ...state,
                initialFormData: action.data
            };
        case 'SET_LOADING_PROJECT_INDEX':
            return {
                ...state,
                loadingProjectIndex: action.index
            };
        default:
            return state;
    }
}

export function useAzureAdoReducer() {
    const [state, dispatch] = useReducer(azureAdoReducer, initialState);

    const actions = {
        setCredentials: (field, value) => 
            dispatch({ type: 'SET_CREDENTIALS', field, value }),
        setProjects: (projects) => 
            dispatch({ type: 'SET_PROJECTS', projects }),
        addProject: () => 
            dispatch({ type: 'ADD_PROJECT' }),
        removeProject: (index) => 
            dispatch({ type: 'REMOVE_PROJECT', index }),
        updateProject: (index, updates) => 
            dispatch({ type: 'UPDATE_PROJECT', index, updates }),
        clearProjects: () => 
            dispatch({ type: 'CLEAR_PROJECTS' }),
        setExistingProjectIds: (projectIds) => 
            dispatch({ type: 'SET_EXISTING_PROJECT_IDS', projectIds }),
        setIsAlreadyIntegrated: (value) => 
            dispatch({ type: 'SET_IS_ALREADY_INTEGRATED', value }),
        setIsSaving: (value) => 
            dispatch({ type: 'SET_IS_SAVING', value }),
        setInitialFormData: (data) => 
            dispatch({ type: 'SET_INITIAL_FORM_DATA', data }),
        setLoadingProjectIndex: (index) => 
            dispatch({ type: 'SET_LOADING_PROJECT_INDEX', index })
    };

    return { state, actions };
}
