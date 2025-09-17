import { Box, Text, HorizontalStack, DataTable } from '@shopify/polaris';
import ChartypeComponent from './ChartypeComponent';
import observeFunc from "../../observe/transform";
import InfoCard from '../../dashboard/new_components/InfoCard';
import { isMCPSecurityCategory, isGenAISecurityCategory } from '../../../../main/labelHelper';
import { mcpCategoryTestData, genAICategoryTestData, apiCategoryTestData } from './dummyData';

function CategoryWiseScoreGraph({  }) {

    // Select appropriate data based on dashboard category
    let categoryTestData;
    if (isMCPSecurityCategory()) {
        categoryTestData = mcpCategoryTestData;
    } else if (isGenAISecurityCategory()) {
        categoryTestData = genAICategoryTestData;
    }

    // Calculate totals
    const totalPass = categoryTestData.reduce((sum, cat) => sum + cat.pass, 0);
    const totalFail = categoryTestData.reduce((sum, cat) => sum + cat.fail, 0);
    const total = totalPass + totalFail;
    // Prepare data for ChartypeComponent
    const chartData = {
        'Passed': {
            text: totalPass,
            color: '#54b074',
        },
        'Failed': {
            text: totalFail,
            color: '#f05352'
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
                <Text color='subdued'>({passed ? cat.pass : cat.fail}/{categoryTotal+cat.skip})</Text>
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