import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

let store = (set)=>({
    hideFullNav: true,
    toggleLeftNav: (hideFullNav) => {
        set({hideFullNav: hideFullNav})
    },
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken }),
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
    }
})

store = devtools(store)
store = persist(store,{storage: createJSONStorage(() => sessionStorage)})

const Store = create(store)

export default Store

