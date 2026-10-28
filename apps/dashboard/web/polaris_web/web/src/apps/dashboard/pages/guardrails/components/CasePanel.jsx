import { useEffect, useState } from "react";
import { Box, Button, Divider, HorizontalStack, Select, Text, TextField, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import { usePermissions } from "@/util/permissions";
import AllowedAction from "@/apps/dashboard/components/shared/AllowedAction";
import ActivityTracker from "@/apps/dashboard/pages/dashboard/components/ActivityTracker";
import threatDetectionApi from "@/apps/dashboard/pages/threat_detection/api";
import useCaseOwners, { ASSIGN_ROLES_MESSAGE } from "@/apps/dashboard/pages/threat_detection/utils/useCaseOwners";
import { CASE_NOTES_MAX_LENGTH, canAssignToMe, changedCaseFields } from "@/apps/dashboard/pages/threat_detection/utils/caseManagement";
import { ownerSelectOptions } from "./ReviewCaseModal";

// The case of an event marked for review, as shown in a flyout: who owns it and the notes, editable in place,
// with the case's timeline below them (activity: ActivityTracker entries, when the screen has any to show).
// eventIds are every event the case covers (a grouped activity is one case). onSaved gets the fields that were
// saved ({ caseOwner?, caseNotes? }) so the screen can show them, and the timeline entries they add, at once.
export default function CasePanel({ eventIds, caseOwner = "", caseNotes = "", activity, onSaved }) {
    const { canCall } = usePermissions();
    const canUpdate = canCall("api/updateMaliciousEventStatus");
    const { owners, canAssign } = useCaseOwners();
    const [owner, setOwner] = useState(caseOwner);
    const [notes, setNotes] = useState(caseNotes);
    const [saving, setSaving] = useState(false);

    const eventKey = (eventIds || []).join(",");
    useEffect(() => {
        setOwner(caseOwner);
        setNotes(caseNotes);
    }, [eventKey, caseOwner, caseNotes]);

    const fields = changedCaseFields({ owner, notes }, { initialOwner: caseOwner, initialNotes: caseNotes });
    const dirty = Object.keys(fields).length > 0;
    const me = window.USER_NAME;

    const save = async () => {
        setSaving(true);
        try {
            const result = await threatDetectionApi.updateMaliciousEventStatus({ eventIds, ...fields });
            if (result?.updateSuccess) {
                func.setToast(true, false, "Case updated");
                onSaved?.(fields);
            } else {
                func.setToast(true, true, "Failed to update the case");
            }
        } catch {
            func.setToast(true, true, "Failed to update the case");
        } finally {
            setSaving(false);
        }
    };

    return (
        <VerticalStack gap="0">
            <Box padding="4">
                <VerticalStack gap="4">
                    <VerticalStack gap="1">
                        <Select
                            label="Owner"
                            options={ownerSelectOptions(owners, owner)}
                            value={owner}
                            onChange={setOwner}
                            disabled={!canUpdate || !canAssign}
                            helpText={canAssign ? undefined : ASSIGN_ROLES_MESSAGE}
                        />
                        {canAssignToMe({ canAssign, me, owner, owners }) && <Button plain onClick={() => setOwner(me)}>Assign to me</Button>}
                    </VerticalStack>
                    <TextField
                        label="Notes"
                        value={notes}
                        onChange={setNotes}
                        multiline={6}
                        maxLength={CASE_NOTES_MAX_LENGTH}
                        showCharacterCount
                        autoComplete="off"
                        placeholder="What was found, what was decided, what is left to do"
                        disabled={!canUpdate}
                    />
                    <HorizontalStack gap="2" align="space-between" blockAlign="center">
                        <Text variant="bodySm" color="subdued">Every change is added to the timeline.</Text>
                        <AllowedAction allowed={canUpdate}>
                            <Button primary loading={saving} disabled={!dirty} onClick={save}>Save</Button>
                        </AllowedAction>
                    </HorizontalStack>
                </VerticalStack>
            </Box>
            {activity?.length > 0 && (
                <>
                    <Divider />
                    <Box paddingBlockStart="4" paddingInlineStart="4">
                        <Text variant="headingSm" as="h3">Timeline</Text>
                    </Box>
                    <ActivityTracker latestActivity={activity} />
                </>
            )}
        </VerticalStack>
    );
}
