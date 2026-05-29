// Shared utilities for NHI Governance pages
import func from "@/util/func";

export function formatRelativeTime(timestamp, fallback = "Never") {
    if (!timestamp) return fallback;
    return func.prettifyEpoch(timestamp);
}
