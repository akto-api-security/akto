import { HorizontalStack, Tag } from "@shopify/polaris";
import { STEP_CONFIG } from "./owaspConfig";

const OWASP_BASE_URL = "https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/";

const OwaspTag = ({ stepNumber, threats }) => {
    const resolvedThreats = stepNumber
        ? (STEP_CONFIG.find(s => s.stepNumber === stepNumber)?.owaspThreats ?? [])
        : (threats ?? []);

    return (
        <HorizontalStack gap="2" wrap>
            {resolvedThreats.map(({ id, name }) => (
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
};

export default OwaspTag;
