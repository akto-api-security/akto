import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

let tableStore = (set)=>({
    selectedItems: [],
    setSelectedItems: (selectedItems) => set({ selectedItems: selectedItems }),

    openedLevels: [],
    setOpenedLevels: (openedLevels) => set({ openedLevels: openedLevels }),

})

tableStore = devtools(tableStore, devtoolsOptions("TableStore"))
const TableStore = create(tableStore)
export default TableStore