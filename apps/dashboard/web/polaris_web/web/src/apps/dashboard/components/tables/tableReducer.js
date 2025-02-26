import PersistStore from "../../../main/PersistStore";
import TableStore from "./TableStore";
const tableInitialState = PersistStore.getState().tableInitialState[window.location.pathname + "/" + window.location.hash] || 0
const selectedItems = TableStore.getState().selectedItems
const openedRows = TableStore.getState().openedLevels
const tableSelectedTab = PersistStore.getState().tableSelectedTab
export const initialState = {
    tabsInfo : tableInitialState,
    selectedItems: selectedItems,
    openedRows: openedRows,
    tableSelectedTab: tableSelectedTab
}

const tableReducer = (state, action) =>{
    const { type, payload } = action;
    switch (type) {
        case "APPLY_FILTER":
            return {
                ...state,
                tabsInfo: payload.tabsInfo,
              };
        case "TABLE_SELECTED_TAB": {
            return {
                ...state,
                tableSelectedTab: payload.tableSelectedTab
            }
        }
        case "SELECT_ROW_ITEMS": {
            return {
                ...state,
                selectedItems: payload.selectedItems
            }
        }
        case "OPEN_LEVELS": {
            return {
                ...state,
                openedRows: payload.openedRows
            }
        }
        default:
            return{...state}
    }
}

export default tableReducer
