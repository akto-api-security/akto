import { ActionList, Avatar, Box, Scrollable } from '@shopify/polaris';
import func from '@/util/func';
import { isMCPSecurityCategory, isGenAISecurityCategory, isAgenticSecurityCategory, isEndpointSecurityCategory } from '../../../../main/labelHelper';

export const getCompliances = () => {
    if (isMCPSecurityCategory() || isAgenticSecurityCategory() || isGenAISecurityCategory() || isEndpointSecurityCategory()) {
        return ["OWASP Agentic Top 10", "OWASP LLM", "EU AI Act", "NIST AI Risk Management Framework", "CIS Controls", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP", "MITRE ATLAS"];
    }
    return ["CIS Controls", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP"];
};

/** Same scroll pattern as DateRangePicker: Scrollable + fixed height inside Popover.Pane. */
export default function ComplianceMenu({ items, onSelect }) {
    return (
        <Scrollable style={{ height: '334px' }}>
            <ActionList
                actionRole="menuitem"
                items={items.map((frameworkName) => ({
                    content: frameworkName,
                    prefix: (
                        <Box>
                            <Avatar
                                source={func.getComplianceIcon(frameworkName)}
                                shape="square"
                                size="extraSmall"
                            />
                        </Box>
                    ),
                    onAction: () => onSelect(frameworkName),
                }))}
            />
        </Scrollable>
    );
}
