import { useEffect, useState } from "react";
import func from "@/util/func";
import { getDashboardCategory } from "@/apps/main/labelHelper";
import settingRequests from "@/apps/dashboard/pages/settings/api";

// Only these base roles (custom roles count as the one they are based on) assign a case, and only to someone
// with one of them. The server checks the same.
export const CASE_OWNER_ROLES = ["ADMIN", "THREAT_ENGINEER"];
export const ASSIGN_ROLES_MESSAGE = "Only Admin and Threat Engineer roles can assign a case.";

// Roles are only enforced where the server enforces them (RBAC on a metered dashboard), as for guardrail policies.
const rolesEnforced = () => !func.checkLocal() && (func.checkForRbacFeature() || func.checkForRbacFeatureBasic());

export const canAssignCases = () => !rolesEnforced() || CASE_OWNER_ROLES.includes(func.currentProductBaseRole());

// The account members a case can be assigned to, from the existing getTeamData (the Users settings page's
// list). It also lists people who were invited but have not joined: those can't own a case (the server only
// accepts members), so they are left out. One request per page load and product (a member's role differs per
// product), shared by every screen that needs it (tables, flyouts, the review dialog); a failed request is
// retried on the next use.
let loadedOwners = null;
let loadedFor = null;
let inFlight = null;
let inFlightFor = null;

const isAktoUser = (user) => String(user?.login || "").toLowerCase().endsWith("@akto.io");

// Admin in the dashboard being viewed (Argus, Atlas...): baseRole is the member's role in this product, sent by
// the same request. A response without it (older server) falls back to admin in any product.
const isAdmin = (user) => user?.baseRole
    ? user.baseRole === "ADMIN"
    : [user?.role, ...Object.values(user?.scopeRoleMapping || {})].some((role) => String(role || "").toUpperCase() === "ADMIN");

// Akto staff are added to customer accounts for support; they are not who the customer assigns a case to, so
// they are left out of the list. An account is the customer's, in this product, when one of its admins here is
// not Akto staff. When all its admins here are Akto staff the account is Akto's own (e.g. the Ironheart demo),
// and there they stay. (A customer who is only an Argus admin does not make Atlas the customer's.)
// With roleRestricted, only members whose base role in this product (baseRole, from the same response) can own
// a case are listed; a response without baseRole is not filtered, the server still refuses the others.
export function toCaseOwners(team, { roleRestricted = false } = {}) {
    const members = (Array.isArray(team) ? team : []).filter((user) => user?.login && !user.isInvitation);
    const customerOwned = members.some((user) => isAdmin(user) && !isAktoUser(user));
    return members
        .filter((user) => !(customerOwned && isAktoUser(user)))
        .filter((user) => !roleRestricted || !user.baseRole || CASE_OWNER_ROLES.includes(user.baseRole))
        .map((user) => ({ login: user.login, name: user.name }))
        .sort((a, b) => a.login.localeCompare(b.login));
}

function loadCaseOwners(key) {
    if (loadedOwners && loadedFor === key) return Promise.resolve(loadedOwners);
    if (!inFlight || inFlightFor !== key) {
        inFlightFor = key;
        const request = settingRequests.getTeamData()
            .then((team) => {
                loadedOwners = toCaseOwners(team, { roleRestricted: rolesEnforced() });
                loadedFor = key;
                return loadedOwners;
            })
            .catch(() => [])
            .finally(() => { if (inFlight === request) inFlight = null; });
        inFlight = request;
    }
    return inFlight;
}

// canAssign: whether the signed-in user may set or change an owner (everyone else can still add notes).
export default function useCaseOwners() {
    const key = getDashboardCategory();
    const cached = loadedFor === key ? loadedOwners : null;
    const [owners, setOwners] = useState(cached || []);
    const [loaded, setLoaded] = useState(cached !== null);

    useEffect(() => {
        let cancelled = false;
        loadCaseOwners(key).then((list) => {
            if (cancelled) return;
            setOwners(list);
            setLoaded(true);
        });
        return () => { cancelled = true; };
    }, [key]);

    return { owners, loaded, canAssign: canAssignCases() };
}
