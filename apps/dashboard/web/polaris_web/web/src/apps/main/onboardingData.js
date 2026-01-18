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
        api_security: {
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
        agentic_security: {
            docsLink: [
                {
                    content: "What is Akto Argus",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/overview"
                },
                {
                    content: "Agentic Discovery",
                    value: "https://ai-security-docs.akto.io/agentic-discovery/get-started"
                },
                {
                    content: "Connect Your First AI Agent",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors"
                },
                {
                    content: "Agentic Red Teaming",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/get-started"
                },
                {
                    content: "Agentic Guardrails",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview"
                }
            ]
        },
        endpoint_security: {
            docsLink: [
                {
                    content: "What is Akto Atlas",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/overview"
                },
                {
                    content: "Endpoint Discovery",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents"
                },
                {
                    content: "Browser Extensions",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/browser-extensions/chrome"
                },
                {
                    content: "MCP Endpoint Shield",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/mcp-endpoint-shield"
                }
            ]
        }
    },
    dashboard_observe_query_mode: {
        api_security: {
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
        }
    },
    dashboard_observe_inventory: {
        api_security: {
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
        agentic_security: {
            title: "Akto Argus Inventory",
            description: "Discover and manage AI agents and MCP servers",
            docsLink: [
                {
                    content: "Agentic Discovery",
                    value: "https://ai-security-docs.akto.io/agentic-discovery/get-started"
                },
                
                {
                    content: "Connect AI Agents",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors"
                }
            ]
        }
    },
    dashboard_observe_inventory_id: {
        api_security: {
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
                },
                {
                    content: 'What is risk score',
                    value: 'https://docs.akto.io/api-discovery/concepts/risk-score'
                },
                {
                    content: `How Akto detects Shadow APIs`,
                    value: `https://docs.akto.io/api-inventory/concepts/shadow-apis`
                },
                {
                    content: `How Akto detects Zombie APIs`,
                    value: `https://docs.akto.io/api-inventory/concepts/zombie-apis`
                }
            ],
            videoLink: [
                {
                    content: "Watch Akto demo" ,
                    value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
                }
            ]
        }
    },
    dashboard_observe_changes: {
        api_security: {
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
        }
    },
    dashboard_observe_sensitive: {
        api_security: {
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
        agentic_security: {
            title: "Sensitive data",
            description: "Identify and manage sensitive data in AI agents and MCP servers",
            docsLink: [
                {
                    content:"What is Sensitive Data",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/sensitive-data"
                },
                {
                    content:"What is Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/data-types"
                },
                {
                    content:"How to create a Custom Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/create-a-custom-data-type"
                },
                {
                    content:"How to set sensitivity of a Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/set-sensitivity-of-a-data-type"
                },
                {
                    content:"How to De-activate a data type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/de-activate-a-data-type"
                },
                {
                    content:"How to Redact sensitive data",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/redact-sensitive-data"
                }
            ]
        },
        endpoint_security: {
            title: "Sensitive data",
            description: "Monitor and protect sensitive data on employee endpoints",
            docsLink: [
                {
                    content:"What is Sensitive Data",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/sensitive-data"
                },
                {
                    content:"What is Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/data-types"
                },
                {
                    content:"How to create a Custom Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/create-a-custom-data-type"
                },
                {
                    content:"How to set sensitivity of a Data Type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/set-sensitivity-of-a-data-type"
                },
                {
                    content:"How to De-activate a data type",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/de-activate-a-data-type"
                },
                {
                    content:"How to Redact sensitive data",
                    value:"https://ai-security-docs.akto.io/agentic-ai-discovery/how-to/redact-sensitive-data"
                }
            ]
        }
    },
    dashboard_testing: {
        api_security: {
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
        agentic_security: {
            title: "Agentic Red Teaming Results",
            description: "View security test results for AI agents and MCP servers",
            docsLink: [
                {
                    content: "Run Your First Agentic Test",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/get-started"
                },
                {
                    content: "Understanding Scan Results",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/test-result"
                },
                {
                    content: "Result Types",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/result-types"
                },
                {
                    content: "Run Tests in CI/CD",
                    value: "https://ai-security-docs.akto.io/integrations/ci-cd-integrations"
                }
            ]
        }
    },
    dashboard_testing_roles: {
        api_security: {
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
        agentic_security: {
            docsLink: [
                {
                    content:"Create a Test Role",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/create-a-test-role"
                },
                {
                    content:"Edit Auth Flow in Test Roles",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/edit-auth-flow-in-test-roles"
                },
                {
                    content:"Conduct Role-Based Testing",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/conduct-role-based-testing"
                },
                {
                    content:"Restrict Access to Test Role Using RBAC",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/restrict-test-role-rbac"
                },
                {
                    content:"Scan Roles Concept",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/test-role"
                }
            ]
        }
    },
    dashboard_testing_user_config:{
        api_security: {
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
        agentic_security: {
            docsLink: [
                {
                    content:"What is User Config",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/user-config"
                },
                {
                    content:"Configure Global Rate Limit",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/configure-global-rate-limit"
                },
                {
                    content:"Configure Pre-request Script",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/configure-pre-request-script"
                },
                {
                    content:"Global Test Template Configuration",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/how-to/global-test-template-configuration"
                },
                {
                    content:"JSON Recording for Auth Tokens",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/json-recording-for-automated-auth-tokens"
                }
            ]
        }
    },
    dashboard_test_library_tests:{
        api_security: {
            title:"Tests",
            description:"View and manage your security test library, browse available tests and run them against your APIs.",
            docsLink:[{
                content:"What is Dynamic Severity",
                value:"https://docs.akto.io/test-editor/concepts/dynamic-severity"
            }]
        },
        agentic_security: {
            title:"Probe Library",
            description:"Browse 1000+ AI security probes for testing agents and MCP servers",
            docsLink:[
                {
                    content:"What is Probe Library",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/concepts/overview"
                },
                {
                    content:"Test YAML Syntax",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/concepts/test-yaml-syntax-detailed"
                },
                {
                    content:"Create Custom Test",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/create-a-custom-test"
                }
            ]
        }
    },
    dashboard_issues:{
        api_security: {
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
        agentic_security: {
            title: "AI Security Issues",
            description: "Manage vulnerabilities in AI agents and MCP servers",
            docsLink: [
                {
                    content:"Issues Overview",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues"
                },
                {
                    content:"Vulnerability Report",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/vulnerability-report"
                },
                {
                    content:"Remediation Steps",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/remediation"
                },
                {
                    content:"Triage Issues",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/triage-issues"
                },
                {
                    content:"Export Vulnerability Report",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/export-vulnerability-report"
                },
                {
                    content:"Integrate with Jira",
                    value:"https://ai-security-docs.akto.io/integrations/jira-integration"
                }
            ]
        }
    },
    dashboard_settings_users:{
        api_security: {
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
                }
            ]
        },
        docsLink: [
            {
                content:"Invite User",
                value:"https://ai-security-docs.akto.io/account-management/invite-user"
            },
            {
                content:"Change User Role",
                value:"https://ai-security-docs.akto.io/account-management/invite-user/change-role-of-a-user"
            },
            {
                content:"Understanding Role Permissions",
                value:"https://ai-security-docs.akto.io/account-management/understanding-role-permissions"
            },
            {
                content:"Custom Roles",
                value:"https://ai-security-docs.akto.io/account-management/custom-roles"
            }
        ]
    },
    dashboard_settings_about:{
        api_security: {
            docsLink: [
                {
                    content:"How to Add Private CIDRs list",
                    value:"https://docs.akto.io/api-inventory/how-to/add-private-cidrs-list"
                },
                {
                    content:"How to Configure Third parties IPs list",
                    value:"https://docs.akto.io/api-inventory/how-to/configure-access-types"
                }
            ]
        }
    },
    dashboard_settings_integrations_ci_cd:{
        api_security: {
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
        docsLink: [
            {
                content:"CI/CD Integrations Overview",
                value:"https://ai-security-docs.akto.io/integrations/ci-cd-integrations"
            },
            {
                content:"GitHub Actions",
                value:"https://ai-security-docs.akto.io/integrations/ci-cd-integrations/github-actions"
            },
            {
                content:"Jenkins",
                value:"https://ai-security-docs.akto.io/integrations/ci-cd-integrations/jenkins"
            },
            {
                content:"GitLab",
                value:"https://ai-security-docs.akto.io/integrations/ci-cd-integrations/gitlab"
            }
        ]
    },
    dashboard_settings_auth_types:{
        api_security: {
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
        }
    },
    dashboard_settings_advanced_filters:{
        api_security: {
            docsLink: [
                {
                    content:"Configure Advanced Filters",
                    value:"https://docs.akto.io/api-inventory/concepts/advanced-filter-option"
                }
            ]
        }
    },
    dashboard_settings_threat_configuration:{
        api_security: {
            docsLink: [
                {
                    content:"How to configure Threat Actor",
                    value:"https://docs.akto.io/api-protection/concepts/threat-actors"
                },
                {
                    content: "How to configure Dynamic Rate Limits",
                    value: "https://docs.akto.io/api-protection/concepts/api-rate-limit"
                }
            ]
        }
    },
    dashboard_settings_tags:{
        api_security: {
            docsLink: [
                {
                    content:"How to create tag",
                    value:"https://docs.akto.io/api-inventory/how-to/set-tags"
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
        }
    },
    dashboard_settings_integrations:{
        api_security: {
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
        }
    },
    dashboard_settings_integrations_burp:{
        api_security: {
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
        }
    },
    dashboard_settings_integrations_postman:{
        api_security: {
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
        }
    },
    dashboard_settings_integrations_akto_apis:{
        api_security: {
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
        }
    },
    dashboard_settings_integrations_akto_gpt:{
        api_security: {
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
        }
    },
    dashboard_settings_integrations_slack:{
        api_security: {
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
        docsLink: [
            {
                content:"Setup Slack Webhook",
                value:"https://ai-security-docs.akto.io/alerts/slack-webhook"
            },
            {
                content:"Configure Test Alerts",
                value:"https://ai-security-docs.akto.io/alerts/alerts-testing-results"
            }
        ]
    },
    dashboard_settings_integrations_webhooks:{
        api_security: {
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
        docsLink: [
            {
                content:"Gmail Webhook",
                value:"https://ai-security-docs.akto.io/alerts/gmail-webhook"
            },
            {
                content:"Microsoft Teams Webhook",
                value:"https://ai-security-docs.akto.io/alerts/microsoft-teams-webhook"
            },
            {
                content:"Setup Test Alerts",
                value:"https://ai-security-docs.akto.io/alerts/alerts-testing-results"
            }
        ]
    },
    dashboard_settings_integrations_github_sso:{
        api_security: {
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
        docsLink: [
            {
                content:"SSO Overview",
                value:"https://ai-security-docs.akto.io/account-management/sso"
            }
        ]
    },
    dashboard_settings_integrations_azure_sso:{
        api_security: {
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
        docsLink: [
            {
                content:"SSO Overview",
                value:"https://ai-security-docs.akto.io/account-management/sso"
            }
        ]
    },
    dashboard_settings_integrations_okta_sso:{
        api_security: {
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
        docsLink: [
            {
                content:"SSO Overview",
                value:"https://ai-security-docs.akto.io/account-management/sso"
            }
        ]
    },
    dashboard_settings_integrations_jira:{
        api_security: {
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
        docsLink: [
            {
                content:"Jira Integration",
                value:"https://ai-security-docs.akto.io/integrations/jira-integration"
            }
        ]
    },
    dashboard_settings_integrations_devrev:{
        api_security: {
            docsLink: [
                {
                    content:"DevRev Integration guide",
                    value:"https://docs.akto.io/issues/how-to/devrev-integration"
                }
            ],
            videoLink: [
                {
                    content: "Watch Akto demo" ,
                    value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
                }
            ]
        }
    },
    dashboard_settings_integrations_github_app:{
        api_security: {
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
        }
    },
    dashboard_settings_test_library:{
        api_security: {
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
        docsLink: [
            {
                content:"Probe Library Overview",
                value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/concepts/overview"
            },
            {
                content:"Add a New Test Library",
                value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/add-a-new-test-library"
            },
            {
                content:"Create Custom Test",
                value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/create-a-custom-test"
            },
            {
                content:"Test YAML Syntax",
                value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/concepts/test-yaml-syntax-detailed"
            }
        ]
    },
    dashboard_settings_billing:{
        api_security: {
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
            ]
        }
    },
    dashboard_test_editor:{
        api_security: {
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
        agentic_security: {
            title: "Test editor",
            description: "Write custom security probes for AI agents and MCP servers",
            docsLink: [
                {
                    content:"Test Editor Overview",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/play-in-test-editor-background"
                },
                {
                    content:"Create Custom Test",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/create-a-custom-test"
                },
                {
                    content:"Test YAML Syntax",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/concepts/test-yaml-syntax-detailed"
                },
                {
                    content:"Edit Test",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/edit-test"
                },
                {
                    content:"Copy Test Content",
                    value:"https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/probe-library/how-to/copy-test-content"
                }
            ]
        }
    },
    dashboard_agent_team_members: {
        api_security: {
            docsLink: [
                {
                    content: "Using agents",
                    value: "https://docs.akto.io/"
                },
                {
                    content: "Discover agents",
                    value: "https://docs.akto.io/"
                }
            ]
        }
    },
    dashboard_mcp_security: {
        api_security: {
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
        agentic_security: {
            title: "MCP Security",
            description: "Secure and test Model Context Protocol servers",
            docsLink: [
                {
                    content: "MCP Red Teaming",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/concepts/mcp-red-teaming"
                },
                {
                    content: "MCP Import for Testing",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/agentic-red-teaming/get-started/manual-import/mcp-import"
                },
                {
                    content: "MCP Proxy Protection",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview/proxy/akto-mcp-proxy"
                },
                {
                    content: "MCP Discovery",
                    value: "https://ai-security-docs.akto.io/agentic-discovery/concepts/mcp-servers"
                }
            ]
        },
        endpoint_security: {
            title: "MCP Security",
            description: "Secure MCP endpoints on employee devices",
            docsLink: [
                {
                    content: "MCP Endpoint Shield",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/mcp-endpoint-shield"
                },
                {
                    content: "View Audit Data",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/audit-data"
                },
                {
                    content: "Cursor Hooks Integration",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/cursor-hooks"
                }
            ]
        }
    },
    dashboard_protection_threat_dashboard: {
        api_security: {
            docsLink: [
                {
                    content: "API Protection Overview",
                    value: "https://docs.akto.io/api-protection/overview"
                }
            ]
        },
        docsLink: [
            {
                content: "Agentic Guardrails Overview",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/overview"
            }
        ]
    },
    dashboard_protection_threat_activity: {
        api_security: {
            docsLink: [
                {
                    content: "View Threat Activity Breakdown",
                    value: "https://docs.akto.io/api-protection/how-to/view-threat-activity-breakdown"
                },
                {
                    content: "Take Actions on Threats",
                    value: "https://docs.akto.io/api-protection/how-to/take-actions-on-threats"
                }
            ]
        },
        docsLink: [
            {
                content: "Guardrail Activity Monitoring",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/concepts/guardrail-activity"
            },
            {
                content: "Detailed Activity View",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/guardrail-activity-detailed-view"
            }
        ]
    },
    dashboard_protection_threat_api: {
        api_security: {
            docsLink: [
                {
                    content: "API Protection Overview",
                    value: "https://docs.akto.io/api-protection/overview"
                }
            ]
        },
        docsLink: [
            {
                content: "Agentic Guardrails Overview",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/overview"
            }
        ]
    },
    dashboard_protection_threat_actor: {
        api_security: {
            docsLink: [
                {
                    content: "Understanding Threat Actors",
                    value: "https://docs.akto.io/api-protection/concepts/threat-actors"
                }
            ]
        },
        docsLink: [
            {
                content: "Guardrail Actors",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/concepts/threat-actors"
            },
            {
                content: "Access Specific Guardrail Actor",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/access-a-specific-guardrail-actor"
            }
        ]
    },
    dashboard_protection_threat_policy: {
        api_security: {
            docsLink: [
                {
                    content: "Threat Policy Concepts",
                    value: "https://docs.akto.io/api-protection/concepts/threat-policy"
                },
                {
                    content: "Manage Threat Policies",
                    value: "https://docs.akto.io/api-protection/how-to/manage-threat-policies"
                }
            ]
        },
        docsLink: [
            {
                content: "Create Guardrail Policies",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/create-guardrail-policies"
            },
            {
                content: "Manage Guardrail Policies",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/manage-guardrail-policies"
            }
        ]
    },
    dashboard_protection_configure_exploits: {
        api_security: {
            docsLink: [
                {
                    content: "How to configure Successful Exploits",
                    value: "https://docs.akto.io/api-protection/concepts/successful-exploits"
                }
            ]
        },
        agentic_security: {
            docsLink: [
                {
                    content: "Agentic Guardrails Overview",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview"
                },
                {
                    content: "MCP Proxy Protection",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview/proxy/akto-mcp-proxy"
                },
                {
                    content: "Agent Proxy Protection",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview/proxy/akto-agent-proxy"
                }
            ]
        }
    },
    dashboard_protection_configure_ignored_events: {
        api_security: {
            docsLink: [
                {
                    content: "How to configure Ignored Events",
                    value: "https://docs.akto.io/api-protection/concepts/threat-ignored-events"
                }
            ]
        },
        agentic_security: {
            docsLink: [
                {
                    content: "Agentic Guardrails Overview",
                    value: "https://ai-security-docs.akto.io/agentic-guardrails/overview"
                }
            ]
        }
    },
    dashboard_reports_issues: {
        api_security: {
            docsLink: [
                {
                    content: "Export Vulnerability Report",
                    value: "https://docs.akto.io/api-security-testing/how-to/export-test-results"
                }
            ]
        },
        agentic_security: {
            docsLink: [
                {
                    content: "Agentic Security Issues Overview",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues"
                },
                {
                    content: "Vulnerability Report Guide",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/vulnerability-report"
                },
                {
                    content: "Export Issues to Reports",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/export-selected-issues-to-reports"
                },
                {
                    content: "Triage and Manage Issues",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/issues/triage-issues"
                }
            ]
        }
    },
    dashboard_reports_compliance: {
        api_security: {
            docsLink: [
                {
                    content: "Export Summary Report",
                    value: "https://docs.akto.io/api-security-testing/concepts/export-summary-report"
                }
            ]
        },
        agentic_security: {
            docsLink: [
                {
                    content: "Compliance Dashboard Overview",
                    value: "https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/compliance"
                }
            ]
        }
    },
    dashboard_reports_threat: {
        api_security: {
            docsLink: []
        },
        agentic_security: {
            docsLink: []
        }
    },
    dashboard_guardrails_activity: {
        api_security: {
            docsLink: []
        },
        docsLink: [
            {
                content: "Guardrail Activity Monitoring",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/concepts/guardrail-activity"
            },
            {
                content: "Detailed Activity View",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/guardrail-activity-detailed-view"
            }
        ]
    },
    dashboard_guardrails_policies: {
        docsLink: [
            {
                content: "Create Guardrail Policies",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/create-guardrail-policies"
            },
            {
                content: "Manage Guardrail Policies",
                value: "https://ai-security-docs.akto.io/agentic-guardrails/how-to/manage-guardrail-policies"
            }
        ]
    },
    dashboard_observe_endpoints: {
        endpoint_security: {
            title: "Akto Atlas Inventory",
            description: "Discover AI tools across employee endpoints",
            docsLink: [
                {
                    content: "Endpoint Discovery",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents"
                },
                {
                    content: "View Audit Data",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/audit-data"
                },
                {
                    content: "Endpoint Shield Details",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/view-endpoint-shield-details"
                }
            ]
        }
    },
    dashboard_testing_test_suite: {
        api_security: {
            docsLink: [
                {
                    content: "Create Custom Test Suites",
                    value: "https://docs.akto.io/api-security-testing/how-to/create-custom-test-suites"
                }
            ]
        },
        docsLink: [
            {
                content: "Agentic Red Teaming",
                value: "https://ai-security-docs.akto.io/agentic-red-teaming/get-started"
            }
        ]
    },
    dashboard_testing_dependency: {
        api_security: {
            docsLink: []
        },
        docsLink: []
    },
    dashboard_observe_audit: {
        endpoint_security: {
            title: "Audit Data",
            description: "View and manage audit records for agentic components",
            docsLink: [
                {
                    content: "View Audit Data",
                    value: "https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/audit-data"
                }
            ]
        },
        agentic_security: {
            title: "Audit Data",
            description: "View and manage audit records for agentic components",
            docsLink: [
                {
                    content: "View Audit Data",
                    value: "https://ai-security-docs.akto.io/agentic-ai-discovery/concepts/audit-data"
                }
            ]
        }
    },
    dashboard_observe_endpoint_shield: {
        endpoint_security: {
            title: "Endpoint Shield",
            description: "Monitor and manage MCP Endpoint Shield agents",
            docsLink: [
                {
                    content: "MCP Endpoint Shield",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents/mcp-endpoint-shield"
                },
                {
                    content: "View Endpoint Shield Details",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/view-endpoint-shield-details"
                }
            ]
        }
    },
    dashboard_observe_agentic_assets: {
        endpoint_security: {
            title: "Agentic Assets (Atlas)",
            description: "View and manage agentic assets discovered on employee endpoints through Akto Atlas",
            docsLink: [
                {
                    content: "What is Akto Atlas",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/overview"
                },
                {
                    content: "Endpoint Discovery",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/endpoints-discovery-agents"
                },
                {
                    content: "View Audit Data",
                    value: "https://ai-security-docs.akto.io/akto-atlas-agentic-ai-security-for-employee-endpoints/audit-data"
                }
            ]
        }
    }
}

export { HOMEDASHBOARD_VIDEO_LENGTH, HOMEDASHBOARD_VIDEO_URL, HOMEDASHBOARD_VIDEO_THUMBNAIL,
    COLLECTIONS_VIDEO_LENGTH, COLLECTIONS_VIDEO_THUMBNAIL, COLLECTIONS_VIDEO_URL,
    TESTING_VIDEO_LENGTH, TESTING_VIDEO_THUMBNAIL, TESTING_VIDEO_URL,
    learnMoreObject, ISSUES_PAGE_DOCS_URL, ROLES_PAGE_DOCS_URL, ENDPOINTS_PAGE_DOCS_URL,
    AUTH_TYPES_PAGE_DOCS_URL, TAGS_PAGE_DOCS_URL
}
