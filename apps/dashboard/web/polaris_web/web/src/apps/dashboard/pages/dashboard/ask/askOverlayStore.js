import { create } from "zustand"
import { devtools } from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

// Whether the Ask Akto overlay is open, and whether a page asked it to start a script-writing
// conversation (service/scripts/validation/CLAUDE.md). The topbar button and ⌘K toggle it; the
// Scripts page opens it in script mode. scriptSession.nonce changes on every request, so asking
// twice starts two fresh conversations.
let askOverlayStore = (set) => ({
    open: false,
    scriptSession: null,
    toggle: () => set((state) => ({ open: !state.open })),
    close: () => set({ open: false }),
    openScriptMode: (apiCollectionId) => set({
        open: true,
        scriptSession: { nonce: Date.now(), apiCollectionId: apiCollectionId ?? null },
    }),
})

askOverlayStore = devtools(askOverlayStore, devtoolsOptions("AskOverlayStore"))
const AskOverlayStore = create(askOverlayStore)

export default AskOverlayStore
