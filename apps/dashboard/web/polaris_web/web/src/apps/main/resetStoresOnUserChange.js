import PersistStore from "./PersistStore";
import LocalStore from "./LocalStorageStore";
import SessionStore from "./SessionStore";
import IssuesStore from "../dashboard/pages/issues/issuesStore";
import { getReportCategoryShortName, shortNameToCategory } from "./labelHelper";

// Saved dashboard data (collections, filters, ...) belongs to the user and account that loaded it.
// If the dashboard opens for a different user or account without a logout in between (for example
// logging in as someone else in the same browser), clear it the same way logout does, so one
// user's cached data is never shown to another.
const OWNER_KEY = "Akto-data-owner";

export default function resetStoresOnUserChange() {
    try {
        if (!window.USER_NAME) {
            return;
        }
        const owner = window.USER_NAME + "|" + window.ACTIVE_ACCOUNT;
        if (localStorage.getItem(OWNER_KEY) === owner) {
            return;
        }
        PersistStore.getState().resetAll();
        // resetAll restores the default category; keep the one a ?category= page (report PDFs, new tabs) set
        const categoryOverride = shortNameToCategory[getReportCategoryShortName()];
        if (categoryOverride) {
            PersistStore.getState().setDashboardCategory(categoryOverride);
        }
        LocalStore.getState().resetStore();
        SessionStore.getState().resetStore();
        IssuesStore.getState().resetStore();
        localStorage.setItem(OWNER_KEY, owner);
    } catch (e) {
        console.error("Error resetting stores on user change:", e);
    }
}
