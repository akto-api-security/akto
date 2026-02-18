import ModuleMetrics from './ModuleMetrics'
import { threatDetectionConfig } from './configs/threatDetectionConfig'

function ThreatDetectionMetrics() {
    return <ModuleMetrics config={threatDetectionConfig} />
}

export default ThreatDetectionMetrics
