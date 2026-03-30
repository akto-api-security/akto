export const threatDetectionConfig = {
    moduleType: 'THREAT_DETECTION',
    title: 'Threat Detection Metrics',
    metrics: ['CPU_USAGE_PERCENT', 'HEAP_MEMORY_USED_MB', 'NON_HEAP_MEMORY_USED_MB'],
    metricNames: {
        'CPU_USAGE_PERCENT': { title: 'CPU Usage', description: 'CPU usage percentage' },
        'HEAP_MEMORY_USED_MB': { title: 'Heap Memory Used', description: 'Heap memory used in MB' },
        'NON_HEAP_MEMORY_USED_MB': { title: 'Non-Heap Memory Used', description: 'Non-heap memory used in MB' }
    },
    fetchStrategy: 'moduleType',
    systemInfoMetrics: ['TOTAL_PHYSICAL_MEMORY_MB', 'HEAP_MEMORY_MAX_MB', 'AVAILABLE_PROCESSORS'],
    enableLegends: false,

    // Extract system info from metrics data (not from moduleInfo.additionalData)
    systemInfoExtractor: (module, systemInfoData) => {
        // systemInfoData is pre-extracted from metrics by the parent component
        if (!systemInfoData) return null
        return systemInfoData
    },

    systemInfoFields: [
        { key: 'availableProcessors', label: 'CPU Cores' },
        { key: 'heapMemoryMaxMb', label: 'Max Heap Memory', unit: 'MB' },
        { key: 'totalPhysicalMemoryMb', label: 'Total Memory', unit: 'MB' }
    ]
}
