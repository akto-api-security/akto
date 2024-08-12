import { createContext, useReducer, useContext } from "react";
import tableReducer, {initialState} from "./tableReducer";

const TableContext = createContext(initialState);

export const TableContextProvider = ({ children }) => {
  const [state, dispatch] = useReducer(tableReducer, initialState);

  const applyFilter = (filter) => {
    dispatch({
      type: "APPLY_FILTER",
      payload: {
        tabsInfo: filter
      }
    });
  };

  const selectItems = (items) => {
    console.log("called", items)
    dispatch({
      type: "SELECT_ROW_ITEMS",
      payload: {
        selectedItems: items
      }
    })
  }

  const value = {
    tabsInfo: state.tabsInfo,
    applyFilter,
    selectItems,
    selectedItems: state.selectedItems
  };
  return <TableContext.Provider value={value}>{children}</TableContext.Provider>;
};

const useTable = () => {
  const context = useContext(TableContext);

  if (context === undefined) {
    throw new Error("useTable must be used within TableContext");
  }

  return context;
};

export default useTable;
