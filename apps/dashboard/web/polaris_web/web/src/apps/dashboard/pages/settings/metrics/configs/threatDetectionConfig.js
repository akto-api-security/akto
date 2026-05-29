export const threatDetectionConfig = {
    moduleType: 'THREAT_DETECTION',
    title: 'Threat Detection Metrics',
    metrics: [
        'KAFKA_RECORDS_LAG_MAX',
        'KAFKA_RECORDS_CONSUMED_RATE',
        'KAFKA_FETCH_AVG_LATENCY',
        'KAFKA_BYTES_CONSUMED_RATE',
        'TD_KAFKA_RECORD_COUNT',
        'TD_KAFKA_RECORD_SIZE',
        'TD_KAFKA_LATENCY',
        'CPU_USAGE_PERCENT',
        'HEAP_MEMORY_USED_MB',
        'NON_HEAP_MEMORY_USED_MB',
    ],
    metricNames: {
        'KAFKA_RECORDS_LAG_MAX': { title: 'Kafka Records Lag', description: 'Max consumer lag across partitions' },
        'KAFKA_RECORDS_CONSUMED_RATE': { title: 'Kafka Consumed Rate', description: 'Records consumed per second' },
        'KAFKA_FETCH_AVG_LATENCY': { title: 'Kafka Fetch Latency', description: 'Average fetch latency in ms' },
        'KAFKA_BYTES_CONSUMED_RATE': { title: 'Kafka Bytes Rate', description: 'Bytes consumed per second' },
        'TD_KAFKA_RECORD_COUNT': { title: 'Records Processed', description: 'Total Kafka records processed' },
        'TD_KAFKA_RECORD_SIZE': { title: 'Records Size', description: 'Total bytes of Kafka records processed' },
        'TD_KAFKA_LATENCY': { title: 'Processing Latency', description: 'Average per-record processing latency in ms' },
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
