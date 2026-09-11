import { saveAs } from "file-saver";
import func from "@/util/func";
import { redactSampleDataByKeywords } from "./redactSampleData";

// Shared by GuardrailDetection.jsx, ThreatDetectionPage.jsx and ViolationsPage.jsx (old + new
// layout) so the JSON shape/redaction stays in one place instead of three copies.
export function downloadMaliciousEventsAsJson(events, fileName) {
    const jsonData = (events || []).map(ev => ({
        _id: ev.id,
        actor: ev.actor,
        category: ev.category,
        country: ev.country,
        detectedAt: { $numberLong: String(ev.timestamp) || String(ev.detectedAt) },
        eventType: ev.eventType,
        filterId: ev.filterId,
        latestApiCollectionId: ev.apiCollectionId || ev.latestApiCollectionId,
        latestApiEndpoint: ev.url || ev.latestApiEndpoint,
        latestApiIp: ev.ip || ev.latestApiIp,
        latestApiMethod: ev.method || ev.latestApiMethod,
        subCategory: ev.subCategory,
        type: ev.type,
        refId: ev.refId,
        severity: ev.severity,
        latestApiOrig:
            ev.payload != null || ev.latestApiOrig != null
                ? redactSampleDataByKeywords(ev.payload ?? ev.latestApiOrig)
                : ev.payload ?? ev.latestApiOrig,
        metadata: ev.metadata,
    }));

    const blob = new Blob([JSON.stringify(jsonData, null, 2)], { type: "application/json;charset=UTF-8" });
    saveAs(blob, fileName);
    func.setToast(true, false, "JSON exported successfully");
}
