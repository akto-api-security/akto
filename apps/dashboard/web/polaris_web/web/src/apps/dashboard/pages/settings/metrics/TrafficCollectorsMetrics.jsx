import ModuleMetrics from './ModuleMetrics'
import { trafficCollectorConfig } from './configs/trafficCollectorConfig'

function TrafficCollectorsMetrics() {
    return <ModuleMetrics config={trafficCollectorConfig} />
}

export default TrafficCollectorsMetrics
