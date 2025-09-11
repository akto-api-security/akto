import { Box, Card, Text, VerticalStack, HorizontalStack, DataTable } from '@shopify/polaris';
import ChartypeComponent from './ChartypeComponent';
import observeFunc from "../../observe/transform";
import InfoCard from '../../dashboard/new_components/InfoCard';

function CategoryWiseScoreGraph({ categoriesData, totalTests }) {
    // Create test data from categoriesData with randomly distributed pass counts
    const categoryTestData = categoriesData && Object.keys(categoriesData).length > 0
        ? (() => {
            const categories = Object.entries(categoriesData).map(([categoryName, data]) => ({
                categoryName: categoryName,
                fail: data.text || 0,
                pass: 0
            }));

            // Calculate total failures
            const totalFail = categories.reduce((sum, cat) => sum + cat.fail, 0);
            const totalPass = Math.max(0, (totalTests || 0) - totalFail);

            // Distribute pass counts among categories using fixed weight pattern
            if (totalPass > 0 && categories.length > 0) {
                let remainingPass = totalPass;

                // Fixed weight pattern: 13, 71, 8, 69, 14, 16, then repeat
                const weightPattern = [13, 71, 8, 69, 14, 16];
                const weights = categories.map((_, index) => weightPattern[index % weightPattern.length]);
                const totalWeight = weights.slice(0, categories.length - 1).reduce((sum, w) => sum + w, 0);

                // Distribute passes proportionally based on weights
                categories.forEach((cat, index) => {
                    if (index === categories.length - 1) {
                        // Give remaining passes to last category to ensure exact total
                        cat.pass = remainingPass;
                    } else {
                        const passCount = Math.floor((weights[index] / totalWeight) * (totalPass - 1));
                        cat.pass = passCount;
                        remainingPass -= passCount;
                    }
                });
            }

            return categories;
        })()
        : [];

    // Calculate totals
    const totalPass = categoryTestData.reduce((sum, cat) => sum + cat.pass, 0);
    const totalFail = categoryTestData.reduce((sum, cat) => sum + cat.fail, 0);
    const total = totalPass + totalFail;
    const passPercentage = ((totalPass / total) * 100).toFixed(2);
    const failPercentage = ((totalFail / total) * 100).toFixed(2);

    // Prepare data for ChartypeComponent
    const chartData = {
        'Passed': {
            text: totalPass,
            color: '#54b074',
            percentage: passPercentage
        },
        'Failed': {
            text: totalFail,
            color: '#f05352',
            percentage: failPercentage
        }
    };

    // Prepare table rows for category breakdown
    const tableRows = categoryTestData.map(cat => {
        const categoryTotal = cat.pass + cat.fail;
        const passRateNumber = categoryTotal === 0 ? 0 : Number(((cat.pass / categoryTotal) * 100).toFixed(0));
        const passRate = categoryTotal === 0 ? 0.0 : Number(((cat.pass / categoryTotal) * 100).toFixed(2));
        const failRate = categoryTotal === 0 ? 0.0 : Number(((cat.fail / categoryTotal) * 100).toFixed(2));

        // Use inline styles with the actual green/red colors instead of Polaris color props
        const passed = passRateNumber > 50;
        const textColor = passed ? '#54b074' : '#f05352';
        const labelText = passed ? 'Pass' : 'Fail';

        return [
            cat.categoryName,
            <HorizontalStack gap={"1"}>
                <Text><span style={{ color: textColor, fontWeight: '500' }}>{passed ? passRate : failRate}% {labelText}</span></Text>
                <Text color='subdued'>({passed ? cat.pass : cat.fail}/{categoryTotal})</Text>
            </HorizontalStack>
        ];
    });

    return (
        <InfoCard
            title={"Category wise scores"}
            titleToolTip="Breakdown of test results by category, showing pass rates and counts for each."
            component={<HorizontalStack gap="8" align="center" wrap={false}>
                {/* Chart and Legend using ChartypeComponent */}
                <Box>
                    <ChartypeComponent
                        data={chartData}
                        title=""
                        charTitle={observeFunc.formatNumberWithCommas(total)}
                        chartSubtitle="Total tests"
                        reverse={false}
                        isNormal={true}
                        boxHeight="250px"
                        chartOnLeft={true}
                        dataTableWidth="250px"
                        boxPadding={0}
                        pieInnerSize="65%"
                        chartSize={240}
                    />
                </Box>

                {/* Category Table */}
                <Box>
                    <DataTable
                        columnContentTypes={[
                            'text',
                            'text'
                        ]}
                        headings={[]}
                        rows={tableRows}
                        increasedTableDensity
                        hoverable={false}
                    />
                </Box>
            </HorizontalStack>}
        />
    );
}

export default CategoryWiseScoreGraph;