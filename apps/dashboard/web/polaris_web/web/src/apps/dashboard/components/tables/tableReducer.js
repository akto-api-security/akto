import PersistStore from "../../../main/PersistStore";
const tableInitialState = PersistStore.getState().tableInitialState[window.location.href] || {}

export const initialState = {
    tabsInfo : tableInitialState
}

const tableReducer = (state, action) =>{
    const { type, payload } = action;
    switch (type) {
        case "APPLY_FILTER":
            return {
                ...state,
                tabsInfo: payload.tabsInfo,
              };
        default:
            return{...state}
    }
}

export default tableReducer
