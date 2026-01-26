export const summaryInfoData = [
    {
        title: "Total Endpoint Components",
        data: "120",
        variant: 'heading2xl',
    },
    {
        title: 'Total Successful exploits',
        data: "150",
        variant: 'heading2xl',
        color: "warning",
    },
    {
        title: 'Total Sensitive Data Events',
        data: "15",
        variant: 'heading2xl',
    },
    {
        title: 'AI Average Guardrail Score',
        data: "4.5",
        variant: 'heading2xl',
        color: "critical",
    }
]

export const commonMcpServers = [
    {
        name: "Jira MCP Server",
        value: 10,
        icon: "/public/logo_jira.svg",
    },
    {
        name: "Playwright MCP Server",
        value: 5,
        icon: "/public/playwright_logo.svg",
    },
    {
        name: "Github MCP Server",
        value: 3,
        icon: "/public/github.svg",
    },
    {
        name: "Slack MCP Server",
        value: 2,
        icon: "/public/slack_logo.svg",
    },
]

export const commonLlmsInBrowsers = [
    {
        name: "ChatGPT",
        value: 11,
        icon: "/public/openai.svg",
    },
    {
        name: "Claude",
        value: 6,
        icon: "/public/claude-code.svg",
    },
    {
        name: "Gemini",
        value: 4,
        icon: "/public/gemini.svg",
    },
    {
        name: "Grok",
        value: 3,
        icon: "/public/grok.svg",
    },
]

export const commonAiAgents = [
    {
        name: "Cursor",
        value: 10,
        icon: "/public/cursor.svg",
    },
    {
        name: "Claude",
        value: 6,
        icon: "/public/claude-code.svg",
    },
    {
        name: "Windsurf",
        value: 4,
        icon: "/public/windsurf.svg",
    }
]

export const complianceData = [
    {
        name: "OWASP Top 10",
        percentage: 78,
        color: "#dc2626",
        icon: "/public/owasp.svg"
    },
    {
        name: "PCI DSS",
        percentage: 65,
        color: "#ea580c",
        icon: "/public/pci.svg"
    },
    {
        name: "GDPR",
        percentage: 82,
        color: "#ca8a04",
        icon: "/public/gdpr.svg"
    },
    {
        name: "HIPAA",
        percentage: 71,
        color: "#16a34a",
        icon: "/public/hipaa.svg"
    }
]

export const threatTimelineData = {
    threatActivityTimelines: [
        {
            ts: Math.floor(Date.now() / 1000) - 6 * 86400, // 6 days ago
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 45 },
                { subcategory: "data_leakage", activityCount: 32 },
                { subcategory: "model_manipulation", activityCount: 18 },
                { subcategory: "unauthorized_access", activityCount: 25 },
                { subcategory: "api_abuse", activityCount: 12 },
                { subcategory: "training_data_poisoning", activityCount: 8 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000) - 5 * 86400,
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 52 },
                { subcategory: "data_leakage", activityCount: 38 },
                { subcategory: "model_manipulation", activityCount: 22 },
                { subcategory: "unauthorized_access", activityCount: 30 },
                { subcategory: "api_abuse", activityCount: 15 },
                { subcategory: "training_data_poisoning", activityCount: 10 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000) - 4 * 86400,
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 48 },
                { subcategory: "data_leakage", activityCount: 41 },
                { subcategory: "model_manipulation", activityCount: 19 },
                { subcategory: "unauthorized_access", activityCount: 28 },
                { subcategory: "api_abuse", activityCount: 17 },
                { subcategory: "training_data_poisoning", activityCount: 11 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000) - 3 * 86400,
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 60 },
                { subcategory: "data_leakage", activityCount: 35 },
                { subcategory: "model_manipulation", activityCount: 25 },
                { subcategory: "unauthorized_access", activityCount: 32 },
                { subcategory: "api_abuse", activityCount: 20 },
                { subcategory: "training_data_poisoning", activityCount: 13 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000) - 2 * 86400,
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 55 },
                { subcategory: "data_leakage", activityCount: 44 },
                { subcategory: "model_manipulation", activityCount: 21 },
                { subcategory: "unauthorized_access", activityCount: 35 },
                { subcategory: "api_abuse", activityCount: 18 },
                { subcategory: "training_data_poisoning", activityCount: 9 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000) - 1 * 86400,
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 58 },
                { subcategory: "data_leakage", activityCount: 40 },
                { subcategory: "model_manipulation", activityCount: 23 },
                { subcategory: "unauthorized_access", activityCount: 29 },
                { subcategory: "api_abuse", activityCount: 16 },
                { subcategory: "training_data_poisoning", activityCount: 12 }
            ]
        },
        {
            ts: Math.floor(Date.now() / 1000), // today
            subCategoryWiseData: [
                { subcategory: "prompt_injection", activityCount: 62 },
                { subcategory: "data_leakage", activityCount: 37 },
                { subcategory: "model_manipulation", activityCount: 27 },
                { subcategory: "unauthorized_access", activityCount: 33 },
                { subcategory: "api_abuse", activityCount: 19 },
                { subcategory: "training_data_poisoning", activityCount: 14 }
            ]
        }
    ]
}

