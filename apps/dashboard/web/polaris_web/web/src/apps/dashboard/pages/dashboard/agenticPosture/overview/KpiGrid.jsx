import { HorizontalGrid } from '@shopify/polaris'
import { CustomersMajor, IdentityCardMajor, KeyMajor, NoteMajor, RiskMajor, SecureMajor } from '@shopify/polaris-icons'
import { KpiTile } from '../../agenticPostureShared'

// Every KPI is sample/mock data right now (static JSON, no real backend wired up yet — see the
// posture plan) — so every tile renders plainly, with no real/not-real distinction to draw yet.
// A KPI's own "illustrative" shape (used later once some tiles go live and others genuinely
// aren't wired up) is merged in as if it were the real value for now.
const ICONS = {
    agents: CustomersMajor,
    highRiskAgents: RiskMajor,
    identityAccess: IdentityCardMajor,
    privilegedTools: KeyMajor,
    sensitiveData: NoteMajor,
    protectionCoverage: SecureMajor,
}

function KpiGrid({ kpis, onOpenLink }) {
    return (
        <HorizontalGrid columns={3} gap="3">
            {(kpis || []).map((kpi) => {
                const display = kpi.status === 'COMING_SOON' ? { ...kpi, ...kpi.illustrative, status: undefined } : kpi
                return <KpiTile key={kpi.id} kpi={display} icon={ICONS[kpi.id]} onOpenLink={onOpenLink} />
            })}
        </HorizontalGrid>
    )
}

export default KpiGrid
