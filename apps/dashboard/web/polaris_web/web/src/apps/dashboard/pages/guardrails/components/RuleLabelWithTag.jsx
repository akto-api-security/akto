import { Text } from "@shopify/polaris";
import OwaspTag from "./OwaspTag";
import func from "@/util/func";

/**
 * Renders rule name with OWASP tag(s) beside it. Tag is shown only for demo accounts.
 * Use as checkbox label, e.g. label={<RuleLabelWithTag name="Tool Misuse" threats={...} />}
 */
const RuleLabelWithTag = ({ name, threats }) => {
    if (!func.isDemoAccount()) {
        return <Text as="span">{name}</Text>;
    }
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <Text as="span">{name}</Text>
            <OwaspTag threats={threats} idOnly />
        </span>
    );
};

export default RuleLabelWithTag;
