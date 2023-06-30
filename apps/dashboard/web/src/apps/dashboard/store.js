import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

let store = (set)=>({
    tableItems:['item1','item2'],
    nextItems: (items)=>{
        set((state)=> ({tableItems: items}))
    },
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken })
})

store = devtools(store)
store = persist(store,{storage: createJSONStorage(() => sessionStorage)})

const Store = create(store)

export default Store

