import { VerticalStack, HorizontalStack, Text, Popover, TextField, Link, Tag, Banner, Button, InlineError, Box } from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
import { useState, useEffect, useRef } from "react";
import { produce } from "immer";
import DropdownSearch, { ALL_VALUES_SENTINEL } from "../../../../components/shared/DropdownSearch";
import Dropdown from "../../../../components/layouts/Dropdown";
import func from "@/util/func";

// Condition rows ("Where my <type> are <values>") shared by the guardrail Scope step and Audit Data blocks.

export const conditionsReducer = produce((draft, action) => func.conditionsReducer(draft, action));

const USER_BETA_BANNER_TEXT = "User targeting is in beta and currently supported only for Browser extensions and the Claude Desktop app.";

const DISPLAY_CAP = 1000;

export const CountPopover = ({ count, label, items, text }) => {
    const [active, setActive] = useState(false);
    const [pinned, setPinned] = useState(false);
    const [search, setSearch] = useState('');
    const closeTimer = useRef(null);

    const open = () => { if (closeTimer.current) clearTimeout(closeTimer.current); setActive(true); };
    const scheduleClose = () => {
        if (pinned) return;
        closeTimer.current = setTimeout(() => setActive(false), 200);
    };
    const pin = () => { open(); setPinned(true); };

    const filtered = (items || []).filter(item =>
        (item.label || '').toLowerCase().includes(search.toLowerCase())
    );
    return (
        <Popover
            active={active}
            activator={
                <span onMouseEnter={open} onMouseLeave={scheduleClose} style={{ cursor: 'pointer' }}>
                    <Link>{text || `${count} ${label}`}</Link>
                </span>
            }
            onClose={() => { setActive(false); setPinned(false); setSearch(''); }}
        >
            <div onMouseEnter={open} onMouseLeave={scheduleClose}>
                <Popover.Pane fixed>
                    <Box padding="2" minWidth="240px" maxWidth="320px">
                        <TextField
                            placeholder={`Search ${label}...`}
                            value={search}
                            onChange={setSearch}
                            onFocus={pin}
                            autoComplete="off"
                        />
                    </Box>
                </Popover.Pane>
                <Popover.Pane>
                    <Box padding="3" maxWidth="320px">
                        {filtered.length === 0
                            ? <Text tone="subdued" variant="bodySm">No {label} found</Text>
                            : <HorizontalStack gap="2" wrap>
                                {/* Index in the key: items can repeat a value (one user stored twice), which breaks filtering. */}
                                {filtered.map((item, i) => (
                                    <Tag key={`${i}-${item.value}`}>{item.label}</Tag>
                                ))}
                              </HorizontalStack>
                        }
                    </Box>
                </Popover.Pane>
            </div>
        </Popover>
    );
};

// Once (per condition list, per mount) after its options have loaded: drop any selected value
// that no longer matches a live option for its type (a server/device removed from the list, or a
// tag value no longer on any user). Without this, a condition's stored value count can silently
// diverge from what's actually visible/checked/enforceable. `enabled` gates this off for Argus —
// backward-compat values there may not yet have a matching option.
export const usePruneStaleConditionValues = (enabled, ready, conditions, dispatch, valSets) => {
    const ranRef = useRef(false);
    useEffect(() => {
        if (!enabled || ranRef.current || !ready) return;
        ranRef.current = true;
        // Descending order: deleting a higher index never shifts the position of one not yet visited.
        for (let index = conditions.length - 1; index >= 0; index--) {
            const c = conditions[index];
            const valSet = valSets[c.type];
            if (!valSet) {
                dispatch({ type: 'delete', index });
                continue;
            }
            // Never prune a negated row's values: removing an excluded value silently WIDENS what the
            // policy matches (e.g. an excluded server that's just offline right now must stay excluded).
            if (c.negated) continue;
            // Never prune the Include wildcard sentinel — it isn't a real option value by design.
            if (c.values?.length === 1 && c.values[0] === ALL_VALUES_SENTINEL) continue;
            if (valSet.size === 0) continue;
            const valid = (c.values || []).filter(v => valSet.has(v));
            if (valid.length !== (c.values || []).length) {
                dispatch({ type: 'updateKey', index, key: 'values', obj: valid });
            }
        }
    }, [ready]);
};

export const sortSelectedFirst = (options, selected) => {
    const selectedSet = new Set(selected);
    return [
        ...options.filter(o => selectedSet.has(o.value)),
        ...options.filter(o => !selectedSet.has(o.value)),
    ];
};

