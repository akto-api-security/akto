import {create} from "zustand"
import {devtools} from "zustand/middleware"

let tableStore = (set)=>({
    selectedItems: [],
    setSelectedItems: (selectedItems) => set({ selectedItems: selectedItems }),

    openedLevels: [],
    setOpenedLevels: (openedLevels) => set({ openedLevels: openedLevels }),

})

tableStore = devtools(tableStore)
const TableStore = create(tableStore)
export default TableStore