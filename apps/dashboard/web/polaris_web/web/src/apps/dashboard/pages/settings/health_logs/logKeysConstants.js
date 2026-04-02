/** Mongo {@code key} values written by {@code LoggerMaker#insert} (error, info, warn, debug). */
export const STORED_LOG_KEY_OPTIONS = [
    { key: 'error', label: 'Error' },
    { key: 'info', label: 'Info' },
    { key: 'warn', label: 'Warn' },
    { key: 'debug', label: 'Debug' },
]

export const ALL_STORED_LOG_KEYS = STORED_LOG_KEY_OPTIONS.map(({ key }) => key)
