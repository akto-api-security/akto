import {create} from "zustand"
import {devtools} from "zustand/middleware"

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
})

quickStartStore = devtools(quickStartStore)
const QuickStartStore = create(quickStartStore)

export default QuickStartStore