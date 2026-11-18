import { HorizontalStack, Text } from "@shopify/polaris";
import Pressable from "../../pages/dashboard/ask/components/Pressable";
import "../../pages/dashboard/ask/askOverlay.css";

/*
 * Row of outlined filter buttons, e.g. status filters next to a table search. Multi-select by
 * default; `single` makes a click replace the selection (clicking the selected one clears it).
 * Selected buttons get the purple border/fill used by the Ask bar's chips.
 *
 * <FilterToggleButtons
 *     options={["Approved", "Unapproved", "Blocked"]}
 *     selected={status.values}
 *     onChange={status.apply}
 *     single
 * />
 */
function FilterToggleButtons({ options = [], selected = [], onChange, single = false }) {
    const toggle = (option) => {
        if (selected.includes(option)) onChange(selected.filter((v) => v !== option));
        else onChange(single ? [option] : [...selected, option]);
    };
    return (
        <HorizontalStack gap="2" wrap={false}>
            {options.map((option) => {
                const on = selected.includes(option);
                return (
                    <Pressable
                        key={option}
                        onClick={() => toggle(option)}
                        accessibilityLabel={`${on ? "Remove" : "Add"} ${option} filter`}
                        background={on ? "bg-primary-subdued-hover" : "bg"}
                        hoverBackground={on ? "bg-primary-subdued-hover" : "bg-subdued"}
                        borderWidth="1"
                        borderColor={on ? "border-magic-strong" : "border"}
                        hoverBorderColor={on ? "border-magic-strong" : "border-strong"}
                        borderRadius="2"
                        minWidth="max-content"
                        paddingBlockStart="1"
                        paddingBlockEnd="1"
                        paddingInlineStart="3"
                        paddingInlineEnd="3"
                    >
                        <Text as="span" variant="bodyMd">{option}</Text>
                    </Pressable>
                );
            })}
        </HorizontalStack>
    );
}

export default FilterToggleButtons;