// Data Protection Trends - Time series data for line chart
export const dataProtectionTrendsData = [
    {
        name: 'Code Blocks',
        color: '#3b82f6', // blue
        data: [
            [Math.floor(Date.now() / 1000) - 13 * 86400, 45],
            [Math.floor(Date.now() / 1000) - 12 * 86400, 52],
            [Math.floor(Date.now() / 1000) - 11 * 86400, 48],
            [Math.floor(Date.now() / 1000) - 10 * 86400, 65],
            [Math.floor(Date.now() / 1000) - 9 * 86400, 58],
            [Math.floor(Date.now() / 1000) - 8 * 86400, 72],
            [Math.floor(Date.now() / 1000) - 7 * 86400, 68],
            [Math.floor(Date.now() / 1000) - 6 * 86400, 75],
            [Math.floor(Date.now() / 1000) - 5 * 86400, 82],
            [Math.floor(Date.now() / 1000) - 4 * 86400, 78],
            [Math.floor(Date.now() / 1000) - 3 * 86400, 85],
            [Math.floor(Date.now() / 1000) - 2 * 86400, 92],
            [Math.floor(Date.now() / 1000) - 1 * 86400, 88],
            [Math.floor(Date.now() / 1000), 95]
        ].map(([ts, val]) => [ts * 1000, val]) // Convert to milliseconds
    },
    {
        name: 'PII Redacts',
        color: '#ef4444', // red
        data: [
            [Math.floor(Date.now() / 1000) - 13 * 86400, 38],
            [Math.floor(Date.now() / 1000) - 12 * 86400, 42],
            [Math.floor(Date.now() / 1000) - 11 * 86400, 35],
            [Math.floor(Date.now() / 1000) - 10 * 86400, 48],
            [Math.floor(Date.now() / 1000) - 9 * 86400, 52],
            [Math.floor(Date.now() / 1000) - 8 * 86400, 45],
            [Math.floor(Date.now() / 1000) - 7 * 86400, 55],
            [Math.floor(Date.now() / 1000) - 6 * 86400, 48],
            [Math.floor(Date.now() / 1000) - 5 * 86400, 58],
            [Math.floor(Date.now() / 1000) - 4 * 86400, 52],
            [Math.floor(Date.now() / 1000) - 3 * 86400, 62],
            [Math.floor(Date.now() / 1000) - 2 * 86400, 55],
            [Math.floor(Date.now() / 1000) - 1 * 86400, 65],
            [Math.floor(Date.now() / 1000), 60]
        ].map(([ts, val]) => [ts * 1000, val])
    },
    {
        name: 'Creds Masked',
        color: '#10b981', // green
        data: [
            [Math.floor(Date.now() / 1000) - 13 * 86400, 28],
            [Math.floor(Date.now() / 1000) - 12 * 86400, 32],
            [Math.floor(Date.now() / 1000) - 11 * 86400, 35],
            [Math.floor(Date.now() / 1000) - 10 * 86400, 42],
            [Math.floor(Date.now() / 1000) - 9 * 86400, 38],
            [Math.floor(Date.now() / 1000) - 8 * 86400, 45],
            [Math.floor(Date.now() / 1000) - 7 * 86400, 52],
            [Math.floor(Date.now() / 1000) - 6 * 86400, 48],
            [Math.floor(Date.now() / 1000) - 5 * 86400, 55],
            [Math.floor(Date.now() / 1000) - 4 * 86400, 62],
            [Math.floor(Date.now() / 1000) - 3 * 86400, 58],
            [Math.floor(Date.now() / 1000) - 2 * 86400, 68],
            [Math.floor(Date.now() / 1000) - 1 * 86400, 72],
            [Math.floor(Date.now() / 1000), 75]
        ].map(([ts, val]) => [ts * 1000, val])
    }
]

