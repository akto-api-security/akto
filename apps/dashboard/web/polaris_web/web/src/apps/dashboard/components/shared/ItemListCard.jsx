import { Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import ShowListInBadge from "./ShowListInBadge";

/*
 * Bordered list of rows (title, optional description, optional right-side tag), e.g. the tools an
 * MCP server exposes. Shows up to `limit` rows; the rest open from a "View all" link when onViewAll is
 * given, otherwise from a "+N" badge whose tooltip lists their titles.
 *
 * <ItemListCard
 *     items={[{ id: "bash", title: "Bash", description: "Runs shell commands", tag: <Badge>Local</Badge> }]}
 *     emptyText="No tools discovered yet"
 *     limit={6}
 *     onViewAll={openComponents}
 * />
 */
function ItemListCard({ items = [], emptyText, limit, onViewAll }) {
    if (!items.length) {
        return <Text variant="bodySm" color="subdued">{emptyText}</Text>;
    }
    const shown = limit ? items.slice(0, limit) : items;
    const hidden = items.length - shown.length;
    return (
        <Box borderWidth="1" borderColor="border-subdued" borderRadius="2">
            {shown.map((item, i) => (
                <Box key={item.id ?? item.title} padding="3" borderBlockStartWidth={i > 0 ? "1" : undefined} borderColor="border-subdued">
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="3">
                        <VerticalStack gap="05">
                            <Text variant="bodyMd">{item.title}</Text>
                            {item.description && <Text variant="bodySm" color="subdued">{item.description}</Text>}
                        </VerticalStack>
                        {item.tag}
                    </HorizontalStack>
                </Box>
            ))}
            {!onViewAll && hidden > 0 && (
                <Box padding="3" borderBlockStartWidth="1" borderColor="border-subdued">
                    <ShowListInBadge itemsArr={items.slice(shown.length).map((item) => item.title)} maxItems={0} useTooltip />
                </Box>
            )}
            {onViewAll && (
                <Box padding="3" borderBlockStartWidth="1" borderColor="border-subdued">
                    <Button plain onClick={onViewAll}>{hidden > 0 ? `View all ${items.length}` : "View details"}</Button>
                </Box>
            )}
        </Box>
    );
}

export default ItemListCard;
