import { useEffect, useState } from "react";
import func from "@/util/func";
import { getDashboardCategory } from "@/apps/main/labelHelper";
import settingRequests from "@/apps/dashboard/pages/settings/api";

// Only these base roles (custom roles count as the one they are based on) assign a case, and only to someone
// with one of them. The server checks the same.
const CASE_OWNER_ROLES = ["ADMIN", "THREAT_ENGINEER"];
export const ASSIGN_ROLES_MESSAGE = "Only Admin and Threat Engineer roles can assign a case.";

// Roles are only enforced where the server enforces them (RBAC on a metered dashboard), as for guardrail policies.
const rolesEnforced = () => !func.checkLocal() && (func.checkForRbacFeature() || func.checkForRbacFeatureBasic());

const canAssignCases = () => !rolesEnforced() || CASE_OWNER_ROLES.includes(func.currentProductBaseRole());

const isAktoUser = (user) => String(user?.login || "").toLowerCase().endsWith("@akto.io");

const isAdminInAnyProduct = (user) => [user?.role, ...Object.values(user?.scopeRoleMapping || {})]
    .some((role) => String(role || "").toUpperCase() === "ADMIN");

// Admin in the dashboard being viewed (Argus, Atlas...): baseRole is the member's role in this product, sent by
// the same request. A response without it (older server) falls back to admin in any product.
const isAdminHere = (user) => user?.baseRole ? user.baseRole === "ADMIN" : isAdminInAnyProduct(user);

// A response without baseRole is not filtered by role: the server still refuses the others.
const hasCaseOwnerRole = (user) => !user.baseRole || CASE_OWNER_ROLES.includes(user.baseRole);

// The members a case can be assigned to; invited people who have not joined can't own a case (the server only
// accepts members).
// - Akto staff are added to customer accounts for support; they are not who the customer assigns a case to, so
//   they are left out. An account is the customer's, in this product, when one of its admins here is not Akto
//   staff. When all its admins here are Akto staff the account is Akto's own (e.g. the Ironheart demo), and
//   there they stay. (A customer who is only an Argus admin does not make Atlas the customer's.)
// - With roleRestricted, only members who have a case owner role in this product are listed.
export function toCaseOwners(team, { roleRestricted = false } = {}) {
    const members = (Array.isArray(team) ? team : []).filter((user) => user?.login && !user.isInvitation);
    const customerOwned = members.some((user) => isAdminHere(user) && !isAktoUser(user));
    return members
        .filter((user) => !(customerOwned && isAktoUser(user)) && (!roleRestricted || hasCaseOwnerRole(user)))
        .map((user) => ({ login: user.login, name: user.name }))
        .sort((a, b) => a.login.localeCompare(b.login));
}

// One request per page load and dashboard category (a member's role differs per product), shared by every
// screen that needs it (tables, flyouts, the review dialog). A failed request (e.g. no access to the members
// list) gives an empty list that is not kept, so the next use retries.
const ownersByCategory = new Map();
const requestsByCategory = new Map();

function loadCaseOwners(category) {
    if (ownersByCategory.has(category)) return Promise.resolve(ownersByCategory.get(category));
    if (!requestsByCategory.has(category)) {
        const request = settingRequests.getTeamData()
            .then((team) => {
                const owners = toCaseOwners(team, { roleRestricted: rolesEnforced() });
                ownersByCategory.set(category, owners);
                return owners;
            })
            .catch(() => [])
            .finally(() => requestsByCategory.delete(category));
        requestsByCategory.set(category, request);
    }
    return requestsByCategory.get(category);
}

// canAssign: whether the signed-in user may set or change an owner (everyone else can still add notes).
export default function useCaseOwners() {
    const category = getDashboardCategory();
    const cached = ownersByCategory.get(category);
    const [owners, setOwners] = useState(cached || []);
    const [loaded, setLoaded] = useState(cached !== undefined);

    useEffect(() => {
        let cancelled = false;
        loadCaseOwners(category).then((list) => {
            if (cancelled) return;
            setOwners(list);
            setLoaded(true);
        });
        return () => { cancelled = true; };
    }, [category]);

    return { owners, loaded, canAssign: canAssignCases() };
}
