import {create} from "zustand"
import {devtools} from "zustand/middleware"

let tableStore = (set)=>({
    selectedItems: [],
    setSelectedItems: (selectedItems) => set({ selectedItems: selectedItems }),
})

tableStore = devtools(tableStore)
const TableStore = create(tableStore)
export default TableStore