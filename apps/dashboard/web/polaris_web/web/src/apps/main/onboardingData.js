const HOMEDASHBOARD_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const HOMEDASHBOARD_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/lKzEU0f_rVI/sddefault.jpg"
const HOMEDASHBOARD_VIDEO_LENGTH = 35

const COLLECTIONS_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const COLLECTIONS_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/lKzEU0f_rVI/sddefault.jpg"
const COLLECTIONS_VIDEO_LENGTH = 35

const TESTING_VIDEO_URL = "https://www.youtube.com/watch?v=fRyusl8ppdY"
const TESTING_VIDEO_THUMBNAIL = "https://img.youtube.com/vi/lKzEU0f_rVI/sddefault.jpg"
const TESTING_VIDEO_LENGTH = 35 

const ISSUES_PAGE_DOCS_URL = "https://docs.akto.io/readme"
const ENDPOINTS_PAGE_DOCS_URL = "https://docs.akto.io/api-inventory/concepts/api-collection"
const AUTH_TYPES_PAGE_DOCS_URL = "https://docs.akto.io/api-inventory/concepts/auth-types"
const TAGS_PAGE_DOCS_URL = "https://docs.akto.io/readme"
const ROLES_PAGE_DOCS_URL = "https://docs.akto.io/testing/user-roles"

const learnMoreObject = {
    dashboard_home: {
        title: "Home page",
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
    dashboard_observe_inventory: {
        title: "Collections page",
        description: "Convenient way to access and manage APIs",
        docsLink: [
            {
                content:"What is an API Collection?",
                value:"https://docs.akto.io/api-inventory/concepts/api-collection#what-is-an-api-collection"
            }
             {
                content:"How to connect API traffic source",
                value:"https://docs.akto.io/traffic-connections/traffic-data-sources"
            }
            {
                content:"How to export an API Collection to Postman",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-postman"
            }
            {
                content:"How to export an API Collection to Burp",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-burp"
            }
            {
                content:"How to create Swagger File using Akto",
                value:"https://docs.akto.io/api-inventory/how-to/create-swagger-file-using-akto"
            }
            {
                content:"How to delete an API Collection",
                value:"https://docs.akto.io/api-inventory/how-to/delete-an-api-collection"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_observe_inventory_ids: {
        title: "Endpoints page",
        description: "All API endpoints across all of your services in Akto.",
        docsLink: [
            {
                content:"What is an API endpoint",
                value:"https://docs.akto.io/api-inventory/concepts/api-endpoints#what-is-an-api-endpoint"
            }
            {
                content:"Meta Properties of API Endpoint",
                value:"https://docs.akto.io/api-inventory/concepts/meta-properties-of-api-endpoint"
            }
            {
                content:"How to copy API Endpoints Data",
                value:"https://docs.akto.io/api-inventory/how-to/copy-api-endpoints-data"
            }
            {
                content:"How to export an API Collection to Postman",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-postman"
            }
            {
                content:"How to export an API Collection to Burp",
                value:"https://docs.akto.io/api-inventory/how-to/export-an-api-collection-to-burp"
            }
            {
                content:"How to create Swagger File using Akto",
                value:"https://docs.akto.io/api-inventory/how-to/create-swagger-file-using-akto"
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
        title: "API changes page",
        description: "Explore the API changes and more.",
        docsLink: [
            {
                content:"Know API changes",
                value:"https://docs.akto.io/api-inventory/concepts/api-changes"
            }
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
        title: "Sensitive data page",
        description: "Explore the sensitive data and its parameters.",
        docsLink: [
            {
                content:"What is Sensitive Parameter?",
                value:"https://docs.akto.io/api-inventory/concepts/sensitive-data#what-is-sensitive-parameter"
            }
            {
                content:"What is Data Type",
                value:"https://docs.akto.io/api-inventory/concepts/data-types#what-is-data-type"
            }
            {
                content:"How to create a Custom Data Type",
                value:"https://docs.akto.io/api-inventory/how-to/create-a-custom-data-type"
            }
            {
                content:"How to set sensitivity of a Data Type",
                value:"https://docs.akto.io/api-inventory/how-to/set-sensitivity-of-a-data-type"
            }
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
        title: "Testing page",
        description: "View all your test results and take action on them in one place.",
        docsLink: [
            {
                content:"Run your first test",
                value:"https://docs.akto.io/testing/run-test"
            }
            {
                content:"How to run tests in CLI using Akto",
                value:"https://docs.akto.io/testing/run-tests-in-cli-using-akto"
            }
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
        title: "Test roles page",
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
        title: "User config page",
        docsLink: [
            {
                content:"How to create user config",
                value:"https://docs.akto.io/testing/create-user-config"
            }
        ],
        videoLink: [
            {
                content: "Watch Akto demo" , 
                value: "https://www.youtube.com/watch?v=fRyusl8ppdY"
            }
        ]
    },
    dashboard_issues:{
        title: "Testing issues page",
        description: "Manage, export & share detailed report of vulnerabilities in your APIs.",
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
    dashboard_test_editor:{
        title: "Test editor page",
        description: "Test playground for security teams and developers to write custom tests to find vulnerabilities in APIs.",
        docsLink: [
            {
                content:"What is test editor",
                value:"https://docs.akto.io/test-editor/overview"
            }
            {
                content:"How to edit built in test",
                value:"https://docs.akto.io/test-editor/writing-custom-tests"
            }
            {
                content:"Test YAML Syntax",
                value:"https://docs.akto.io/test-editor/test-yaml-syntax-one-pager"
            }
            {
                content:"How to write Custom Tests",
                value:"https://docs.akto.io/test-editor/writing-custom-tests"
            }
        ],
        videoLink: [
            {
                content: "Watch Test Editor demo" , 
                value: "https://www.youtube.com/watch?v=4BIBra9J0Ek"
            }
        ]
    },
}

export { HOMEDASHBOARD_VIDEO_LENGTH, HOMEDASHBOARD_VIDEO_URL, HOMEDASHBOARD_VIDEO_THUMBNAIL,
    COLLECTIONS_VIDEO_LENGTH, COLLECTIONS_VIDEO_THUMBNAIL, COLLECTIONS_VIDEO_URL,
    TESTING_VIDEO_LENGTH, TESTING_VIDEO_THUMBNAIL, TESTING_VIDEO_URL,
    learnMoreObject, ISSUES_PAGE_DOCS_URL, ROLES_PAGE_DOCS_URL, ENDPOINTS_PAGE_DOCS_URL,
    AUTH_TYPES_PAGE_DOCS_URL, TAGS_PAGE_DOCS_URL
}
