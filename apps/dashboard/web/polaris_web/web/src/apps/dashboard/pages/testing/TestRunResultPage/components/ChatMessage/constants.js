// Asset paths
export const ASSETS = {
    AKTO_LOGO: '/public/akto.svg',
    FRAME_LOGO: '/public/Frame.svg',
    DIVIDER: '/public/Divider.svg',
    DIVIDER_ALERT: '/public/Divider_alert.svg',
};

// Colors
export const COLORS = {
    REQUEST: '#9C6ADE',
    RESPONSE: '#6D7175',
    TEXT_SUBDUED: '#6D7175',
    TEXT_PRIMARY: '#202223',
};

// Default labels
export const DEFAULT_LABELS = {
    USER: 'Tested interaction',
    AGENT: 'HR agent response',
};

// Message types (keeping 'request'/'response' for backward compatibility)
export const MESSAGE_TYPES = {
    REQUEST: 'request',  // User message
    RESPONSE: 'response', // Agent message
    USER: 'request',      // Alias for clarity
    AGENT: 'response',    // Alias for clarity
};
