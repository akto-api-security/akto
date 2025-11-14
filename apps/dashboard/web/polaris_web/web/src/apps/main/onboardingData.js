const HOMEDASHBOARD_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const HOMEDASHBOARD_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/fRyusl8ppdY/sddefault.jpg"
const HOMEDASHBOARD_VIDEO_LENGTH = 195

const COLLECTIONS_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const COLLECTIONS_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/fRyusl8ppdY/sddefault.jpg"
const COLLECTIONS_VIDEO_LENGTH = 195

const TESTING_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const TESTING_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/fRyusl8ppdY/sddefault.jpg"
const TESTING_VIDEO_LENGTH = 195 

const ISSUES_PAGE_DOCS_URL = "https://docs.akto.io/readme"
const ENDPOINTS_PAGE_DOCS_URL = "https://docs.akto.io/api-inventory/concepts/api-collection"
const AUTH_TYPES_PAGE_DOCS_URL = "https://docs.akto.io/api-inventory/concepts/auth-types"
const TAGS_PAGE_DOCS_URL = "https://docs.akto.io/readme"
const ROLES_PAGE_DOCS_URL = "https://docs.akto.io/testing/user-roles"

const learnMoreObject = {
    dashboard_home: {
        docsLink: [
            {
                content:"Get started guide",
                value:"https://docs.akto.io/readme"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_query_mode: {
        title: "Explore Mode",
        description: "Convenient way manage APIs and create collections",
        docsLink: [
            {
                content:"What is an API Collection?",
                value:"https://docs.akto.io/api-inventory/concepts/api-collection#what-is-an-api-collection"
            },
            {
                content:"What is Explore mode",
                value:"https://docs.akto.io/api-inventory/concepts/explore-mode"
            },
            {
                content:"How to Add collection using Explore mode",
                value:"https://docs.akto.io/api-inventory/how-to/add-collection-using-explore-mode"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_inventory: {
        title: "API Inventory",
        description: "Convenient way to access and manage APIs",
        docsLink: [
            {
                content:"What is an API Collection?",
                value:"https://docs.akto.io/api-inventory/concepts/api-collection#what-is-an-api-collection"
            },
            {
                content: "What is an API group?",
                value: "https://docs.akto.io/api-inventory/concepts/api-groups"
            },
            {
                content: "How to create API group?",
                value: "https://docs.akto.io/api-inventory/how-to/create-api-group"
            },
            {
                content: "How to Remove API(s) from API group",
                value: "https://docs.akto.io/api-inventory/how-to/remove-api-s-from-api-group",
            },
            {
                content: "How to Deactivate API collection?",
                value: "https://docs.akto.io/api-inventory/how-to/deactivate-an-api-collection"
            },
            {
                content: "How to Set environment type",
                value: "https://docs.akto.io/api-inventory/how-to/set-environment-type"
            },
            {
                content:"How to Run Test on Any One Endpoint",
                value:"https://docs.akto.io/api-security-testing/how-to/run-test-on-any-one-endpoint"
            },
            {
                content: "How to Open Endpoint in Test Editor",
                value: "https://docs.akto.io/test-editor/how-to/opening-endpoint-in-test-editor"
            },
            {
                content:"How to connect API traffic source",
                value:"https://docs.akto.io/traffic-connections/traffic-data-sources"
            },
            {
                content:"How to export an API Collection to Postman",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-postman"
            },
            {
                content:"How to export an API Collection to Burp",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-burp"
            },
            {
                content:"How to create Swagger File using Akto",
                value:"https://docs.akto.io/api-inventory/how-to/create-swagger-file-using-akto"
            },
            {
                content:"How to delete an API Collection",
                value:"https://docs.akto.io/api-inventory/how-to/delete-an-api-collection"
            },
            {
                content: 'What is risk score',
                value: 'https://docs.akto.io/api-discovery/concepts/risk-score'
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_inventory_id: {
        title: "API Endpoints",
        description: "All API endpoints across all of your services in Akto.",
        docsLink: [
            {
                content:"What is an API endpoint",
                value:"https://docs.akto.io/api-inventory/concepts/api-endpoints#what-is-an-api-endpoint"
            },
            {
                content:"Meta Properties of API Endpoint",
                value:"https://docs.akto.io/api-inventory/concepts/meta-properties-of-api-endpoint"
            },
            {
                content:"How to copy API Endpoints Data",
                value:"https://docs.akto.io/api-inventory/how-to/copy-api-endpoints-data"
            },
            {
                content:"How to export an API Collection to Postman",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-postman"
            },
            {
                content:"How to export an API Collection to Burp",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-burp"
            },
            {
                content:"How to create Swagger File using Akto",
                value:"https://docs.akto.io/api-inventory/how-to/create-swagger-file-using-akto"
            }, {
                content: 'What is risk score',
                value: 'https://docs.akto.io/api-discovery/concepts/risk-score'
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_changes: {
        title: "API changes",
        description: "Explore the API changes and more.",
        docsLink: [
            {
                content:"Know API changes",
                value:"https://docs.akto.io/api-inventory/concepts/api-changes"
            },
            {
                content:"How to configure alerts on API changes",
                value:"https://docs.akto.io/api-inventory/how-to/configure-alerts-on-api-changes"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_sensitive: {
        title: "Sensitive data",
        description: "Explore the sensitive data and its parameters.",
        docsLink: [
            {
                content:"What is Sensitive Parameter?",
                value:"https://docs.akto.io/api-inventory/concepts/sensitive-data#what-is-sensitive-parameter"
            },
            {
                content:"What is Data Type",
                value:"https://docs.akto.io/api-inventory/concepts/data-types#what-is-data-type"
            },
            {
                content:"How to create a Custom Data Type",
                value:"https://docs.akto.io/api-inventory/how-to/create-a-custom-data-type"
            },
            {
                content:"How to set sensitivity of a Data Type",
                value:"https://docs.akto.io/api-inventory/how-to/set-sensitivity-of-a-data-type"
            },
            {
                content:"How to De-activate a data type",
                value:"https://docs.akto.io/api-inventory/how-to/de-activate-a-data-type"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_testing: {
        title: "Test results",
        description: "View all your test results and take action on them in one place.",
        docsLink: [
            {
                content:"Run your first test",
                value:"https://docs.akto.io/testing/run-test"
            },
            {
                content:"Learn Result Types",
                value:"https://docs.akto.io/api-security-testing/concepts/result-types"
            },
            {
                content:"How to run tests in CLI using Akto",
                value:"https://docs.akto.io/testing/run-tests-in-cli-using-akto"
            },
            {
                content:"How to run tests in CI/CD",
                value:"https://docs.akto.io/testing/run-tests-in-cicd"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_testing_roles: {
        docsLink: [
            {
                content:"How to create new test role",
                value:"https://docs.akto.io/testing/user-roles"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_testing_user_config:{
        docsLink: [
            {
                content:"How to create user config",
                value:"https://docs.akto.io/testing/create-user-config"
            },
            {
                content:"How to Configure Global Rate Limit",
                value:"https://docs.akto.io/api-security-testing/how-to/configure-global-rate-limit"
            },
            {
                content:"How to Configure Pre-request script",
                value:"https://docs.akto.io/api-security-testing/how-to/configure-pre-request-script"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_test_library_tests:{
        title:"Tests",
        description:"View and manage your security test library, browse available tests and run them against your APIs.",
        docsLink:[{
            content:"What is Dynamic Severity",
            value:"https://docs.akto.io/test-editor/concepts/dynamic-severity"
        }]
    },
    dashboard_issues:{
        title: "API Issues",
        description: "Manage, export & share detailed report of vulnerabilities in your APIs.",
        docsLink: [
            {
                content:"Issues Overview",
                value:"https://docs.akto.io/issues/concepts/overview"
            },
            {
                content:"Issue Values",
                value:"https://docs.akto.io/issues/concepts/values"
            },
            {
                content:"Vulnerability Report",
                value:"https://docs.akto.io/issues/concepts/vulnerability-report"
            },
            {
                content:"How to Integrate Jira",
                value:"https://docs.akto.io/issues/how-to/jira-integration"
            },
            {
                content:"How to Triage issues",
                value:"https://docs.akto.io/issues/how-to/triage-issues"
            },
            {
                content:"How to Export Vulnerability Report",
                value:"https://docs.akto.io/issues/how-to/export-vulnerability-report"
            },
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_users:{
        docsLink: [
            {
                content:"How to invite user",
                value:"https://docs.akto.io/account/invite-user"
            },
            {
                content:"How to change role of a User",
                value:"https://docs.akto.io/account/invite-user/change-role-of-a-user"
            },
            {
                content:"Understanding Role Permissions",
                value:"https://docs.akto.io/account/understanding-role-permissions"
            },
        ]
    },
    dashboard_settings_about:{
        docsLink: [
            {
                content:"How to Add Private CIDRs list",
                value:"https://docs.akto.io/api-inventory/how-to/add-private-cidrs-list"
            },
            {
                content:"How to Configure Third parties IPs list",
                value:"https://docs.akto.io/api-inventory/how-to/configure-access-types"
            }
        ],
    },
    dashboard_settings_integrations_ci_cd:{
        docsLink: [
            {
                content:"Run tests in CI/CD",
                value:"https://docs.akto.io/testing/run-tests-in-cicd"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_auth_types:{
        docsLink: [
            {
                content:"How to create Auth Type",
                value:"https://docs.akto.io/api-inventory/concepts/auth-types"
            },
            {
                content:"How to Add a Custom Auth Type",
                value:"https://docs.akto.io/api-inventory/how-to/add-a-custom-auth-type"
            },
            {
                content:"How to Reset Auth Type",
                value:"https://docs.akto.io/api-inventory/how-to/reset-an-auth-type"
            },
            {
                content:"How to Create & Edit Auth Type",
                value:"https://docs.akto.io/api-security-testing/how-to/create-and-edit-auth-types"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_advanced_filters:{
        docsLink: [
            {
                content:"Configure Advanced Filters",
                value:"https://docs.akto.io/api-inventory/concepts/advanced-filter-option"
            }
        ],
    },
    dashboard_settings_threat_configuration:{
        docsLink: [
            {
                content:"How to configure Threat Actor",
                value:"https://docs.akto.io/api-protection/concepts/threat-actors"
            },
            {
                content: "How to configure Dynamic Rate Limits",
                value: "https://docs.akto.io/api-protection/concepts/api-rate-limit"
            }
        ],
    },
    dashboard_settings_tags:{
        docsLink: [
            {
                content:"How to create tag",
                value:"https://docs.akto.io/api-inventory/concepts/tags"
            },
            {
                content:"How to Create new Tag",
                value:"https://docs.akto.io/api-inventory/how-to/create-new-tags"
            },
            {
                content:"How to Edit Tag",
                value:"https://docs.akto.io/api-inventory/how-to/edit-tags"
            },
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations:{
        docsLink: [
            {
                content:"Get started guide",
                value:"https://docs.akto.io/readme"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_burp:{
        docsLink: [
            {
                content:"Connect Akto with Burp Suite",
                value:"https://docs.akto.io/traffic-connections/traffic-data-sources/burp-suite"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_postman:{
        docsLink: [
            {
                content:"Connect Akto with Postman",
                value:"https://docs.akto.io/traffic-connections/traffic-data-sources/postman"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_akto_apis:{
        docsLink: [
            {
                content:"API reference",
                value:"https://docs.akto.io/api-reference/api-reference"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_akto_gpt:{
        title: "Akto GPT",
        description: "Harness the power of ChatGPT for API Security on your fingertips now.",
        docsLink: [
            {
                content:"AktoGPT Guide",
                value:"https://docs.akto.io/aktogpt"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_slack:{
        docsLink: [
            {
                content:"What is Webhook Alert?",
                value:"https://docs.akto.io/api-inventory/concepts/alerts#what-is-webhook-alert"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_webhooks:{
        docsLink: [
            {
                content:"What is Webhook Alert?",
                value:"https://docs.akto.io/api-inventory/concepts/alerts#what-is-webhook-alert"
            },
            {
                content:"How to configure alerts on API changes",
                value:"https://docs.akto.io/api-inventory/how-to/configure-alerts-on-api-changes"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_github_sso:{
        docsLink: [
            {
                content:"How to setup GitHub SSO",
                value:"https://docs.akto.io/sso/github-oidc"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_azure_sso:{
        docsLink: [
            {
                content:"How to setup Azure SSO",
                value:"https://docs.akto.io/sso/azuread-saml"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_okta_sso:{
        docsLink: [
            {
                content:"How to setup Okta SSO",
                value:"https://docs.akto.io/sso/okta-oidc"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_jira:{
        docsLink: [
            {
                content:"Jira Integration guide",
                value:"https://docs.akto.io/issues/how-to/jira-integration"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_integrations_github_app:{
        docsLink: [
            {
                content:"Get started guide",
                value:"https://docs.akto.io/readme"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_test_library:{
        docsLink: [
            {
                content:"How to contribute to test library",
                value:"https://docs.akto.io/test-editor/test-library#how-to-contribute-to-test-library"
            },
            {
                content:"How to add a new test library",
                value:"https://docs.akto.io/test-editor/how-to/add-a-new-test-library"
            },
            {
                content:"How to edit built in test",
                value:"https://docs.akto.io/test-editor/writing-custom-tests"
            },
            {
                content:"Test YAML Syntax",
                value:"https://docs.akto.io/test-editor/test-yaml-syntax-one-pager"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_settings_billing:{
        docsLink: [
            {
                content:"Pricing plans",
                value:"https://docs.akto.io/pricing/pricing-plans"
            },
            {
                content:"Upgrade your plan",
                value:"https://docs.akto.io/pricing/how-to/upgrade-your-plan"
            },
            {
                content:"Downgrade your plan",
                value:"https://docs.akto.io/pricing/how-to/downgrade-your-plan"
            },
            {
                content: "How to Sync Usage Data",
                value: "https://docs.akto.io/pricing/how-to/sync-usage-data"
            },
        ],
    },
    dashboard_test_editor:{
        title: "Test editor",
        description: "Test playground for security teams and developers to write custom tests to find vulnerabilities in APIs.",
        docsLink: [
            {
                content:"What is test editor",
                value:"https://docs.akto.io/test-editor/overview"
            },
            {
                content:"How to edit built in test",
                value:"https://docs.akto.io/test-editor/writing-custom-tests"
            },
            {
                content:"Test YAML Syntax",
                value:"https://docs.akto.io/test-editor/test-yaml-syntax-one-pager"
            },
            {
                content:"How to write Custom Tests",
                value:"https://docs.akto.io/test-editor/writing-custom-tests"
            },
            {
                content:"How to Copy Test Content",
                value:"https://docs.akto.io/test-editor/how-to/copy-test-content"
            }
        ],
        videoLink: [
            {
                content: "Watch Test Editor demo" , 
                value: "https://www.youtube.com/watch?v=4BIBra9J0Ek"
            }
        ]
    },
    // TODO: update docs links
    dashboard_agent_team_members: {
        docsLink: [
            {
                content: "Using agents",
                value: "https://docs.akto.io/"
            },
            {
                content: "Discover agents",
                value: "https://docs.akto.io/"
            }
        ],
    },
    dashboard_mcp_security: {
        title: "MCP Security",
        description: "Learn more about MCP Security, discovery, and test results.",
        docsLink: [
            {
                content: "MCP Security Overview",
                value: "https://docs.akto.io/agentic-ai/akto-mcp-server"
            },
            {
                content: "How to use MCP Security",
                value: "https://docs.akto.io/agentic-ai/akto-mcp-server"
            }
        ]
    },
    dashboard_protection_configure_exploits: {
        docsLink: [
            {
                content: "How to configure Successful Exploits",
                value: "https://docs.akto.io/api-protection/concepts/successful-exploits"
            }
        ]
    },
    dashboard_protection_configure_ignored_events: {
        docsLink: [
            {
                content: "How to configure Ignored Events",
                value: "https://docs.akto.io/api-protection/concepts/threat-ignored-events"
            }
        ]
    }
}

export { HOMEDASHBOARD_VIDEO_LENGTH, HOMEDASHBOARD_VIDEO_URL, HOMEDASHBOARD_VIDEO_THUMBNAIL,
    COLLECTIONS_VIDEO_LENGTH, COLLECTIONS_VIDEO_THUMBNAIL, COLLECTIONS_VIDEO_URL,
    TESTING_VIDEO_LENGTH, TESTING_VIDEO_THUMBNAIL, TESTING_VIDEO_URL,
    learnMoreObject, ISSUES_PAGE_DOCS_URL, ROLES_PAGE_DOCS_URL, ENDPOINTS_PAGE_DOCS_URL,
    AUTH_TYPES_PAGE_DOCS_URL, TAGS_PAGE_DOCS_URL
}
