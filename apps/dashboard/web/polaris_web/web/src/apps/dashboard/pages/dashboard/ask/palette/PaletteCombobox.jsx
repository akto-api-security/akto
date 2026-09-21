import { Combobox, Icon, Listbox } from "@shopify/polaris"
import { SearchMinor } from "@shopify/polaris-icons"

// Polaris Combobox + Listbox, the same pattern ApiCollectionsDropdown.jsx already uses. This is
// the single biggest simplification available for the palette: it gives role="combobox",
// aria-expanded, aria-activedescendant, listbox/option roles, and up/down/Enter with
// end-wrapping for free — no hand-rolled arrow-key code anywhere in this file.
//
// Options render in EXACTLY the order resolveCommand.resolve() returns them, deliberately not
// grouped into sections — Listbox auto-highlights whichever option is first in DOM order, and
// that is the entire mechanism behind askFirst (see resolveCommand.js): reordering here would
// silently break which option Enter selects.
export default function PaletteCombobox({ value, onChange, options, onSelect, placeholder }) {
    return (
        <Combobox
            preferredPosition="below"
            activator={
                <Combobox.TextField
                    autoFocus
                    prefix={<Icon source={SearchMinor} />}
                    onChange={onChange}
                    label="Ask Akto or jump to a page"
                    labelHidden
                    value={value}
                    placeholder={placeholder || "Ask Akto, or jump anywhere…"}
                    autoComplete="off"
                />
            }
        >
            <Listbox onSelect={(id) => onSelect(options.find((o) => o.id === id))}>
                {options.map((option) => (
                    <Listbox.Option key={option.id} value={option.id} accessibilityLabel={option.label}>
                        {option.label}
                    </Listbox.Option>
                ))}
            </Listbox>
        </Combobox>
    )
}
