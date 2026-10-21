import { useEffect, useMemo, useState } from "react";
import settingsApi from "../../../settings/api";
import { isEndpointSecurityCategory } from "../../../../../main/labelHelper";
import { ALL_VALUES_SENTINEL } from "../../../../components/shared/DropdownSearch";

// deviceId is the agent's device label, "{hostname}-{first8ofMachineID}" — the unique,
// stable part is the segment after the LAST hyphen (the hostname itself may contain hyphens).
// Falls back to a plain prefix slice for any value that doesn't follow that format.
export const deviceIdSuffix = (deviceId) => {
    if (!deviceId) return '';
    const idx = deviceId.lastIndexOf('-');
    return idx >= 0 ? deviceId.slice(idx + 1) : deviceId.slice(0, 8);
};

// The 8-4-4-4-12 shape, so an unrelated composite id like "okta_12345" isn't read as an org.
const ORG_UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

// Pulls the org uuid off a composite userId of the form "<email>_<orgUuid>" — the shape a Claude
// login writes, one row per org — returning '' when the id carries no org.
//
// Split on the LAST underscore, never the first: email local-parts legally contain underscores
// ("first_last@corp.com_<uuid>"), and a first-underscore split would silently yield a wrong org.
// A uuid contains no underscore, so the last one is always the separator.
//
// Mirrors AgenticUsers#orgUuidFromUserId (Java) and orgUUIDFromUserID (validator/claude_org_match.go),
// which is what actually matches these ids at request time. Keep all three in step.
export const orgUuidFromUserId = (userId) => {
    if (!userId) return '';
    const idx = userId.lastIndexOf('_');
    if (idx < 0) return '';
    const candidate = userId.slice(idx + 1);
    return ORG_UUID_RE.test(candidate) ? candidate : '';
};

// One option per value; values are the dropdown's React keys, and duplicates break its filtering.
const uniqueByValue = (options) => {
    const seen = new Set();
    return options.filter(o => !seen.has(o.value) && seen.add(o.value));
};

