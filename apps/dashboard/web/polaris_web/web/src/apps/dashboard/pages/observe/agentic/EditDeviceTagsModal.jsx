import { useState, useCallback } from "react";
import { Badge, Banner, Box, Button, Checkbox, Divider, FormLayout, HorizontalStack, Modal, Text, TextField, VerticalStack } from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import settingRequests from "../../settings/api";

function initialState(usernames, rows) {
    if (usernames.length > 1) {
        return { usernames, isBulk: true, entries: [], originalManualKeys: [], clearAll: false, newKey: '', newValue: '', saving: false };
    }
    const firstUser = rows.find((r) => usernames.includes(r.id));
    const entries = (firstUser?.tags || []).map((t) => ({ key: t.key, value: t.value, source: t.source }));
    return {
        usernames,
        isBulk: false,
        entries,
        originalManualKeys: entries.filter((e) => e.source === 'manual').map((e) => e.key),
        clearAll: false,
        newKey: '',
        newValue: '',
        saving: false,
    };
}

function EditDeviceTagsModal({ usernames, rows, onClose, onSaved }) {
    const [editTagModal, setEditTagModal] = useState(() => initialState(usernames, rows));

    const updateEntryValue = useCallback((idx, value) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.map((e, i) => (i === idx && e.source === 'manual' ? { ...e, value } : e)),
        }));
    }, []);

    const removeEntry = useCallback((idx) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.filter((e, i) => !(i === idx && e.source === 'manual')),
        }));
    }, []);

    const addEntry = useCallback(() => {
        const key = editTagModal.newKey.trim().toLowerCase();
        const value = editTagModal.newValue.trim();
        if (!key) {
            func.setToast(true, true, "Device tag key cannot be empty");
            return;
        }
        if (editTagModal.entries.some((e) => e.key === key && e.value === value && e.source === 'manual')) {
            func.setToast(true, true, "This tag already exists.");
            return;
        }
        setEditTagModal((prev) => ({
            ...prev,
            entries: [...prev.entries, { key, value, source: 'manual' }],
            newKey: '',
            newValue: '',
        }));
    }, [editTagModal.newKey, editTagModal.newValue, editTagModal.entries]);

    const saveEditTag = useCallback(async () => {
        setEditTagModal((prev) => ({ ...prev, saving: true }));
        try {
            const selectedRows = rows.filter((r) => editTagModal.usernames.includes(r.id));
            const groupNames = selectedRows.map((r) => r.groupName).filter(Boolean);

            const manualEntries = editTagModal.entries.filter((e) => e.source === 'manual');
            const tags = {};
            manualEntries.forEach((e) => {
                if (!(e.key in tags)) tags[e.key] = [];
                if (e.value) tags[e.key].push(e.value);
            });
            if (editTagModal.isBulk) {
                if (editTagModal.clearAll) {
                    selectedRows.forEach((r) => (r.tags || []).forEach((t) => {
                        if (t?.key && t.source === 'manual' && !(t.key in tags)) tags[t.key] = [];
                    }));
                }
            } else {
                const currentManualKeys = new Set(manualEntries.map((e) => e.key));
                editTagModal.originalManualKeys.forEach((k) => { if (!currentManualKeys.has(k)) tags[k] = []; });
            }

            await settingRequests.bulkUpdateUserDeviceTag(groupNames, tags);

            func.setToast(true, false, "Tags updated successfully");
            onClose();
            onSaved();
        } catch {
            func.setToast(true, true, "Failed to update tags");
            setEditTagModal((prev) => ({ ...prev, saving: false }));
        }
    }, [editTagModal, onClose, onSaved, rows]);

    const hasNonManualEntry = editTagModal.entries.some((e) => e.source && e.source !== 'manual');

    return (
        <Modal
            open
            onClose={onClose}
            title={`Edit device tags — ${editTagModal.usernames?.length > 1 ? `${editTagModal.usernames.length} users` : (rows.find((r) => editTagModal.usernames?.[0] === r.id)?.groupName || '')}`}
            primaryAction={{ content: 'Save', onAction: saveEditTag, loading: editTagModal.saving }}
            secondaryActions={[{ content: 'Cancel', onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {editTagModal.isBulk ? (
                        <Banner tone="info">
                            <Text variant="bodySm">
                                Editing {editTagModal.usernames.length} users at once — their existing tags may differ, so they aren't shown here. A tag you add below is applied to all of them; use "Clear all" to remove every manually-set device tag from all of them instead.
                            </Text>
                        </Banner>
                    ) : hasNonManualEntry && (
                        <Banner tone="info">
                            <Text variant="bodySm">
                                Tags synced from elsewhere (e.g. Okta) are shown for reference and can't be edited here — they're managed at the source and will reappear on the next sync. A manual tag can coexist with one of these on the same key; both apply.
                            </Text>
                        </Banner>
                    )}
                    <FormLayout>
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Existing tags</Text>
                            {editTagModal.entries.length === 0 ? (
                                <Text variant="bodySm" color="subdued">
                                    {editTagModal.isBulk ? "Not shown for multiple users — see note above." : "No device tags yet."}
                                </Text>
                            ) : (
                                editTagModal.entries.map((entry, idx) => {
                                    const isManual = entry.source === 'manual';
                                    return (
                                        <HorizontalStack key={`${entry.key}|${entry.source}|${idx}`} gap="2" blockAlign="center" wrap={false}>
                                            <Box width="100px">
                                                <Text variant="bodyMd" fontWeight="medium" truncate>{entry.key}</Text>
                                            </Box>
                                            <Box minWidth="0" width="100%">
                                                <TextField
                                                    label={entry.key}
                                                    labelHidden
                                                    value={entry.value}
                                                    onChange={isManual ? (v) => updateEntryValue(idx, v) : () => {}}
                                                    disabled={!isManual}
                                                    autoComplete="off"
                                                />
                                            </Box>
                                            <Box minWidth="72px">
                                                {isManual ? (
                                                    <Button plain destructive icon={DeleteMinor} onClick={() => removeEntry(idx)} accessibilityLabel={`Remove ${entry.key}`} />
                                                ) : (
                                                    <Badge size="small">{entry.source}</Badge>
                                                )}
                                            </Box>
                                        </HorizontalStack>
                                    );
                                })
                            )}
                        </VerticalStack>
                        <Divider />
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Add a tag</Text>
                            <HorizontalStack gap="2" blockAlign="end" wrap={false}>
                                <Box minWidth="0" width="100%">
                                    <TextField
                                        label="Key"
                                        placeholder="e.g. department"
                                        value={editTagModal.newKey}
                                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, newKey: v }))}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Box minWidth="0" width="100%">
                                    <TextField
                                        label="Value"
                                        value={editTagModal.newValue}
                                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, newValue: v }))}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Box paddingBlockStart="6"><Button onClick={addEntry}>Add</Button></Box>
                            </HorizontalStack>
                        </VerticalStack>
                        {editTagModal.isBulk && (
                            <Checkbox
                                label={`Clear all manually-set device tags for these ${editTagModal.usernames.length} users`}
                                checked={editTagModal.clearAll}
                                onChange={(checked) => setEditTagModal((prev) => ({ ...prev, clearAll: checked }))}
                            />
                        )}
                    </FormLayout>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}

export default EditDeviceTagsModal;
