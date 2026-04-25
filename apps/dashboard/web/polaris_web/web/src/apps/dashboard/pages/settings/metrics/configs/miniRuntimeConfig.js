export const miniRuntimeConfig = {
    moduleType: 'MINI_RUNTIME',
    title: 'Mini Runtime Metrics',
    metrics: [
        'CPU_USAGE_PERCENT',
        'HEAP_MEMORY_USED_MB',
        'NON_HEAP_MEMORY_USED_MB',
        'RT_KAFKA_RECORD_COUNT',
        'RT_KAFKA_RECORD_SIZE',
        'RT_KAFKA_LATENCY',
        'RT_API_RECEIVED_COUNT',
        'KAFKA_RECORDS_LAG_MAX',
        'KAFKA_RECORDS_CONSUMED_RATE',
        'KAFKA_FETCH_AVG_LATENCY',
        'KAFKA_BYTES_CONSUMED_RATE'
    ],
    metricNames: {
        'CPU_USAGE_PERCENT': { title: 'CPU Usage', description: 'CPU usage percentage' },
        'HEAP_MEMORY_USED_MB': { title: 'Heap Memory Used', description: 'Heap memory used in MB' },
        'NON_HEAP_MEMORY_USED_MB': { title: 'Non-Heap Memory Used', description: 'Non-heap memory used in MB' },
        'RT_KAFKA_RECORD_COUNT': { title: 'Kafka Record Count', description: 'Mini Runtime Kafka records count' },
        'RT_KAFKA_RECORD_SIZE': { title: 'Kafka Record Size', description: 'Mini Runtime Kafka record size' },
        'RT_KAFKA_LATENCY': { title: 'Kafka Latency', description: 'Mini Runtime Kafka latency' },
        'RT_API_RECEIVED_COUNT': { title: 'API Received Count', description: 'Mini Runtime APIs received' },
        'KAFKA_RECORDS_LAG_MAX': { title: 'Kafka Records Lag Max', description: 'Maximum consumer lag for Mini Runtime Kafka' },
        'KAFKA_RECORDS_CONSUMED_RATE': { title: 'Kafka Records Consumed Rate', description: 'Kafka records consumed rate' },
        'KAFKA_FETCH_AVG_LATENCY': { title: 'Kafka Fetch Avg Latency', description: 'Average Kafka fetch latency' },
        'KAFKA_BYTES_CONSUMED_RATE': { title: 'Kafka Bytes Consumed Rate', description: 'Kafka bytes consumed rate' }
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