export const dataProtectionTrendsLabels = [
    { label: 'Code Blocks', color: '#3b82f6' },
    { label: 'PII Redacts', color: '#ef4444' },
    { label: 'Creds Masked', color: '#10b981' }
]

// Top Triggered Guardrail Policies - Pie chart data
export const guardrailPoliciesData = {
    'PII Redaction Policy': {
        text: 152,
        color: '#ef4444' // red
    },
    'Prompt Injection Block': {
        text: 98,
        color: '#f59e0b' // amber
    },
    'Code Upload Block': {
        text: 67,
        color: '#3b82f6' // blue
    },
    'MCP Denylist': {
        text: 45,
        color: '#10b981' // green
    }
}

export const attackRequests = [
    {
        id: 1,
        source: {
            name: "New York, USA",
            lat: 40.7128,
            lon: -74.0060,
            country: "US"
        },
        destination: {
            name: "London, UK",
            lat: 51.5074,
            lon: -0.1278,
            country: "GB"
        },
        attackType: "SQL Injection",
        timestamp: "2024-01-15T10:30:00Z"
    },
    {
        id: 2,
        source: {
            name: "Tokyo, Japan",
            lat: 35.6762,
            lon: 139.6503,
            country: "JP"
        },
        destination: {
            name: "San Francisco, USA",
            lat: 37.7749,
            lon: -122.4194,
            country: "US"
        },
        attackType: "XSS Attack",
        timestamp: "2024-01-15T11:15:00Z"
    },
    {
        id: 3,
        source: {
            name: "Moscow, Russia",
            lat: 55.7558,
            lon: 37.6173,
            country: "RU"
        },
        destination: {
            name: "Berlin, Germany",
            lat: 52.5200,
            lon: 13.4050,
            country: "DE"
        },
        attackType: "DDoS Attack",
        timestamp: "2024-01-15T12:00:00Z"
    },
    {
        id: 4,
        source: {
            name: "Beijing, China",
            lat: 39.9042,
            lon: 116.4074,
            country: "CN"
        },
        destination: {
            name: "Sydney, Australia",
            lat: -33.8688,
            lon: 151.2093,
            country: "AU"
        },
        attackType: "Brute Force",
        timestamp: "2024-01-15T13:20:00Z"
    },
    {
        id: 5,
        source: {
            name: "São Paulo, Brazil",
            lat: -23.5505,
            lon: -46.6333,
            country: "BR"
        },
        destination: {
            name: "Madrid, Spain",
            lat: 40.4168,
            lon: -3.7038,
            country: "ES"
        },
        attackType: "Path Traversal",
        timestamp: "2024-01-15T14:45:00Z"
    },
    {
        id: 6,
        source: {
            name: "Mumbai, India",
            lat: 19.0760,
            lon: 72.8777,
            country: "IN"
        },
        destination: {
            name: "Singapore",
            lat: 1.3521,
            lon: 103.8198,
            country: "SG"
        },
        attackType: "Command Injection",
        timestamp: "2024-01-15T15:30:00Z"
    },
    {
        id: 7,
        source: {
            name: "Dubai, UAE",
            lat: 25.2048,
            lon: 55.2708,
            country: "AE"
        },
        destination: {
            name: "Paris, France",
            lat: 48.8566,
            lon: 2.3522,
            country: "FR"
        },
        attackType: "SSRF Attack",
        timestamp: "2024-01-15T16:10:00Z"
    },
    {
        id: 8,
        source: {
            name: "Seoul, South Korea",
            lat: 37.5665,
            lon: 126.9780,
            country: "KR"
        },
        destination: {
            name: "Toronto, Canada",
            lat: 43.6532,
            lon: -79.3832,
            country: "CA"
        },
        attackType: "XXE Attack",
        timestamp: "2024-01-15T17:00:00Z"
    }
]