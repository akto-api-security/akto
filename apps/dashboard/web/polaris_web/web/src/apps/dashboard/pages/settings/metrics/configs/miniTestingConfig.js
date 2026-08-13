export const miniTestingConfig = {
    moduleType: 'MINI_TESTING',
    title: 'Mini Testing Metrics',
    metrics: [
        'TESTING_TESTS_EXECUTED_COUNT',
        'TESTING_API_HIT_SUCCESS_COUNT',
        'TESTING_API_HIT_FAILURE_COUNT',
        'TESTING_ISSUES_CREATED_COUNT',
        'TESTING_KAFKA_QUEUE_PENDING',
        'TESTING_KAFKA_SEND_FAILURE_COUNT',
        'TESTING_RATE_LIMIT_EXCEEDED_COUNT',
        'TESTING_RUN_COUNT',
        'TESTING_RUN_LATENCY',
        'SAMPLE_DATA_FETCH_LATENCY',
        'MULTIPLE_SAMPLE_DATA_FETCH_LATENCY',
        'CYBORG_CALL_LATENCY',
        'CYBORG_CALL_COUNT',
        'CYBORG_DATA_SIZE',
        'CPU_USAGE_PERCENT',
        'HEAP_MEMORY_USED_MB',
        'NON_HEAP_MEMORY_USED_MB',
    ],
    metricNames: {
        'TESTING_RUN_COUNT': { title: 'Testing Run Count', description: 'Number of testing runs executed' },
        'TESTING_RUN_LATENCY': { title: 'Testing Run Latency', description: 'Average latency of testing runs' },
        'SAMPLE_DATA_FETCH_LATENCY': { title: 'Sample Data Fetch Latency', description: 'Average latency for fetching sample data' },
        'MULTIPLE_SAMPLE_DATA_FETCH_LATENCY': { title: 'Multiple Sample Data Fetch Latency', description: 'Average latency for fetching multiple sample data' },
        'TESTING_TESTS_EXECUTED_COUNT': { title: 'Tests Executed', description: 'Number of individual tests executed' },
        'TESTING_API_HIT_SUCCESS_COUNT': { title: 'Successful API Hits', description: 'Number of test API calls that completed successfully' },
        'TESTING_API_HIT_FAILURE_COUNT': { title: 'Unsuccessful API Hits', description: 'Number of test API calls that failed at the network/execution level' },
        'TESTING_ISSUES_CREATED_COUNT': { title: 'Issues Created', description: 'Number of issues created during testing, across all severities' },
        'TESTING_KAFKA_QUEUE_PENDING': { title: 'Testing Kafka Queue Pending', description: 'Number of test messages remaining in the Kafka consumer queue' },
        'TESTING_KAFKA_SEND_FAILURE_COUNT': { title: 'Testing Kafka Send Failures', description: 'Number of failures encountered while sending test messages to Kafka' },
        'TESTING_RATE_LIMIT_EXCEEDED_COUNT': { title: 'Testing Rate Limit Exceeded', description: 'Number of times the daily testing rate limit was exceeded' },
        'CYBORG_CALL_LATENCY': { title: 'Cyborg Call Latency', description: 'Average latency of Cyborg calls' },
        'CYBORG_CALL_COUNT': { title: 'Cyborg Call Count', description: 'Number of Cyborg calls made' },
        'CYBORG_DATA_SIZE': { title: 'Cyborg Data Size', description: 'Size of data exchanged with Cyborg' },
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
