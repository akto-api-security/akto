import PersistStore from "@/apps/main/PersistStore"
import { CATEGORY_API_SECURITY } from "@/apps/main/labelHelper"

// The dashboard the user is on — the same value request.js turns into x-context-source, so the
// palette's own copy and prompts always match the tiles the backend returns.
export default function useDashboardCategory() {
    return PersistStore((state) => state.dashboardCategory) || CATEGORY_API_SECURITY
}
