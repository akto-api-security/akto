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

function KpiGrid({ kpis, onOpenLink }) {
    return (
        <HorizontalGrid columns={3} gap="3">
            {(kpis || []).map((kpi) => (
                <KpiTile key={kpi.id} kpi={kpi} icon={ICONS[kpi.id]} onOpenLink={onOpenLink} />
            ))}
        </HorizontalGrid>
    )
}

export default KpiGrid
