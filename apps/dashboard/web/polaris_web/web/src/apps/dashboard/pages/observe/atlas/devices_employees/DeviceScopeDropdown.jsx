import { useState, useCallback } from "react";
import { ActionList, Button, Popover } from "@shopify/polaris";
export const DEVICE_SCOPES = [
    { id: "all", content: "All devices" },
    { id: "desktop", content: "Desktop agents" },
    { id: "browser", content: "Browser extensions" },
];

function DeviceScopeDropdown({ value, onChange }) {
    const [active, setActive] = useState(false);
    const toggle = useCallback(() => setActive((prev) => !prev), []);

    const selected = DEVICE_SCOPES.find((scope) => scope.id === value) || DEVICE_SCOPES[0];

    const items = DEVICE_SCOPES.map((scope) => ({
        content: scope.content,
        active: scope.id === selected.id,
        onAction: () => {
            onChange(scope.id);
            setActive(false);
        },
    }));

    return (
        <Popover
            active={active}
            onClose={toggle}
            activator={<Button disclosure onClick={toggle}>{selected.content}</Button>}
        >
            <ActionList actionRole="menuitem" items={items} />
        </Popover>
    );
}

export default DeviceScopeDropdown;
