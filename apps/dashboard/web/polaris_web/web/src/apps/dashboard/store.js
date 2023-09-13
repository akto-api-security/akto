import {create} from "zustand"
import {devtools} from "zustand/middleware"

let store = (set)=>({
    leftNavCollapsed: false,
    toggleLeftNavCollapsed: () => {
        set(state => ({ leftNavCollapsed: !state.leftNavCollapsed }))
    },
    username: window.USER_NAME,
    accounts: window.ACCOUNTS,
    activeAccount: window.ACTIVE_ACCOUNT, 
    toastConfig: {
        isActive: false,
        isError: false,
        message: ""
    },
    setToastConfig: (updateToastConfig) => {
        set({
            toastConfig: {
                isActive: updateToastConfig.isActive,
                isError: updateToastConfig.isError,
                message: updateToastConfig.message
            }
        })
    },
    allCollections: [],
    setAllCollections:(allCollections)=>{
        set({allCollections: allCollections})
    },
    dataTypeNames: [],
    setDataTypeNames: (dataTypeNames) => {
        set({dataTypeNames:dataTypeNames})  
    },
    isLocalDeploy: window.DASHBOARD_MODE === "LOCAL_DEPLOY",

    allRoutes: [],
    setAllRoutes:(allRoutes)=>{
        set({allRoutes: allRoutes})
    },
    
    collectionsMap: {},
    setCollectionsMap:(collectionsMap)=>{
        set({collectionsMap: collectionsMap})
    },
})

store = devtools(store)
const Store = create(store)

export default Store

