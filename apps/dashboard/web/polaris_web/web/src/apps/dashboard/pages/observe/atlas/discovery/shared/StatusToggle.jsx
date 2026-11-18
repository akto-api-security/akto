import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";

// Pressable that toggles one status filter; dimmed while another status is picked. Extra props set its spacing.
export default function StatusToggle({ selected, dimmed, accessibilityLabel, onClick, children, ...spacing }) {
    return (
        <Pressable
            onClick={onClick}
            accessibilityLabel={accessibilityLabel}
            aria-pressed={selected}
            hoverBackground="bg-subdued"
            borderRadius="2"
            opacity={dimmed ? "0.5" : undefined}
            {...spacing}
        >
            {children}
        </Pressable>
    );
}
