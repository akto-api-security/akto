import { Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { riskBand } from '../../agenticPostureShared'
import SimpleIndexTable from '../../../../components/tables/SimpleIndexTable'
import func from '@/util/func'
import CustomProgressBar from '../../new_components/CustomProgressBar'

const HEADINGS = [
    { title: 'Category' },
    { title: 'Weight' },
    { title: 'Sub-score' },
    { title: 'Points' },
]

function SubScoreCell({ subScore }) {
    const band = riskBand(subScore)
    return (
        <Box minWidth="160px">
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Box width="90px">
                    <CustomProgressBar progress={subScore} height="8px" borderRadius="var(--p-border-radius-1)"
                        backgroundColor="var(--p-color-bg-strong)"
                        topColor={band ? func.getHexColorForSeverity(band.severity) : undefined} />
                </Box>
                <Text variant="bodySm" color="subdued">{subScore.toFixed(1)} / 100</Text>
            </HorizontalStack>
        </Box>
    )
}

function ScoreBreakdownSection({ rows, totalScore }) {
    if (!rows || rows.length === 0) return null

    const tableRows = [
        ...rows.map((row) => [
            <Text variant="bodyMd" fontWeight="medium">{row.category}</Text>,
            <Text variant="bodyMd" color="subdued">{row.weight}%</Text>,
            <SubScoreCell subScore={row.subScore} />,
            <Text variant="bodyMd" fontWeight="semibold">{row.points.toFixed(1)}</Text>,
        ]),
        [
            <Text variant="bodyMd" fontWeight="bold">Posture score</Text>,
            <Text variant="bodyMd" fontWeight="bold">100%</Text>,
            null,
            <Text variant="bodyMd" fontWeight="bold">{totalScore} / 100</Text>,
        ],
    ]

    return (
        <Card padding="0">
            <Box padding="5" paddingBlockEnd="3">
                <VerticalStack gap="1">
                    <Text variant="bodyMd" fontWeight="semibold">How this score is calculated</Text>
                    <Text variant="bodySm" color="subdued">
                        Each category's sub-score × weight; the points add up to the posture score.
                    </Text>
                </VerticalStack>
            </Box>
            <SimpleIndexTable
                resourceName={{ singular: 'category', plural: 'categories' }}
                headings={HEADINGS}
                rows={tableRows}
                getRowId={(cells, index) => (index < rows.length ? rows[index].category : 'postureScoreTotal')}
            />
        </Card>
    )
}

export default ScoreBreakdownSection
