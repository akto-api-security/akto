import { useEffect, useState } from "react";
import settingRequests from "@/apps/dashboard/pages/settings/api";

// The account members a case can be assigned to, from the existing getTeamData (the Users settings page's
// list). It also lists people who were invited but have not joined: those can't own a case (the server only
// accepts members), so they are left out. One request per page load, shared by every screen that needs it
// (tables, flyouts, the review dialog); a failed request is retried on the next use.
let loadedOwners = null;
let inFlight = null;

const isAktoUser = (user) => String(user?.login || "").toLowerCase().endsWith("@akto.io");

// Admin in the account's base role or in any product (scope) it has roles for.
const isAdmin = (user) => [user?.role, ...Object.values(user?.scopeRoleMapping || {})]
    .some((role) => String(role || "").toUpperCase() === "ADMIN");

// Akto staff are added to customer accounts for support; they are not who the customer assigns a case to, so
// they are left out of the list. An account is the customer's when one of its admins is not Akto staff. When
// all its admins are Akto staff the account is Akto's own (e.g. the Ironheart demo), and there they stay.
export function toCaseOwners(team) {
    const members = (Array.isArray(team) ? team : []).filter((user) => user?.login && !user.isInvitation);
    const customerOwned = members.some((user) => isAdmin(user) && !isAktoUser(user));
    return members
        .filter((user) => !(customerOwned && isAktoUser(user)))
        .map((user) => ({ login: user.login, name: user.name }))
        .sort((a, b) => a.login.localeCompare(b.login));
}

function loadCaseOwners() {
    if (loadedOwners) return Promise.resolve(loadedOwners);
    if (!inFlight) {
        inFlight = settingRequests.getTeamData()
            .then((team) => {
                loadedOwners = toCaseOwners(team);
                return loadedOwners;
            })
            .catch(() => [])
            .finally(() => { inFlight = null; });
    }
    return inFlight;
}

export default function useCaseOwners() {
    const [owners, setOwners] = useState(loadedOwners || []);
    const [loaded, setLoaded] = useState(loadedOwners !== null);

    useEffect(() => {
        let cancelled = false;
        loadCaseOwners().then((list) => {
            if (cancelled) return;
            setOwners(list);
            setLoaded(true);
        });
        return () => { cancelled = true; };
    }, []);

    return { owners, loaded };
}
