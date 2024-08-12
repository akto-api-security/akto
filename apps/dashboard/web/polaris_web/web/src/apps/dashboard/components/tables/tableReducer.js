import PersistStore from "../../../main/PersistStore";
const tableInitialState = PersistStore.getState().tableInitialState[window.location.pathname + "/" + window.location.hash] || 0
export const initialState = {
    tabsInfo : tableInitialState,
    selectedItems: []
}

const tableReducer = (state, action) =>{
    const { type, payload } = action;
    console.log("reducer", payload)
    switch (type) {
        case "APPLY_FILTER":
            return {
                ...state,
                tabsInfo: payload.tabsInfo,
              };
        case "SELECT_ROW_ITEMS": {
            return {
                ...state,
                selectedItems: payload.selectedItems
            }
        }
        default:
            return{...state}
    }
}

export default tableReducer
