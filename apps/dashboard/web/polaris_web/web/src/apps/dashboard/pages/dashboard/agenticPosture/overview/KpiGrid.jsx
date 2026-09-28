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
    privilegedTools: 'privilegedTools',
    sensitiveData: 'sensitiveData',
}

// Assets has no drill of its own — Agentic AI Discovery already is the asset inventory,
// so the tile navigates there rather than duplicating it in a flyout.
const KPI_ROUTE = {
    assets: '/dashboard/observe/agentic-assets',
}

function KpiGrid({ kpis, onOpenLink, onOpenDrill, onOpenRoute }) {
    return (
        <HorizontalGrid columns={3} gap="3">
            {(kpis || []).map((kpi) => {
                const drillId = KPI_DRILL_ID[kpi.id]
                const route = KPI_ROUTE[kpi.id]
                const onOpen = drillId ? () => onOpenDrill(drillId)
                    : route ? () => onOpenRoute(route)
                    : undefined
                return (
                    <KpiTile
                        key={kpi.id}
                        kpi={kpi}
                        icon={ICONS[kpi.id]}
                        onOpenLink={onOpenLink}
                        onOpen={onOpen}
                        forceClickable={!!onOpen}
                    />
                )
            })}
        </HorizontalGrid>
    )
}

export default KpiGrid
