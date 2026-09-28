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
}

function KpiGrid({ kpis, onOpenLink, onOpenDrill }) {
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