// Custom underline-tab strip, not Polaris LegacyTabs — LegacyTabs collapses into a "..." overflow menu at this popover's width
const NEGATION_TABS = [
    { label: 'Include', value: false },
    { label: 'Exclude', value: true },
];
const negationHeader = (negated, onToggle) => (
    <div style={{ display: 'flex' }}>
        {NEGATION_TABS.map(tab => {
            const isSelected = negated === tab.value;
            return (
                <div
                    key={tab.label}
                    onClick={() => onToggle(tab.value)}
                    style={{
                        flex: 1,
                        textAlign: 'center',
                        padding: '10px 0 8px',
                        cursor: 'pointer',
                        borderBottom: isSelected ? '2px solid #6c5ce7' : '1px solid var(--p-border-subdued, #e1e3e5)',
                    }}
                >
                    <Text variant="bodyMd" fontWeight={isSelected ? 'semibold' : 'regular'} tone={isSelected ? undefined : 'subdued'}>
                        {tab.label}
                    </Text>
                </div>
            );
        })}
    </div>
);

const typeLabel = (typeOptions, type) => (typeOptions.find(o => o.value === type)?.label || type)
    .replace(/\s*\[\d+\]/, '')
    .replace(/\s*\(Beta\)/i, '')
    .trim();

// orTypes (e.g. USER) match as an alternative to the other rows, so they're shown last and labelled "Or".
export const ConditionRows = ({ conditions, typeOptions, dispatch, operator = 'OR', showError = false, getNegationProps = null, getOptionsForType, orTypes = [] }) => {
    const isOrType = (condition) => orTypes.includes(condition.type);
    // Display order only; `index` stays the condition's position for dispatch.
    const displayed = conditions.map((condition, index) => ({ condition, index }))
        .sort((a, b) => Number(isOrType(a.condition)) - Number(isOrType(b.condition)));
    const operatorLabel = (condition) => {
        const op = isOrType(condition) ? 'OR' : operator;
        return op.charAt(0).toUpperCase() + op.slice(1).toLowerCase();
    };
    const rowCaption = (typeOptions, condition, negated) => {
        const opts = getOptionsForType(condition.type);
        const label = typeLabel(typeOptions, condition.type);
        const values = condition.values || [];
        const isIncludeAll = !negated && values.length === 1 && values[0] === ALL_VALUES_SENTINEL;
        const isExcludeNone = negated && values.length === 0;
        if (isIncludeAll || isExcludeNone) {
            return { text: `All ${label}`, items: opts, label };
        }
        if (values.length === 0) return null;
        const items = values.map(v => ({ value: v, label: opts.find(o => o.value === v)?.label || v }));
        return negated
            ? { text: `All ${label} except ${values.length}`, items, label }
            : { text: `${values.length} ${label}`, items, label };
    };

    const usedTypes = new Set(conditions.map(c => c.type));
    const availableTypeOptions = typeOptions.filter(o => !o.disabled);
    const allTypesFilled = availableTypeOptions.length === 0 || availableTypeOptions.every(o => usedTypes.has(o.value));
    const nextUnusedType = availableTypeOptions.find(o => !usedTypes.has(o.value))?.value;
    // Zero rows is unfinished; Exclude-with-nothing is a deliberate "apply to everything" scope, not an unfinished one
    const isDirty = conditions.length === 0;

    return (
        <VerticalStack gap="3">
            {displayed.map(({ condition, index }, position) => {
                const otherUsedTypes = new Set(conditions.filter((_, i) => i !== index).map(c => c.type));
                const rowTypeOptions = typeOptions.map(o => ({
                    ...o,
                    disabled: o.disabled || otherUsedTypes.has(o.value),
                }));
                const negationProps = getNegationProps?.(condition.type);
                const negated = !!negationProps?.negated;
                return (
                    <VerticalStack key={index} gap="2">
                        <HorizontalStack gap="2" blockAlign="center">
                            <div style={{ minWidth: 64, textAlign: 'right' }}>
                                {position === 0
                                    ? <Text variant="bodyMd" tone="subdued" fontWeight="medium">Where my</Text>
                                    : <Text variant="bodyMd" tone="subdued" fontWeight="medium">{operatorLabel(condition)}</Text>
                                }
                            </div>
                            <div style={{ flex: '1', minWidth: 0 }}>
                                <Dropdown
                                    id={`cond-type-${condition.type}-${index}`}
                                    menuItems={rowTypeOptions}
                                    disabledOptions={rowTypeOptions.filter(o => o.disabled).map(o => o.value)}
                                    // value, not initial: Dropdown re-syncs value, so the count updates once users load.
                                    value={rowTypeOptions.find(o => o.value === condition.type)?.label || rowTypeOptions[0]?.label}
                                    selected={(val) => {
                                        dispatch({ type: 'updateKey', index, key: 'type', obj: val });
                                        dispatch({ type: 'updateKey', index, key: 'values', obj: [] });
                                        dispatch({ type: 'updateKey', index, key: 'otherValues', obj: [] });
                                        negationProps?.onToggle(false);
                                    }}
                                />
                            </div>
                            <Text variant="bodyMd" tone="subdued" fontWeight="medium">{getOptionsForType(condition.type).length > 1 ? 'are' : 'is'}</Text>
                            <div style={{ flex: '2', minWidth: 0 }}>
                                <DropdownSearch
                                    id={`cond-val-${condition.type}-${index}`}
                                    placeholder="Select value"
                                    headerContent={negationProps ? negationHeader(negated, (val) => {
                                        if (val === negated) return; // clicking the already-active tab is a no-op
                                        // Swap rather than clear: the polarity you're leaving keeps its picks in
                                        // otherValues, restored if you switch back without touching the other tab.
                                        // Only clears the first time you visit a tab you've never touched — never
                                        // silently reinterprets one polarity's picks as the other's. Two updateKey
                                        // dispatches, not one combined action — both read from this closure's
                                        // condition snapshot, so dispatch order between them doesn't matter.
                                        negationProps.onToggle(val);
                                        dispatch({ type: 'updateKey', index, key: 'values', obj: condition.otherValues || [] });
                                        dispatch({ type: 'updateKey', index, key: 'otherValues', obj: condition.values || [] });
                                    }) : undefined}
                                    optionsList={sortSelectedFirst(getOptionsForType(condition.type), condition.values || [])}
                                    setSelected={(vals) => dispatch({ type: 'updateKey', index, key: 'values', obj: vals })}
                                    preSelected={condition.values || []}
                                    allowMultiple={true}
                                    negated={negated}
                                    onToggleNegated={negationProps?.onToggle}
                                    disabled={getOptionsForType(condition.type).length === 0}
                                    value={negated
                                        ? ((condition.values || []).length > 0 ? `All except ${condition.values.length}` : 'All selected')
                                        : ((condition.values || []).length === 1 && condition.values[0] === ALL_VALUES_SENTINEL ? 'All selected'
                                            : (condition.values || []).length > 0 ? `${condition.values.length} selected` : undefined)}
                                    sliceMaxVal={DISPLAY_CAP}
                                />
                            </div>
                            <Button icon={DeleteMinor} onClick={() => {
                                dispatch({ type: 'delete', index });
                                // Reset the toggle too, so "Exclude" can't linger with no row left to undo it
                                negationProps?.onToggle(false);
                            }} />
                        </HorizontalStack>
                    </VerticalStack>
                );
            })}
            {(() => {
                const segments = displayed
                    .map(({ condition: c }) => {
                        const seg = rowCaption(typeOptions, c, !!getNegationProps?.(c.type)?.negated);
                        return seg && { ...seg, isOr: isOrType(c) };
                    })
                    .filter(Boolean);
                if (segments.length === 0) return null;
                const andCount = segments.filter(seg => !seg.isOr).length;
                // "A, B & C or D": commas and "&" within the AND rows, "or" before an alternative.
                const separator = (i) => segments[i + 1].isOr ? 'or' : (i === andCount - 2 ? '&' : ',');
                return (
                    <HorizontalStack gap="1" blockAlign="center" wrap>
                        <Text variant="bodyMd" color="subdued">This includes</Text>
                        {segments.flatMap((seg, i) => [
                            <CountPopover key={`seg-${i}`} text={seg.text} label={seg.label} items={seg.items} />,
                            i < segments.length - 1 && (
                                <Text key={`sep-${i}`} variant="bodyMd" color="subdued">
                                    {separator(i)}
                                </Text>
                            )
                        ]).filter(Boolean)}
                        <Text variant="bodyMd" color="subdued">.</Text>
                    </HorizontalStack>
                );
            })()}
            <HorizontalStack gap="4" blockAlign="center">
                {!allTypesFilled && <Button onClick={() => dispatch({ type: 'add', obj: { type: nextUnusedType, values: [] } })}>Add condition</Button>}
                {conditions.length > 0 && (
                    <Button plain destructive onClick={() => {
                        conditions.forEach(c => getNegationProps?.(c.type)?.onToggle(false));
                        dispatch({ type: 'clear' });
                    }}>Clear all</Button>
                )}
            </HorizontalStack>
            {showError && isDirty && (
                <InlineError message='Add at least one condition, or switch to "Apply to all".' fieldID="" />
            )}
            {conditions.some(c => c.type === 'USER') && (
                <Banner tone="info">{USER_BETA_BANNER_TEXT}</Banner>
            )}
        </VerticalStack>
    );
};
