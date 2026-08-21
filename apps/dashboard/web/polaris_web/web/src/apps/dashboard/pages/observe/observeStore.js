import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

let observeStore = (set)=>({

    inventoryFlyout: null,
    setInventoryFlyout:(inventoryFlyout)=>{
        set({inventoryFlyout: inventoryFlyout})
    },

    filteredItems: [],
    setFilteredItems:(filteredItems)=>{
        set({filteredItems: filteredItems})
    },

    samples: [],
    setSamples:(samples)=>{
        set({samples: samples})
    },
    
    selectedUrl: {},
    setSelectedUrl:(selectedUrl)=>{
        set({selectedUrl: selectedUrl})
    },
})

observeStore = devtools(observeStore, devtoolsOptions("ObserveStore"))
const ObserveStore = create(observeStore)

export default ObserveStore

