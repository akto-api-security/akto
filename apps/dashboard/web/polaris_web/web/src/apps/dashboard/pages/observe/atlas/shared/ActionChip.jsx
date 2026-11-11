import { Text } from "@shopify/polaris";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";

const NOOP = () => {};

// Outlined chip button for secondary actions and filters; size "slim" for rows.
export default function ActionChip({ label, onClick, disabled, size = "medium" }) {
    const block = size === "slim" ? "1" : "2";
    return (
        <Pressable
            accessibilityLabel={label}
            onClick={disabled ? NOOP : onClick}
            background="bg"
            hoverBackground="bg-subdued"
            borderWidth="1"
            borderColor="border"
            borderRadius="2"
            opacity={disabled ? "0.5" : undefined}
            paddingBlockStart={block}
            paddingBlockEnd={block}
            paddingInlineStart="3"
            paddingInlineEnd="3"
        >
            <Text as="span" fontWeight="medium">{label}</Text>
        </Pressable>
    );
}
