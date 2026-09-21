import { saveAs } from "file-saver";
import func from "@/util/func";
import { redactSampleDataByKeywords } from "./redactSampleData";
import { extractBehaviour, extractRuleViolated, parseStoredRiskScore } from "./formatUtils";
import { isAgenticSecurityCategory, isEndpointSecurityCategory } from "@/apps/main/labelHelper";

// Shared by GuardrailDetection.jsx, ThreatDetectionPage.jsx and ViolationsPage.jsx (old + new
// layout) so the JSON shape/redaction stays in one place instead of three copies.
export function downloadMaliciousEventsAsJson(events, fileName) {
    const isAgenticOrAtlas = isAgenticSecurityCategory() || isEndpointSecurityCategory();
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
        status: ev.status,
        host: ev.host,
        // Same category gating as the CSV export / SusDataTable.jsx's on-screen columns.
        ...(isAgenticOrAtlas
            ? {
                riskScore: parseStoredRiskScore(ev.metadata) ?? "",
                behaviour: extractBehaviour(ev.metadata) || "",
                ruleViolated: extractRuleViolated(ev.metadata) || "",
            }
            : { successfulExploit: ev.successfulExploit }),
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

const BASE_CSV_HEADERS = [
    { text: "ID", value: "_id" },
    { text: "Severity", value: "severity" },
    { text: "Status", value: "status" },
    { text: "Actor", value: "actor" },
    { text: "Host", value: "host" },
    { text: "Endpoint", value: "latestApiEndpoint" },
    { text: "Method", value: "latestApiMethod" },
    { text: "Collection Id", value: "latestApiCollectionId" },
    { text: "Category", value: "category" },
    { text: "Sub Category", value: "subCategory" },
    { text: "Type", value: "type" },
    { text: "Policy", value: "filterId" },
];
// "Successful Exploit" is only ever populated for API Security; risk score / behaviour / rule
// violated only for Agentic (Argus) and Endpoint (Atlas) — same gating as SusDataTable.jsx's columns.
const API_SECURITY_CSV_HEADERS = [{ text: "Successful Exploit", value: "successfulExploit" }];
const AGENTIC_ATLAS_CSV_HEADERS = [
    { text: "Risk Score", value: "riskScore" },
    { text: "Behaviour", value: "behaviour" },
    { text: "Rule Violated", value: "ruleViolated" },
];
const TRAILING_CSV_HEADERS = [{ text: "Detected At", value: "detectedAt" }];

// fileName without extension — exportTableAsCSV appends ".csv".
export function downloadMaliciousEventsAsCsv(events, fileName) {
    const isAgenticOrAtlas = isAgenticSecurityCategory() || isEndpointSecurityCategory();
    const headers = [
        ...BASE_CSV_HEADERS,
        ...(isAgenticOrAtlas ? AGENTIC_ATLAS_CSV_HEADERS : API_SECURITY_CSV_HEADERS),
        ...TRAILING_CSV_HEADERS,
    ];
    const rows = (events || []).map(ev => ({
        _id: ev.id,
        severity: ev.severity,
        status: ev.status,
        actor: ev.actor,
        host: ev.host,
        latestApiEndpoint: ev.url || ev.latestApiEndpoint,
        latestApiMethod: ev.method || ev.latestApiMethod,
        latestApiCollectionId: ev.apiCollectionId || ev.latestApiCollectionId,
        category: ev.category,
        subCategory: ev.subCategory,
        type: ev.type,
        filterId: ev.filterId,
        successfulExploit: ev.successfulExploit,
        riskScore: parseStoredRiskScore(ev.metadata) ?? "",
        behaviour: extractBehaviour(ev.metadata) || "",
        ruleViolated: extractRuleViolated(ev.metadata) || "",
        detectedAt: func.prettifyEpoch(ev.timestamp || ev.detectedAt || 0),
    }));
    func.exportTableAsCSV(headers, rows, fileName);
}
