import React from "react";
import { Badge, Text, Tooltip } from "@shopify/polaris";

const SHOWN = 2;

// A skill's risk tags across every user's copy of it (row.skillTags, merged server-side):
// the first few as badges, the rest behind a "+N" tooltip.
export default function SkillTagBadges({ tags }) {
    if (!tags?.length) return null;
    const hidden = tags.slice(SHOWN);
    return (
        <>
            {tags.slice(0, SHOWN).map((tag) => <Badge key={tag} size="small" status="warning">{tag}</Badge>)}
            {hidden.length > 0 && (
                <Tooltip content={<Text variant="bodySm">{hidden.join(", ")}</Text>} preferredPosition="above">
                    <Badge size="small">{`+${hidden.length}`}</Badge>
                </Tooltip>
            )}
        </>
    );
}
