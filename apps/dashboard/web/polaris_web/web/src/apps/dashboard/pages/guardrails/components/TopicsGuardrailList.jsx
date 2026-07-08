import { Fragment, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, Box, HorizontalStack, Link, List, Text, Tooltip } from "@shopify/polaris";
import func from "@/util/func";
import {
    normalizeTopicHierarchy,
    buildTopicGuardrailPrefillForTopic,
    fetchGuardrailPolicyNamesCached,
    findExistingPolicyPerTopic,
    GUARDRAIL_POLICIES_PATH,
} from "../topicGuardrailUtils";

const LLM_OBS_PATH = "/dashboard/observe/llm-observability";

function makeTopicUrl(username, topic, subTopic) {
    const p = new URLSearchParams();
    if (username) p.set("username", username);
    if (topic) p.set("topic", topic);
    if (subTopic) p.set("subTopic", subTopic);
    return `${LLM_OBS_PATH}?${p.toString()}`;
}

function TopicRow({ topic, subTopics, username, existingPolicyName, onCreateGuardrail, onViewGuardrail }) {
    const [hovered, setHovered] = useState(false);
    const label = func.toSentenceCase(topic);

    return (
        <List.Item>
            <Box onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
                <HorizontalStack gap="2" blockAlign="center">
                    {username ? (
                        <Link url={makeTopicUrl(username, topic)} target="_blank">
                            <Badge>{label}</Badge>
                        </Link>
                    ) : (
                        <Badge>{label}</Badge>
                    )}
                    {username && subTopics.length > 0 && (
                        <Text variant="bodySm" color="subdued" as="span">
                            {subTopics.map((sub, i) => (
                                <Fragment key={sub}>
                                    <Link removeUnderline monochrome url={makeTopicUrl(username, topic, sub)} target="_blank">
                                        {func.toSentenceCase(sub)}
                                    </Link>
                                    {i < subTopics.length - 1 ? ", " : ""}
                                </Fragment>
                            ))}
                        </Text>
                    )}
                    {hovered && (existingPolicyName ? (
                        <Tooltip content="A guardrail policy already covers this topic">
                            <Link onClick={() => onViewGuardrail(existingPolicyName)}>View guardrail</Link>
                        </Tooltip>
                    ) : (
                        <Tooltip content="Create a new blocking guardrail policy for this topic">
                            <Link onClick={() => onCreateGuardrail(topic)}>Create guardrail</Link>
                        </Tooltip>
                    ))}
                </HorizontalStack>
            </Box>
        </List.Item>
    );
}

export default function TopicsGuardrailList({ topicHierarchy, username }) {
    const navigate = useNavigate();
    const normalized = useMemo(() => normalizeTopicHierarchy(topicHierarchy), [topicHierarchy]);
    const topics = useMemo(() => Object.keys(normalized), [normalized]);
    const [existingByTopic, setExistingByTopic] = useState({});

    useEffect(() => {
        if (topics.length === 0) { setExistingByTopic({}); return; }
        let cancelled = false;
        fetchGuardrailPolicyNamesCached().then(names => {
            if (!cancelled) setExistingByTopic(findExistingPolicyPerTopic(names, topics));
        });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [topics.join(",")]);

    if (topics.length === 0) return null;

    const handleCreateGuardrail = (topic) => {
        const prefill = buildTopicGuardrailPrefillForTopic(topic, topicHierarchy);
        navigate(GUARDRAIL_POLICIES_PATH, { state: { topicGuardrailPrefill: prefill } });
    };

    const handleViewGuardrail = (policyName) => {
        navigate(`${GUARDRAIL_POLICIES_PATH}?policy=${encodeURIComponent(policyName)}`);
    };

    return (
        <List>
            {topics.map(topic => (
                <TopicRow
                    key={topic}
                    topic={topic}
                    subTopics={normalized[topic]}
                    username={username}
                    existingPolicyName={existingByTopic[topic]}
                    onCreateGuardrail={handleCreateGuardrail}
                    onViewGuardrail={handleViewGuardrail}
                />
            ))}
        </List>
    );
}
