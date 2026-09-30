import { HorizontalGrid } from '@shopify/polaris'
import { CustomersMajor, IdentityCardMajor, KeyMajor, NoteMajor, RiskMajor, SecureMajor } from '@shopify/polaris-icons'
import { ComingSoonOverlay, KpiTile } from '../../agenticPostureShared'

const ICONS = {
    assets: CustomersMajor,
    highRiskAgents: RiskMajor,
    identityAccess: IdentityCardMajor,
    privilegedTools: KeyMajor,
    sensitiveData: NoteMajor,
    protectionCoverage: SecureMajor,
}

// Illustrative values shown blurred until the identity resolver exists.
const COMING_SOON_KPIS = {
    identityAccess: { value: 7, secondaryFootnote: '2 shared · 3 orphaned', secondaryTone: 'warning' },
}

const KPI_DRILL_ID = {
    protectionCoverage: 'protectionCoverage',
    highRiskAgents: 'highRiskAgents',
    privilegedTools: 'privilegedTools',
    sensitiveData: 'sensitiveData',
}

// Assets has no drill of its own — the inventory page already lists every asset, so the tile
// navigates there rather than duplicating it in a flyout.
const KPI_ROUTE = {
    assets: '/dashboard/observe/inventory',
}

function KpiGrid({ kpis, onOpenLink, onOpenDrill, onOpenRoute }) {
    return (
        <HorizontalGrid columns={3} gap="3">
            {(kpis || []).map((kpi) => {
                const sample = COMING_SOON_KPIS[kpi.id]
                if (sample) {
                    return (
                        <ComingSoonOverlay key={kpi.id} panelId={kpi.id}>
                            <KpiTile kpi={{ ...kpi, ...sample }} icon={ICONS[kpi.id]} />
                        </ComingSoonOverlay>
                    )
                }
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
