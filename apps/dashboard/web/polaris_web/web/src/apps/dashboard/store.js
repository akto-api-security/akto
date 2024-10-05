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
    isLocalDeploy: window?.DASHBOARD_MODE === "LOCAL_DEPLOY",
    isAws: window?.CLOUD_TYPE !== "GCP",

    allRoutes: [],
    setAllRoutes:(allRoutes)=>{
        set({allRoutes: allRoutes})
    },
    confirmationModalConfig: {
        modalContent: "", 
        primaryActionContent: "Confirm", 
        primaryAction: ()=>{},
        show: false
    },
    setConfirmationModalConfig: (updateConfirmationModal) => {
        set({
            confirmationModalConfig: {
                modalContent: updateConfirmationModal.modalContent,
                primaryActionContent: updateConfirmationModal.primaryActionContent,
                primaryAction: updateConfirmationModal.primaryAction,
                show: updateConfirmationModal.show
            }
        })
    },
    
})

store = devtools(store)
const Store = create(store)

export default Store

