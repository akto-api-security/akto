import { Card, VerticalStack, Box, HorizontalGrid } from '@shopify/polaris'
import SemiCircleProgress from '../../../components/shared/SemiCircleProgress'
import ComponentHeader from './ComponentHeader'

const AverageIssueAgeCard = ({ issueAgeData = [], itemId = "", onRemoveComponent, tooltipContent = "", onSeverityClick }) => {
    return (
        <Card>
            <VerticalStack gap={4} align='space-between'>
                <ComponentHeader title='Average Issue Age' itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />

                <Box width='100%'>
                    <HorizontalGrid columns={2} gap={4} alignItems='center' blockAlign='center'>
                        {issueAgeData.map((issue, idx) => (
                            <Box key={idx}>
                                <div
                                    style={{
                                        display: 'flex',
                                        justifyContent: 'center',
                                        alignItems: 'center',
                                        cursor: onSeverityClick && issue.severity ? 'pointer' : undefined
                                    }}
                                    onClick={onSeverityClick && issue.severity ? () => onSeverityClick(issue.severity) : undefined}
                                    onKeyDown={onSeverityClick && issue.severity ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.currentTarget.click(); } } : undefined}
                                    role={onSeverityClick && issue.severity ? 'button' : undefined}
                                    tabIndex={onSeverityClick && issue.severity ? 0 : undefined}
                                >
                                    <SemiCircleProgress
                                        progress={issue.progress}
                                        size={140}
                                        height={110}
                                        width={180}
                                        color={issue.color}
                                        backgroundColor='#F2F4F7'
                                        centerText={`${issue.days} days`}
                                        subtitle={issue.label}
                                    />
                                </div>
                            </Box>
                        ))}
                    </HorizontalGrid>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default AverageIssueAgeCard
