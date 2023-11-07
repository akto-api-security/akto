import {create} from "zustand"
import {devtools} from "zustand/middleware"

let webhooksStore = (set)=>({
    customWebhooks: [],
    setCustomWebhooks: (customWebhooks) => set({ customWebhooks: customWebhooks }),
})

webhooksStore = devtools(webhooksStore)
const WebhooksStore = create(webhooksStore)
export default WebhooksStore

