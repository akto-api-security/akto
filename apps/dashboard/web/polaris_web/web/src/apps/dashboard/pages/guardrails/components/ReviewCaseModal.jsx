import { useEffect, useState } from "react";
import { Button, Modal, Select, Text, TextField, VerticalStack } from "@shopify/polaris";
import useCaseOwners, { ASSIGN_ROLES_MESSAGE } from "@/apps/dashboard/pages/threat_detection/utils/useCaseOwners";
import { CASE_NOTES_MAX_LENGTH, canAssignToMe, changedCaseFields, ownerOptionLabel } from "@/apps/dashboard/pages/threat_detection/utils/caseManagement";

// The owner choices of a case: unassigned, the account's members, and the current owner even if they have
// since left the account, so the select always shows what is actually set.
export function ownerSelectOptions(owners, currentOwner) {
    const options = [{ label: "Unassigned", value: "" }, ...(owners || []).map((o) => ({ label: ownerOptionLabel(o), value: o.login }))];
    if (currentOwner && !options.some((o) => o.value === currentOwner)) options.push({ label: currentOwner, value: currentOwner });
    return options;
}

// "Mark for review": opens a case, optionally with an owner and notes. onConfirm gets only the fields the user
// set ({ caseOwner?, caseNotes? }), so marking events that already have an owner or notes never clears them by
// leaving the dialog blank.
export default function ReviewCaseModal({ open, onClose, onConfirm, summary, initialOwner = "", initialNotes = "", loading = false }) {
    const { owners, canAssign } = useCaseOwners();
    const [owner, setOwner] = useState(initialOwner);
    const [notes, setNotes] = useState(initialNotes);

    useEffect(() => {
        if (open) {
            setOwner(initialOwner);
            setNotes(initialNotes);
        }
    }, [open, initialOwner, initialNotes]);

    const me = window.USER_NAME;

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Mark for review"
            primaryAction={{
                content: "Mark for review",
                loading,
                onAction: () => onConfirm(changedCaseFields({ owner, notes }, { initialOwner, initialNotes })),
            }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {summary && <Text variant="bodyMd">{summary}</Text>}
                    <VerticalStack gap="1">
                        <Select
                            label="Owner"
                            options={ownerSelectOptions(owners, owner)}
                            value={owner}
                            onChange={setOwner}
                            disabled={!canAssign}
                            helpText={canAssign ? "Who is responsible for reviewing this. You can change it later." : ASSIGN_ROLES_MESSAGE}
                        />
                        {canAssignToMe({ canAssign, me, owner, owners }) && <Button plain onClick={() => setOwner(me)}>Assign to me</Button>}
                    </VerticalStack>
                    <TextField
                        label="Notes"
                        value={notes}
                        onChange={setNotes}
                        multiline={4}
                        maxLength={CASE_NOTES_MAX_LENGTH}
                        showCharacterCount
                        autoComplete="off"
                        placeholder="Why is this being reviewed? What should the owner look at?"
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}
