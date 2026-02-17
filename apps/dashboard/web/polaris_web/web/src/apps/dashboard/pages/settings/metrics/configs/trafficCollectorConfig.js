export const trafficCollectorConfig = {
    moduleType: 'TRAFFIC_COLLECTOR',
    title: 'Traffic Collectors Metrics',
    metrics: ['TC_CPU_USAGE', 'TC_MEMORY_USAGE'],
    metricNames: {
        'TC_CPU_USAGE': { title: 'CPU Usage', description: 'Traffic Collector CPU usage percentage' },
        'TC_MEMORY_USAGE': { title: 'Memory Usage', description: 'Traffic Collector memory usage in MB' }
    },
    fetchStrategy: 'prefix',
    metricPrefix: 'TC_',
    enableLegends: true,

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
