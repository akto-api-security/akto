import { HorizontalStack, Tag } from "@shopify/polaris";
import { STEP_CONFIG } from "./owaspConfig";

const OWASP_BASE_URL = "https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/";

const OwaspTag = ({ stepNumber, threats, idOnly = false }) => {
    const resolvedThreats = stepNumber
        ? (STEP_CONFIG.find(s => s.stepNumber === stepNumber)?.owaspThreats ?? [])
        : (threats ?? []);

    const content = resolvedThreats.map(({ id, name }) => (
        <a
            key={id}
            href={OWASP_BASE_URL}
            target="_blank"
            rel="noopener noreferrer"
            style={{ textDecoration: "none" }}
        >
            <Tag>{idOnly ? id : `OWASP Agentic ${id} - ${name}`}</Tag>
        </a>
    ));

    if (idOnly) {
        return (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                {content}
            </span>
        );
    }
    return <HorizontalStack gap="2" wrap>{content}</HorizontalStack>;
};

export default OwaspTag;
