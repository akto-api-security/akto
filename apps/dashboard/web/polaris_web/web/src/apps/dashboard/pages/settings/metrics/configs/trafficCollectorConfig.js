export const trafficCollectorConfig = {
    moduleType: 'TRAFFIC_COLLECTOR',
    title: 'Traffic Collectors Metrics',
    /** When true, module dropdown includes a "Live modules only" filter (default on). */
    showLiveModulesFilter: true,
    metrics: [
        // Traffic read off eBPF vs traffic genuinely lost. Deliberately filtered
        // traffic (ignore-ports, IP hosts, envoy) is configuration, not loss, and
        // is excluded from the discard figure.
        'TC_EVENTS_RECEIVED',
        'TC_EVENTS_DISCARDED',
        // Collector process health.
        'TC_CPU_USAGE',
        'TC_MEMORY_USAGE',
        'TC_HOST_MEMORY_USED_MB',
        'TC_GOROUTINES',
        'TC_SYSTEM_CPU_PERCENT'
    ],
    metricNames: {
        'TC_EVENTS_RECEIVED': { title: 'Traffic Read (events)', description: 'eBPF socket data events that passed the port filter and entered the pipeline' },
        'TC_EVENTS_DISCARDED': { title: 'Traffic Discarded (events)', description: 'Events the collector lost and could not process. Excludes deliberately filtered traffic, which is configuration rather than loss' },
        'TC_CPU_USAGE': { title: 'CPU Usage', description: 'CPU usage percentage of traffic collector modules' },
        'TC_MEMORY_USAGE': { title: 'Memory Used', description: 'Memory used in MB by traffic collector modules' },
        'TC_HOST_MEMORY_USED_MB': { title: 'Host Memory Used', description: 'Host used memory (MB)' },
        'TC_GOROUTINES': { title: 'Goroutines', description: 'Number of goroutines' },
        'TC_SYSTEM_CPU_PERCENT': { title: 'System CPU Percent', description: 'Host-level CPU usage percentage' }
    },
    fetchStrategy: 'prefix',
    metricPrefix: 'TC_',
    enableLegends: false,

    // Extract system info from moduleInfo.additionalData.profiling
    systemInfoExtractor: (module) => {
        const profiling = module.additionalData?.profiling
        if (!profiling) return null
        return {
            totalCpuCores: profiling.cpu_cores_total || null,
            totalMemoryMB: profiling.memory_cumulative_mb ? parseFloat(profiling.memory_cumulative_mb).toFixed(2) : null
        }
    },

    systemInfoFields: [
        { key: 'totalCpuCores', label: 'CPU Cores' },
        { key: 'totalMemoryMB', label: 'Memory', unit: 'MB' }
    ]
}