// Agentic users (Atlas only) and the targeting options built from them; args only feed the preview.
export const useAgenticUserOptions = ({ applyToAllUsers, targetTags, negatedTargetTags, targetDeviceIds, negatedTargetDeviceIds }) => {
    const [agenticUsers, setAgenticUsers] = useState([]);
    const [usersLoading, setUsersLoading] = useState(false);

    // Every device-tag key/value pair seen across AgenticUsers — powers the dynamic
    // tag-key picker below (any key is targetable, e.g. group, role, team, department).
    const availableTagKeyValues = useMemo(() => {
        const map = new Map();
        (agenticUsers || []).forEach(u => {
            (u.deviceTags || []).forEach(t => {
                if (!t?.key || !t.value) return;
                if (!map.has(t.key)) map.set(t.key, new Set());
                map.get(t.key).add(t.value);
            });
        });
        return Array.from(map.entries())
            .map(([key, values]) => ({ key, values: Array.from(values).sort() }))
            .sort((a, b) => a.key.localeCompare(b.key));
    }, [agenticUsers]);

    // One row per identity (userName/userEmail), regardless of whether it has any device on
    // record — this is the "Users" targeting pool: pick a person directly rather than one of
    // their devices. Kept as its own dropdown option (see ServerSettingsStep) so a selection is
    // never ambiguous about whether it names a person or a device.
    const availableUsers = useMemo(() => {
        const options = [];
        (agenticUsers || []).forEach(u => {
            const name = u.userName || u.userEmail;
            if (!name) return;
            // An org-scoped identity is selectable in its own right, and must be SENT as its userId:
            // a Claude login writes one row per org, all sharing one userName, so the username alone
            // cannot say which org was meant. The validator reads the org back off this exact id
            // (orgUUIDFromUserID), so it is both the label and the value here. The backend emits
            // these alongside the username-deduped row, never instead of it — see
            // ModuleInfoAction#fetchAgenticUsers — so the person is still targetable across all orgs.
            if (orgUuidFromUserId(u.userId)) {
                // Shown as email-orgName__orgType for readability; the submitted/stored value stays
                // the raw userId (email_orgUuid) the validator matches on — see orgUuidFromUserId.
                const label = (u.userEmail && u.organizationName && u.organizationType)
                    ? `${u.userEmail}-${u.organizationName}__${u.organizationType}`
                    : u.userId;
                options.push({ label, value: u.userId });
                return;
            }
            // Every other row is unchanged: keyed by username and labelled by email, which is what
            // enforcement matches these rows on. Only the org rows above show a userId.
            const label = u.userEmail && u.userEmail !== name ? `${u.userEmail} · ${name}` : name;
            options.push({ label, value: name });
        });
        return uniqueByValue(options).sort((a, b) => a.label.localeCompare(b.label));
    }, [agenticUsers]);

    // One row per device — the "Devices" targeting pool. Device rows stay keyed on the device ID
    // because that is what enforcement matches — a device-label prefix parsed out of
    // mcpServerName, never a username (see ModuleInfoDao.fetchUsernameToDeviceIdsForEndpointShield).
    // Identities with no device on record don't appear here; they're only selectable via the
    // separate "Users" pool above.
    const availableDevices = useMemo(() => {
        let options = [];
        (agenticUsers || []).forEach(u => {
            const name = u.userName || u.userEmail;
            if (!name) return;
            (u.devices || []).filter(Boolean).forEach(deviceId => {
                options.push({ label: `${name} · ${deviceIdSuffix(deviceId)}`, value: deviceId });
            });
        });
        options = uniqueByValue(options);
        // Two different device IDs can render the same suffix — a "{host}-{first8}" label and a
        // raw machine ID can share those 8 characters — which would show as two identical rows
        // the user cannot tell apart. Spell out the full ID for any label that isn't unique.
        const labelCounts = new Map();
        options.forEach(o => labelCounts.set(o.label, (labelCounts.get(o.label) || 0) + 1));
        options.forEach(o => {
            if (labelCounts.get(o.label) > 1) o.label = `${o.label} (${o.value})`;
        });
        return options.sort((a, b) => a.label.localeCompare(b.label));
    }, [agenticUsers]);

    // Maps each selectable value — from either availableUsers (a username, or an org-scoped userId)
    // or availableDevices (a device id) — back to the identity that owns it. fetchAgenticUsers
    // already gives us each identity's userEmail/userId — building this here lets a save send the
    // exact identity behind a selection instead of leaving the backend to re-derive it by guessing
    // at a raw string. Used to build userMetadata (identity snapshots) for BOTH targetDeviceIds and
    // targetUserNames selections; it never feeds into targetDeviceIds itself.
    const deviceValueToIdentity = useMemo(() => {
        const map = new Map();
        (agenticUsers || []).forEach(u => {
            if (!u) return;
            const identity = { userName: u.userName || null, userEmail: u.userEmail || null, userId: u.userId || null };
            // An org-scoped row is selected BY its userId, and several such rows share one userName —
            // so key it on the id alone. Letting it also claim the username would have whichever org
            // row came last overwrite the all-orgs identity that legitimately owns that key.
            if (orgUuidFromUserId(u.userId)) {
                map.set(u.userId, identity);
                return;
            }
            const name = u.userName || u.userEmail;
            if (name) map.set(name, identity);
            (u.devices || []).filter(Boolean).forEach(deviceId => map.set(deviceId, identity));
        });
        return map;
    }, [agenticUsers]);

    // Rows behind the live "applies to N devices" preview — device-only, matched by device tags
    // and/or explicitly-picked targetDeviceIds, exactly as before the Users pool existed. Users
    // (targetUserNames) are a separate, direct selection with no tag-expansion — ServerSettingsStep
    // shows their count on its own (just targetUserNames.length) — so they're deliberately not
    // folded into this list.
    const matchingDeviceRows = useMemo(() => {
        const rows = [];
        (agenticUsers || []).forEach(u => {
            (u.devices || []).forEach(deviceId => {
                rows.push({ deviceId, username: u.userName, tags: u.deviceTags || [] });
            });
        });
        if (applyToAllUsers) return rows;
        const tagKeys = Object.keys(targetTags || {}).filter(k => (targetTags[k] || []).length > 0);
        const deviceSet = new Set(targetDeviceIds);
        if (tagKeys.length === 0 && deviceSet.size === 0) return [];
        return rows.filter(r => {
            const matchesAllTagKeys = tagKeys.every(k => {
                const valueSet = new Set(targetTags[k]);
                const hasMatch = r.tags.some(t => t.key === k && (valueSet.has(ALL_VALUES_SENTINEL) || valueSet.has(t.value)));
                return negatedTargetTags?.[k] ? !hasMatch : hasMatch;
            });
            const deviceOk = deviceSet.size === 0
                || (negatedTargetDeviceIds ? !deviceSet.has(r.deviceId) : deviceSet.has(r.deviceId));
            return matchesAllTagKeys && deviceOk;
        });
    }, [agenticUsers, applyToAllUsers, targetTags, targetDeviceIds, negatedTargetTags, negatedTargetDeviceIds]);

    // Fetch agentic users to populate team/role/device targeting options (Atlas only). This is
    // the single source of truth for every "users" display in the wizard — availableDevices and
    // matchingDeviceRows below are both derived from it, so the "Apply to all" and "Select Device
    // Tags & Users" popovers always show the same per-device, suffixed data.
    useEffect(() => {
        if (!isEndpointSecurityCategory()) return;
        let isActive = true;
        (async () => {
            setUsersLoading(true);
            try {
                const agenticUsersResp = await settingsApi.fetchAgenticUsers().catch(() => ({}));
                if (!isActive) return;
                setAgenticUsers(agenticUsersResp?.agenticUsers || []);
            } finally {
                if (isActive) setUsersLoading(false);
            }
        })();
        return () => { isActive = false; };
    }, []);

    return { agenticUsers, usersLoading, availableTagKeyValues, availableUsers, availableDevices, deviceValueToIdentity, matchingDeviceRows };
};
