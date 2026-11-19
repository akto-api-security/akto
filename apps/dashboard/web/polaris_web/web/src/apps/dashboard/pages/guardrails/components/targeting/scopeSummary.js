import { ALL_VALUES_SENTINEL } from "../../../../components/shared/DropdownSearch";

// "N X" / "All X except N" phrasing, shared by the guardrail summary and Audit Data blocks.
export const scopePart = (negated, arr, singular) => {
    const count = arr?.length || 0;
    if (negated) return count > 0 ? `All ${singular} except ${count}` : `All ${singular}`;
    if (count === 1 && arr[0] === ALL_VALUES_SENTINEL) return `All ${singular}`;
    return count > 0 ? `${count} ${singular}` : null;
};

// A Device Tags & Users selection as text, e.g. "2 Teams, 1 User"; '' when nothing is selected.
export const describeUserScope = ({ targetTags, negatedTargetTags, targetDeviceIds, negatedTargetDeviceIds, targetUserNames, negatedTargetUserNames }) => {
    const userParts = [];
    Object.entries(targetTags || {}).forEach(([key, values]) => {
        if (values?.length > 0 || negatedTargetTags?.[key]) {
            const label = key.charAt(0).toUpperCase() + key.slice(1);
            const p = scopePart(!!negatedTargetTags?.[key], values, `${label}${(values?.length || 0) !== 1 ? 's' : ''}`);
            if (p) userParts.push(p);
        }
    });
    const devicePart = scopePart(negatedTargetDeviceIds, targetDeviceIds, `Device${targetDeviceIds?.length !== 1 ? 's' : ''}`);
    if (devicePart) userParts.push(devicePart);
    const userPart = scopePart(negatedTargetUserNames, targetUserNames, `User${targetUserNames?.length !== 1 ? 's' : ''}`);
    if (userPart) userParts.push(userPart);
    return userParts.join(', ');
};
