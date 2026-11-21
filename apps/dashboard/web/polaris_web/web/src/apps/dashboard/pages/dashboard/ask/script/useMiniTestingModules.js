import { useEffect, useState } from "react"
import api from "../../../testing/api"
import settingFunctions from "../../../settings/module"

// The mini-testing modules a validation can run on, loaded the way the test role page does it: the account must
// have hybrid testing on, then the names of modules with a recent heartbeat. A failed load is reported as
// loadFailed, never as "no modules", so the picker does not pretend the account has none.
export default function useMiniTestingModules() {
    const [state, setState] = useState({ loading: true, hybridEnabled: false, names: [], loadFailed: false })

    useEffect(() => {
        let cancelled = false
        ;(async () => {
            try {
                const accountConfig = await settingFunctions.fetchAccountConfig()
                if (!accountConfig?.hybridTestingEnabled) {
                    if (!cancelled) setState({ loading: false, hybridEnabled: false, names: [], loadFailed: false })
                    return
                }
                const resp = await api.fetchMiniTestingServiceNames()
                if (!cancelled) setState({ loading: false, hybridEnabled: true, names: resp?.miniTestingServiceNames || [], loadFailed: false })
            } catch (e) {
                if (!cancelled) setState({ loading: false, hybridEnabled: false, names: [], loadFailed: true })
            }
        })()
        return () => { cancelled = true }
    }, [])

    return state
}
