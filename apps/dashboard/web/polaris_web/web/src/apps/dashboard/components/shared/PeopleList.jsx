import { Avatar, HorizontalStack, Text, Tooltip, VerticalStack } from "@shopify/polaris";

export const initialsOf = (name = "") => name.split(/[\s._@-]+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("");

/*
 * People rows: initials avatar, name, a secondary line and right-aligned text (with an optional tooltip).
 *
 * <PeopleList people={[{ name: "rahul.verma", secondary: "3 devices", trailing: "10 minutes ago", trailingTooltip: "11/5/2026, 1:20:00 PM" }]} emptyText="No users" />
 */
function PeopleList({ people = [], emptyText }) {
    if (!people.length) return <Text variant="bodySm" color="subdued">{emptyText}</Text>;
    return (
        <VerticalStack gap="3">
            {people.map((p) => (
                <HorizontalStack key={p.name} align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                        <Avatar size="small" initials={initialsOf(p.name)} name={p.name} />
                        <VerticalStack gap="0">
                            <Text variant="bodyMd">{p.name}</Text>
                            {p.secondary && <Text variant="bodySm" color="subdued">{p.secondary}</Text>}
                        </VerticalStack>
                    </HorizontalStack>
                    {p.trailing && (p.trailingTooltip
                        ? <Tooltip content={p.trailingTooltip}><Text variant="bodySm" color="subdued">{p.trailing}</Text></Tooltip>
                        : <Text variant="bodySm" color="subdued">{p.trailing}</Text>)}
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

export default PeopleList;
