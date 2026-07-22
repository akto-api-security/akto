export const accountJobExecutorConfig = {
    moduleType: 'ACCOUNT_JOB_EXECUTOR',
    title: 'Account Job Executor Metrics',
    metrics: ['CPU_USAGE_PERCENT', 'HEAP_MEMORY_USED_MB', 'ACCOUNT_JOBS_RUN_FAILED'],
    metricNames: {
        'CPU_USAGE_PERCENT': { title: 'CPU Usage', description: 'CPU usage percentage' },
        'HEAP_MEMORY_USED_MB': { title: 'Heap Memory Used', description: 'Heap memory used in MB' },
        'ACCOUNT_JOBS_RUN_FAILED': { title: 'Failed Job Runs', description: 'Number of account jobs that failed or were retried due to an error' }
    },
    fetchStrategy: 'moduleType',
    enableLegends: false,
}
