import { useCallback, useRef, useState } from "react";
import func from "@/util/func";
import discoveryApi from "../discoveryApi";
import { STATUS_META } from "../constants";

const UNDO_LABEL = "Undo";

// One write path for Approve / Block / Mark under review, with an Undo back to each tool's previous status.
export default function useAssetStatusUpdate(onUpdated) {
    const [pending, setPending] = useState(false);
    // Blocks a second write (a quick Approve then Undo) while one is still running.
    const busy = useRef(false);

    const restore = useCallback(async (items) => {
        if (busy.current) {
            func.setToast(true, true, "Another change is still running, try Undo again in a moment");
            return;
        }
        busy.current = true;
        setPending(true);
        const keysByStatus = {};
        items.forEach((item) => {
            (keysByStatus[item.status] = keysByStatus[item.status] || []).push(item.key);
        });
        try {
            await Promise.all(Object.entries(keysByStatus).map(([status, keys]) => discoveryApi.updateAtlasAssetStatus(keys, status)));
            func.setToast(true, false, "Change undone");
            if (onUpdated) onUpdated();
        } catch (error) {
            func.setToast(true, true, "Failed to undo the change");
        } finally {
            busy.current = false;
            setPending(false);
        }
    }, [onUpdated]);

    // items: [{ key, status }] where status is each tool's current status, kept for Undo.
    const update = useCallback(async (items, status, label) => {
        if (busy.current) return;
        busy.current = true;
        setPending(true);
        try {
            await discoveryApi.updateAtlasAssetStatus(items.map((item) => item.key), status);
            func.setToast(true, false, `${label} marked ${STATUS_META[status].label.toLowerCase()}`, {
                content: UNDO_LABEL,
                onAction: () => {
                    func.setToast(false, false, "");
                    restore(items);
                },
            });
            if (onUpdated) onUpdated();
        } catch (error) {
            func.setToast(true, true, "Failed to update the status");
        } finally {
            busy.current = false;
            setPending(false);
        }
    }, [onUpdated, restore]);

    return { update, pending };
}
