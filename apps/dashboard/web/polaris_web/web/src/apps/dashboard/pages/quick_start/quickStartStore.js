import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

let quickStartStore = (set)=>({
    currentConnector: null,
    setCurrentConnector:(currentConnector)=>{
        set({currentConnector: currentConnector})
    },

    active: null,
    setActive:(active)=>{
        set({active: active})
    },

    yamlContent: null,
    setYamlContent:(yamlContent)=>{
        set({yamlContent: yamlContent})
    },

    duplicateScanData: null,
    setDuplicateScanData:(duplicateScanData)=>{
        set({duplicateScanData: duplicateScanData})
    },
})

quickStartStore = devtools(quickStartStore, devtoolsOptions("QuickStartStore"))
const QuickStartStore = create(quickStartStore)

export default QuickStartStore