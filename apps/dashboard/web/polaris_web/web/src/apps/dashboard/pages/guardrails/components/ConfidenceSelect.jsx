import { useState } from 'react';
import { Popover, Button, ActionList, Text, VerticalStack } from '@shopify/polaris';

const CONFIDENCE_OPTIONS = [
    { label: 'Trigger on low confidence', value: 'low', helpText: 'Most protective - may flag borderline content.' },
    { label: 'Trigger on moderate confidence', value: 'moderate', helpText: 'Balanced protection. Recommended for most teams.' },
    { label: 'Trigger only if high confidence', value: 'high', helpText: 'Least restrictive - only blocks high-certainty violations.' },
];

export { CONFIDENCE_OPTIONS };

// 0–1 float ↔ option
export const toOpt = (val) => val <= 0.35 ? 'low' : val <= 0.65 ? 'moderate' : 'high';
export const fromOpt = (opt) => opt === 'low' ? 0.3 : opt === 'moderate' ? 0.6 : 0.9;

// level string (none/low/medium/high) ↔ option
export const toLevelOpt = (level) => { const l = (level || '').toLowerCase(); return l === 'high' ? 'high' : l === 'medium' ? 'moderate' : 'low'; };
export const fromLevelOpt = (opt) => opt === 'high' ? 'high' : opt === 'moderate' ? 'medium' : 'low';

// 0–100 int ↔ option
export const toOpt100 = (val) => val <= 35 ? 'low' : val <= 65 ? 'moderate' : 'high';
export const fromOpt100 = (opt) => opt === 'low' ? 25 : opt === 'moderate' ? 50 : 75;

export default function ConfidenceSelect({ label, value, onChange, helpText }) {
    const [open, setOpen] = useState(false);
    const selected = CONFIDENCE_OPTIONS.find(o => o.value === value) || CONFIDENCE_OPTIONS[0];

    return (
        <VerticalStack gap="1">
            {label && <Text variant="bodyMd" fontWeight="medium">{label}</Text>}
            <Popover
                active={open}
                onClose={() => setOpen(false)}
                activator={
                    <Button
                        onClick={() => setOpen(o => !o)}
                        disclosure={open ? 'up' : 'down'}
                        fullWidth
                        textAlign="left"
                    >
                        {selected.label}
                    </Button>
                }
                fullWidth
            >
                <ActionList
                    actionRole="menuitem"
                    items={CONFIDENCE_OPTIONS.map(opt => ({
                        content: opt.label,
                        helpText: opt.helpText,
                        onAction: () => { onChange(opt.value); setOpen(false); },
                        active: opt.value === value,
                    }))}
                />
            </Popover>
            {helpText && <Text variant="bodySm" tone="subdued">{helpText}</Text>}
        </VerticalStack>
    );
}
