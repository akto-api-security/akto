import React from "react";
import { Badge, Link, Text } from "@shopify/polaris";
import TooltipWithLink from "@/apps/dashboard/components/shared/TooltipWithLink";

const MISCONFIGURED_DEFINITION = "This asset has a config setting that gives it excessive permissions, weakens its sandbox, or skips approval checks.";
const DOCS_URL = "https://ai-security-docs.akto.io/agentic-guardrails/concepts/misconfigurations";

export default function MisconfiguredBadge({ deviceCount }) {
    const countSentence = deviceCount > 0
        ? `${deviceCount} device${deviceCount === 1 ? "" : "s"} misconfigured. `
        : "";
    const content = (
        <Text variant="bodySm">
            {countSentence}
            {MISCONFIGURED_DEFINITION}
            {" "}
            <Link url={DOCS_URL} target="_blank">Learn more</Link>
        </Text>
    );
    return (
        <TooltipWithLink content={content} preferredPosition="above">
            <Badge size="small" status="attention">Misconfigured</Badge>
        </TooltipWithLink>
    );
}
