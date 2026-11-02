import { Box, Card, HorizontalGrid } from "@shopify/polaris";
import StatCard from "./StatCard";
import { STAT_CARDS } from "./statCards";

function StatsRow({ stats, onSelect }) {
    return (
        <Card padding="0">
            <HorizontalGrid columns={{ xs: 2, sm: 3, lg: 6 }}>
                {STAT_CARDS.map((tile, index) => {
                    const stat = stats?.[tile.id];
                    return (
                        <Box
                            key={tile.id}
                            borderInlineEndWidth={index < STAT_CARDS.length - 1 ? "1" : undefined}
                            borderColor="border-subdued"
                        >
                            <StatCard
                                label={tile.label}
                                value={stat?.value}
                                subtext={stat?.subtext}
                                subtextTone={stat?.tone}
                                onClick={onSelect ? () => onSelect(tile.id) : undefined}
                            />
                        </Box>
                    );
                })}
            </HorizontalGrid>
        </Card>
    );
}

export default StatsRow;
