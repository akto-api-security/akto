import React from "react";
import { Button } from "@shopify/polaris";
import { MagicMinor } from "@shopify/polaris-icons";

// The "open the insights flyout" button — same icon/shape everywhere it appears
// (AgenticAssetsPage, GuardrailPolicies, ViolationsPage), only the label differs.
export default function InsightsEntryButton({ granted, onClick, label }) {
    if (!granted) return null;
    return <Button icon={MagicMinor} onClick={onClick}>{label}</Button>;
}
