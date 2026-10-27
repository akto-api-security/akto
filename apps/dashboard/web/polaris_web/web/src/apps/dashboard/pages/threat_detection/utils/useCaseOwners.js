import { useEffect, useState } from "react";
import settingRequests from "@/apps/dashboard/pages/settings/api";

// The account members a case can be assigned to, from the existing getTeamData (the Users settings page's
// list). It also lists people who were invited but have not joined: those can't own a case (the server only
// accepts members), so they are left out. One request per page load, shared by every screen that needs it
// (tables, flyouts, the review dialog); a failed request is retried on the next use.
let loadedOwners = null;
let inFlight = null;

export function toCaseOwners(team) {
    return (Array.isArray(team) ? team : [])
        .filter((user) => user?.login && !user.isInvitation)
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
