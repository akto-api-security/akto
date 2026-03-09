import { HorizontalStack, Tag } from "@shopify/polaris";

const OWASP_BASE_URL = "https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/";

const OwaspTag = ({ threats }) => (
    <HorizontalStack gap="2" wrap>
        {threats.map(({ id, name }) => (
            <a
                key={id}
                href={OWASP_BASE_URL}
                target="_blank"
                rel="noopener noreferrer"
                style={{ textDecoration: "none" }}
            >
                <Tag>OWASP Agentic {id} - {name}</Tag>
            </a>
        ))}
    </HorizontalStack>
);

export default OwaspTag;
