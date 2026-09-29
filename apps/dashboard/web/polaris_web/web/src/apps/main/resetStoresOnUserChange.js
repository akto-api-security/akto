import PersistStore from "./PersistStore";
import LocalStore from "./LocalStorageStore";
import SessionStore from "./SessionStore";
import IssuesStore from "../dashboard/pages/issues/issuesStore";

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
        LocalStore.getState().resetStore();
        SessionStore.getState().resetStore();
        IssuesStore.getState().resetStore();
        localStorage.setItem(OWNER_KEY, owner);
    } catch (e) {
        console.error("Error resetting stores on user change:", e);
    }
}
