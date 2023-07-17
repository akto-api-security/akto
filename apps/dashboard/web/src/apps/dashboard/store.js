import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

let store = (set)=>({
    leftNavSelected: '',
    setLeftNavSelected: (selected) =>  set({ leftNavSelected: selected }), 
    leftNavCollapsed: true,
    toggleLeftNavCollapsed: () => {
        set(state => ({ leftNavCollapsed: !state.leftNavCollapsed }))
    },
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken }),
    username: window.USER_NAME,
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
    }
})

store = devtools(store)
store = persist(store,{storage: createJSONStorage(() => sessionStorage)})

const Store = create(store)

export default Store

