import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

let persistStore = (set) => ({
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken }),
})

persistStore = devtools(persistStore)
persistStore = persist(persistStore,{storage: createJSONStorage(() => sessionStorage)})

const PersistStore = create(persistStore);

export default PersistStore

