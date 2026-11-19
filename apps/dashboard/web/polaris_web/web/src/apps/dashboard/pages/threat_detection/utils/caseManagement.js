// Guardrail case management: an event marked for review can have an owner and notes, and every status,
// owner and notes change is kept as a timeline entry on the event ({ timestamp, updatedBy, activityType, status, owner, notes }, oldest first).
// Shared by the old (SusDataTable / SampleDetails) and new (ViolationsPage / ViolationFlyout) screens.

// The owner filter value for events with no owner; the threat backend knows it by the same name.
export const UNASSIGNED_OWNER = "__unassigned__";
export const CASE_NOTES_MAX_LENGTH = 5000;
// The server keeps this many of the newest entries.
const CASE_TIMELINE_MAX_ENTRIES = 10;

// activityType says which of status, owner and notes an entry is about; the others are "". STATUS: status = the
// new status. OWNER: owner = the new owner ("" when unassigned). NOTES: notes = the notes as saved ("" when cleared).
export const ACTIVITY_TYPE = { STATUS: "STATUS", OWNER: "OWNER", NOTES: "NOTES" };

const STATUS_DESCRIPTIONS = {
    UNDER_REVIEW: "Marked for review",
    ACTIVE: "Reactivated",
    IGNORED: "Ignored",
    TRAINING: "Marked for training",
    HUMAN_APPROVAL: "Sent for human approval",
};

const NOTES_PREVIEW_LENGTH = 140;

export function truncateText(text, max) {
    const value = String(text || "");
    return value.length > max ? `${value.slice(0, max).trimEnd()}…` : value;
}

const knownOwner = (login, owners) => (owners || []).find((o) => o.login === login);

// "Alice Smith" when the account member has a name, else their login.
export function ownerLabel(login, owners) {
    if (!login) return "";
    const name = knownOwner(login, owners)?.name;
    return name && name !== "-" ? name : login;
}

// "Alice Smith (alice@acme.com)" for pickers, where two members can share a name.
export function ownerOptionLabel(owner) {
    return owner.name && owner.name !== "-" && owner.name !== owner.login ? `${owner.name} (${owner.login})` : owner.login;
}

// "Assign to me" is offered to someone who can assign, is one of the listed owners, and isn't already the owner.
export function canAssignToMe({ canAssign, me, owner, owners }) {
    return !!canAssign && !!me && owner !== me && (owners || []).some((o) => o.login === me);
}

export function describeCaseActivity(entry, owners) {
    switch (entry?.activityType) {
        case ACTIVITY_TYPE.STATUS:
            return STATUS_DESCRIPTIONS[entry.status] || `Status changed to ${String(entry.status || "").replace(/_/g, " ").toLowerCase()}`;
        case ACTIVITY_TYPE.OWNER:
            return entry.owner ? `Assigned to ${ownerLabel(entry.owner, owners)}` : "Unassigned";
        case ACTIVITY_TYPE.NOTES:
            return entry.notes ? `Notes updated: “${truncateText(entry.notes, NOTES_PREVIEW_LENGTH)}”` : "Notes cleared";
        default:
            return "Updated";
    }
}

// Entries for ActivityTracker ({ description, timestamp, user }), oldest first, starting with when the
// event was detected. Events from before case management have just that first entry.
export function buildCaseTimeline(caseTimeline, detectedAt, owners, detectedLabel = "Detected") {
    const entries = (caseTimeline || [])
        .map((entry) => ({ description: describeCaseActivity(entry, owners), timestamp: entry.timestamp, user: entry.updatedBy || undefined }))
        .sort((a, b) => a.timestamp - b.timestamp);
    return detectedAt ? [{ description: detectedLabel, timestamp: detectedAt }, ...entries] : entries;
}

// What the server will do with this update, applied to a row we already have, so an open flyout shows the
// change at once. Follows the server's rule: only a value that actually changes gets a timeline entry.
// A field left undefined is left as is; "" clears the owner or notes.
export function applyCaseUpdate(current, { status, caseOwner, caseNotes }, updatedBy, now = Math.floor(Date.now() / 1000)) {
    const next = {
        status: current.status,
        caseOwner: current.caseOwner || "",
        caseNotes: current.caseNotes || "",
        caseTimeline: [...(current.caseTimeline || [])],
    };
    const add = (activityType, changed) => next.caseTimeline.push({ timestamp: now, updatedBy: updatedBy || "", activityType, status: "", owner: "", notes: "", ...changed });
    if (status !== undefined && status !== (current.status || "ACTIVE")) {
        next.status = status;
        add(ACTIVITY_TYPE.STATUS, { status });
    }
    if (caseOwner !== undefined && caseOwner.trim() !== next.caseOwner) {
        next.caseOwner = caseOwner.trim();
        add(ACTIVITY_TYPE.OWNER, { owner: next.caseOwner });
    }
    if (caseNotes !== undefined) {
        const notes = caseNotes.slice(0, CASE_NOTES_MAX_LENGTH);
        if (notes !== next.caseNotes) {
            next.caseNotes = notes;
            add(ACTIVITY_TYPE.NOTES, { notes });
        }
    }
    next.caseTimeline = next.caseTimeline.slice(-CASE_TIMELINE_MAX_ENTRIES);
    return next;
}

// Only what the user actually set: leaving a blank field blank must not clear an owner or notes an event
// already has, and an unchanged value needs no update.
export function changedCaseFields({ owner, notes }, { initialOwner = "", initialNotes = "" } = {}) {
    const fields = {};
    if (owner !== initialOwner) fields.caseOwner = owner;
    if (notes !== initialNotes) fields.caseNotes = notes;
    return fields;
}

export function caseOwnerFilterChoices(owners) {
    return [
        { label: "Unassigned", value: UNASSIGNED_OWNER },
        ...(owners || []).map((o) => ({ label: ownerOptionLabel(o), value: o.login })),
    ];
}

export const CASE_NOTES_FILTER_CHOICES = [
    { label: "With notes", value: "true" },
    { label: "Without notes", value: "false" },
];

// Both choices selected (or neither) is no filter.
export function caseNotesFilterValue(selected) {
    const values = Array.isArray(selected) ? selected.map(String) : selected != null ? [String(selected)] : [];
    const hasWith = values.includes("true");
    const hasWithout = values.includes("false");
    if (hasWith === hasWithout) return undefined;
    return hasWith;
}

// The request fields for the Owner and Notes filters ({ caseOwners?, hasCaseNotes? }). Only the Under Review tab
// has them, so a filter left over from another tab never narrows this one.
export function caseFilterRequestParams(tab, filters) {
    if (tab !== "under_review") return {};
    const hasCaseNotes = caseNotesFilterValue(filters?.caseNotes);
    return {
        ...(filters?.caseOwner?.length ? { caseOwners: filters.caseOwner } : {}),
        ...(typeof hasCaseNotes === "boolean" ? { hasCaseNotes } : {}),
    };
}
