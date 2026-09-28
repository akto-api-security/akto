import { HorizontalGrid } from '@shopify/polaris'
import { CustomersMajor, IdentityCardMajor, KeyMajor, NoteMajor, RiskMajor, SecureMajor } from '@shopify/polaris-icons'
import { KpiTile } from '../../agenticPostureShared'

const ICONS = {
    assets: CustomersMajor,
    highRiskAgents: RiskMajor,
    identityAccess: IdentityCardMajor,
    privilegedTools: KeyMajor,
    sensitiveData: NoteMajor,
    protectionCoverage: SecureMajor,
}

const KPI_DRILL_ID = {
    protectionCoverage: 'protectionCoverage',
    highRiskAgents: 'highRiskAgents',
}

function KpiGrid({ kpis, onOpenLink, onOpenDrill }) {
    return (
        <HorizontalGrid columns={3} gap="3">
            {(kpis || []).map((kpi) => {
                const drillId = KPI_DRILL_ID[kpi.id]
                return (
                    <KpiTile
                        key={kpi.id}
                        kpi={kpi}
                        icon={ICONS[kpi.id]}
                        onOpenLink={onOpenLink}
                        onOpen={drillId ? () => onOpenDrill(drillId) : undefined}
                        forceClickable={!!drillId}
                    />
                )
            })}
        </HorizontalGrid>
    )
}

export default KpiGrid
