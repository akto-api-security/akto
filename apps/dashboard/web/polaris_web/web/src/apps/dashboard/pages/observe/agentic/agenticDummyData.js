// ─── AI chat ──────────────────────────────────────────────────────────────────

export const MOCK_RESPONSE =
    "I've analysed the request context. Based on the endpoint traffic and skill invocation patterns, I can see some anomalous behavior. The prompt appears to violate the configured security policy - this type of request would be flagged as a critical violation.";

// ─── Skills ───────────────────────────────────────────────────────────────────

export const BASE_SKILL_NAMES = [
    "Generate Snapshot", "Design a Framework", "Construct a Prototype",
    "Craft a Snapshot", "Collect Insights", "Compile an Overview",
    "Generate an Analysis Report", "Summarize Findings", "Draft a Proposal",
    "Examine Data", "Prepare Documentation", "Master Testing Techniques",
    "Run Diagnostic", "Deploy Service", "Query Database", "Execute Script",
    "Fetch Credentials", "Parse Config", "Validate Schema", "Export Report",
    "Sync Repository", "Trigger Pipeline", "Scan Endpoints", "Audit Logs",
    "Monitor Resources", "Rotate Secrets", "Invoke Lambda", "List Buckets",
];

export function generateSkills(total) {
    return Array.from({ length: total }, (_, i) => ({
        id: i,
        name: BASE_SKILL_NAMES[i % BASE_SKILL_NAMES.length] +
              (i >= BASE_SKILL_NAMES.length ? ` v${Math.floor(i / BASE_SKILL_NAMES.length) + 1}` : ""),
        isNew: i < 6,
        violations: i === 0 ? 1 : 0,
        blocked: false,
    }));
}

export const DUMMY_SKILL_SAMPLE = {
    message: JSON.stringify({
        method: "POST",
        path: "/mcp/tools/call",
        requestHeaders: JSON.stringify({
            "content-type": "application/json",
            "authorization": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAwMSJ9",
            "x-mcp-session": "sess_8f2a91b4c3d1",
        }),
        requestPayload: JSON.stringify({
            name: "generate_snapshot",
            arguments: {
                prompt: "Generate a complete state snapshot",
                context: { sessionId: "sess_8f2a91b4c3d1", depth: 2 },
            },
        }),
        statusCode: 200,
        responseHeaders: JSON.stringify({
            "content-type": "application/json",
            "x-mcp-request-id": "req_7c3d12e9a4b5",
        }),
        responsePayload: JSON.stringify({
            content: [{
                type: "text",
                text: JSON.stringify({
                    snapshot: { id: "snap_20260522_001", timestamp: "2026-05-22T10:30:00Z", status: "complete" },
                    usage: { promptTokens: 142, completionTokens: 89 },
                }),
            }],
        }),
    }),
};

export const SKILL_SCHEMA_PARAMS = [
    { name: "prompt",     type: "string", required: true,  desc: "The instruction or prompt for the skill to execute" },
    { name: "context",    type: "object", required: false, desc: "Optional session context to scope the skill execution" },
    { name: "timeout_ms", type: "number", required: false, desc: "Maximum execution time in milliseconds" },
];

export const SKILL_VIOLATION_ROWS = [
    { id: 0, severity: "critical", title: "PII Sent to External LLM",     desc: "Skill transmitted user PII in the prompt payload to a third-party LLM without redaction or consent.", time: "2m ago"  },
    { id: 1, severity: "high",     title: "Excessive Token Consumption",   desc: "Skill consumed 52k tokens in a single invocation, exceeding the 10k policy limit.", time: "17m ago" },
    { id: 2, severity: "medium",   title: "Unusual Invocation Rate",       desc: "Skill invoked 47 times within 2 minutes, consistent with automated enumeration.", time: "1h ago"  },
];

// ─── MCP tools, resources, prompts ───────────────────────────────────────────

export const MCP_TOOLS = {
    "mcp.akto.io": [
        { id: 0, name: "run_api_scan",         riskLevel: "high",     description: "Trigger a security scan on a target API endpoint to detect OWASP Top 10 vulnerabilities and misconfigurations.", params: [{name:"target_url",type:"string",required:true,desc:"API endpoint URL to scan"},{name:"scan_profile",type:"string",required:false,desc:"Scan depth: quick, standard, deep"},{name:"auth_token",type:"string",required:false,desc:"Bearer token for authenticated scanning"}] },
        { id: 1, name: "get_vulnerabilities",  riskLevel: "medium",   description: "Retrieve vulnerabilities detected in the most recent scan, filtered by severity.", params: [{name:"severity",type:"string",required:false,desc:"Filter: critical, high, medium, low"},{name:"limit",type:"number",required:false,desc:"Max results (default 50)"}] },
        { id: 2, name: "generate_test_payload", riskLevel: "critical", description: "Generate synthetic attack payloads for pen-testing and red-team exercises.", params: [{name:"attack_type",type:"string",required:true,desc:"Attack class: sqli, xss, ssrf, auth_bypass, rce"},{name:"count",type:"number",required:false,desc:"Number of payloads to generate"},{name:"encoding",type:"string",required:false,desc:"Output encoding: none, base64, url"}] },
        { id: 3, name: "export_report",        riskLevel: "low",      description: "Export vulnerability scan results as a structured report.", params: [{name:"format",type:"string",required:true,desc:"Export format: pdf, csv, json, sarif"},{name:"scan_id",type:"string",required:false,desc:"Specific scan ID (defaults to latest)"}] },
        { id: 4, name: "replay_request",       riskLevel: "high",     description: "Replay a captured HTTP request with optional parameter mutations to test different inputs.", params: [{name:"request_id",type:"string",required:true,desc:"ID of the captured request"},{name:"mutations",type:"object",required:false,desc:"Key-value pairs to override in headers or payload"}] },
    ],
    "razorpay-stdio": [
        { id: 0, name: "create_payment_link",   riskLevel: "critical", description: "Create a new payment link for a customer. Supports UPI, cards, and net banking.", params: [{name:"amount",type:"number",required:true,desc:"Amount in smallest currency unit (paise for INR)"},{name:"currency",type:"string",required:true,desc:"ISO 4217 currency code (e.g., INR)"},{name:"description",type:"string",required:false,desc:"Payment description shown to customer"},{name:"customer_id",type:"string",required:false,desc:"Razorpay customer ID to pre-fill checkout"}] },
        { id: 1, name: "list_transactions",     riskLevel: "high",     description: "Retrieve a paginated list of recent payment transactions within a date range.", params: [{name:"from",type:"number",required:false,desc:"Start timestamp (Unix epoch)"},{name:"to",type:"number",required:false,desc:"End timestamp (Unix epoch)"},{name:"count",type:"number",required:false,desc:"Records per page (max 100)"}] },
        { id: 2, name: "refund_payment",        riskLevel: "critical", description: "Issue a full or partial refund for a completed payment. Refunds are irreversible.", params: [{name:"payment_id",type:"string",required:true,desc:"Razorpay payment ID (pay_XXX)"},{name:"amount",type:"number",required:false,desc:"Partial refund amount. Omit for full refund."},{name:"reason",type:"string",required:false,desc:"Reason: duplicate, fraudulent, customer_request"}] },
        { id: 3, name: "get_customer",          riskLevel: "medium",   description: "Fetch customer profile details by Razorpay customer ID.", params: [{name:"customer_id",type:"string",required:true,desc:"Razorpay customer ID (cust_XXX)"}] },
        { id: 4, name: "create_subscription",   riskLevel: "critical", description: "Create a recurring billing subscription on a predefined plan.", params: [{name:"plan_id",type:"string",required:true,desc:"Razorpay plan ID"},{name:"customer_id",type:"string",required:true,desc:"Customer to attach the subscription"},{name:"total_count",type:"number",required:true,desc:"Total number of billing cycles"}] },
        { id: 5, name: "get_settlement_report", riskLevel: "high",     description: "Retrieve payout settlement reports including net amounts and settlement dates.", params: [{name:"year",type:"number",required:true,desc:"Settlement year (YYYY)"},{name:"month",type:"number",required:true,desc:"Settlement month (1-12)"}] },
    ],
    "postgres-mcp": [
        { id: 0, name: "execute_query",  riskLevel: "critical", description: "Execute an arbitrary SQL query. Supports SELECT, INSERT, UPDATE, DELETE.", params: [{name:"query",type:"string",required:true,desc:"SQL query string"},{name:"params",type:"array",required:false,desc:"Parameterized bindings"},{name:"timeout_ms",type:"number",required:false,desc:"Query timeout in milliseconds"}] },
        { id: 1, name: "list_tables",    riskLevel: "low",      description: "List all tables and views in the database schema.", params: [{name:"schema",type:"string",required:false,desc:"Schema name (default: public)"},{name:"include_views",type:"boolean",required:false,desc:"Include views in result"}] },
        { id: 2, name: "describe_table", riskLevel: "low",      description: "Get column definitions, data types, constraints, and indexes for a table.", params: [{name:"table_name",type:"string",required:true,desc:"Fully qualified table name"}] },
        { id: 3, name: "export_data",    riskLevel: "high",     description: "Export the result of a SELECT query to CSV or JSON.", params: [{name:"query",type:"string",required:true,desc:"SELECT query to export"},{name:"format",type:"string",required:true,desc:"Output format: csv or json"}] },
    ],
    "kubernetes-mcp": [
        { id: 0, name: "list_pods",        riskLevel: "low",      description: "List running pods with their current status and resource utilization.", params: [{name:"namespace",type:"string",required:false,desc:"Kubernetes namespace"},{name:"label_selector",type:"string",required:false,desc:"Label selector to filter pods"}] },
        { id: 1, name: "scale_deployment", riskLevel: "critical", description: "Scale a Kubernetes deployment to a specified number of replicas.", params: [{name:"deployment",type:"string",required:true,desc:"Deployment name"},{name:"replicas",type:"number",required:true,desc:"Desired replica count"},{name:"namespace",type:"string",required:false,desc:"Target namespace"}] },
        { id: 2, name: "get_logs",         riskLevel: "medium",   description: "Stream or fetch logs from a specific pod or container.", params: [{name:"pod_name",type:"string",required:true,desc:"Pod name"},{name:"container",type:"string",required:false,desc:"Container name"},{name:"tail_lines",type:"number",required:false,desc:"Number of log lines to return"}] },
        { id: 3, name: "exec_command",     riskLevel: "critical", description: "Execute a shell command inside a running container. Equivalent to kubectl exec.", params: [{name:"pod_name",type:"string",required:true,desc:"Target pod"},{name:"command",type:"array",required:true,desc:"Command array e.g. ['sh','-c','ls /etc']"}] },
        { id: 4, name: "delete_resource",  riskLevel: "critical", description: "Delete a Kubernetes resource by name and kind.", params: [{name:"kind",type:"string",required:true,desc:"Resource kind: Pod, Deployment, Service..."},{name:"name",type:"string",required:true,desc:"Resource name"},{name:"namespace",type:"string",required:false,desc:"Target namespace"}] },
    ],
    "aws-mcp": [
        { id: 0, name: "list_buckets",      riskLevel: "low",      description: "List all S3 buckets in the AWS account.", params: [] },
        { id: 1, name: "get_object",        riskLevel: "high",     description: "Download the contents of an object from S3.", params: [{name:"bucket",type:"string",required:true,desc:"S3 bucket name"},{name:"key",type:"string",required:true,desc:"Object key within bucket"}] },
        { id: 2, name: "invoke_function",   riskLevel: "critical", description: "Invoke an AWS Lambda function synchronously or asynchronously.", params: [{name:"function_name",type:"string",required:true,desc:"Lambda function name or ARN"},{name:"payload",type:"object",required:false,desc:"JSON event payload"},{name:"invocation_type",type:"string",required:false,desc:"RequestResponse or Event"}] },
        { id: 3, name: "describe_instances", riskLevel: "medium",  description: "Describe EC2 instances with state, instance type, and network config.", params: [{name:"instance_ids",type:"array",required:false,desc:"List of EC2 instance IDs"},{name:"filters",type:"array",required:false,desc:"EC2 filter objects"}] },
        { id: 4, name: "put_secret",        riskLevel: "critical", description: "Create or update a secret in AWS Secrets Manager.", params: [{name:"secret_name",type:"string",required:true,desc:"Secret name or ARN"},{name:"secret_value",type:"string",required:true,desc:"New secret value"}] },
    ],
    "github-mcp": [
        { id: 0, name: "list_repos",         riskLevel: "low",    description: "List repositories accessible to the token, including private repos.", params: [{name:"org",type:"string",required:false,desc:"Organization name"},{name:"visibility",type:"string",required:false,desc:"all, public, or private"}] },
        { id: 1, name: "read_file",           riskLevel: "medium", description: "Read the raw contents of a file at a specific path and branch.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"path",type:"string",required:true,desc:"File path"},{name:"ref",type:"string",required:false,desc:"Branch or commit SHA"}] },
        { id: 2, name: "create_issue",        riskLevel: "medium", description: "Create a new GitHub issue with title, body, and labels.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"title",type:"string",required:true,desc:"Issue title"},{name:"body",type:"string",required:false,desc:"Markdown body"}] },
        { id: 3, name: "merge_pull_request",  riskLevel: "high",   description: "Merge an open pull request using squash, merge, or rebase.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"pull_number",type:"number",required:true,desc:"PR number"},{name:"merge_method",type:"string",required:false,desc:"merge, squash, or rebase"}] },
    ],
    "slack-mcp": [
        { id: 0, name: "send_message",  riskLevel: "high",   description: "Send a message to a Slack channel or DM.", params: [{name:"channel",type:"string",required:true,desc:"Channel ID or #name"},{name:"text",type:"string",required:true,desc:"Message text (supports mrkdwn)"},{name:"thread_ts",type:"string",required:false,desc:"Parent message timestamp for threading"}] },
        { id: 1, name: "get_history",   riskLevel: "medium", description: "Retrieve message history of a Slack channel.", params: [{name:"channel",type:"string",required:true,desc:"Channel ID"},{name:"limit",type:"number",required:false,desc:"Max messages (default 100)"}] },
        { id: 2, name: "list_channels", riskLevel: "low",    description: "List channels the bot has access to.", params: [{name:"types",type:"string",required:false,desc:"public_channel, private_channel, im, mpim"}] },
        { id: 3, name: "upload_file",   riskLevel: "high",   description: "Upload a file to a Slack channel.", params: [{name:"channel",type:"string",required:true,desc:"Target channel"},{name:"content",type:"string",required:true,desc:"File content"},{name:"filename",type:"string",required:true,desc:"Display filename"}] },
    ],
    "jira-mcp": [
        { id: 0, name: "list_issues",   riskLevel: "low",    description: "Query JIRA issues using JQL with pagination.", params: [{name:"jql",type:"string",required:true,desc:"JQL query string"},{name:"max_results",type:"number",required:false,desc:"Max issues (default 50)"}] },
        { id: 1, name: "create_issue",  riskLevel: "medium", description: "Create a new JIRA issue.", params: [{name:"project_key",type:"string",required:true,desc:"JIRA project key"},{name:"issue_type",type:"string",required:true,desc:"Bug, Task, Story, Epic"},{name:"summary",type:"string",required:true,desc:"Issue title"}] },
        { id: 2, name: "update_status", riskLevel: "medium", description: "Transition a JIRA issue to a new workflow status.", params: [{name:"issue_key",type:"string",required:true,desc:"JIRA issue key (ENG-123)"},{name:"transition",type:"string",required:true,desc:"Transition name"}] },
        { id: 3, name: "assign_issue",  riskLevel: "low",    description: "Assign a JIRA issue to a specific user.", params: [{name:"issue_key",type:"string",required:true,desc:"JIRA issue key"},{name:"account_id",type:"string",required:true,desc:"Atlassian account ID"}] },
    ],
    "notion-mcp": [
        { id: 0, name: "list_pages",  riskLevel: "low",    description: "List pages accessible to the integration.", params: [{name:"page_size",type:"number",required:false,desc:"Results per page (max 100)"}] },
        { id: 1, name: "create_page", riskLevel: "medium", description: "Create a new Notion page under a parent.", params: [{name:"parent_id",type:"string",required:true,desc:"Parent page or database ID"},{name:"title",type:"string",required:true,desc:"Page title"}] },
        { id: 2, name: "update_page", riskLevel: "medium", description: "Update properties or content of an existing page.", params: [{name:"page_id",type:"string",required:true,desc:"Notion page ID"},{name:"properties",type:"object",required:false,desc:"Property values to update"}] },
        { id: 3, name: "search",      riskLevel: "low",    description: "Full-text search across the Notion workspace.", params: [{name:"query",type:"string",required:true,desc:"Search text"}] },
    ],
    "salesforce-mcp": [
        { id: 0, name: "query_records",      riskLevel: "high",   description: "Execute a SOQL query against Salesforce objects.", params: [{name:"soql",type:"string",required:true,desc:"SOQL query string"}] },
        { id: 1, name: "create_opportunity", riskLevel: "medium", description: "Create a new Salesforce Opportunity.", params: [{name:"name",type:"string",required:true,desc:"Opportunity name"},{name:"account_id",type:"string",required:true,desc:"Parent Account ID"},{name:"stage",type:"string",required:true,desc:"Sales stage"},{name:"close_date",type:"string",required:true,desc:"Expected close (YYYY-MM-DD)"}] },
        { id: 2, name: "update_contact",     riskLevel: "high",   description: "Update fields on an existing Contact record.", params: [{name:"contact_id",type:"string",required:true,desc:"Salesforce Contact ID"},{name:"fields",type:"object",required:true,desc:"Key-value field updates"}] },
    ],
    "sharepoint-mcp": [
        { id: 0, name: "list_files",    riskLevel: "low",  description: "List files in a SharePoint document library.", params: [{name:"site_url",type:"string",required:true,desc:"SharePoint site URL"},{name:"library",type:"string",required:true,desc:"Document library name"}] },
        { id: 1, name: "download_file", riskLevel: "high", description: "Download contents of a SharePoint file.", params: [{name:"site_url",type:"string",required:true,desc:"Site URL"},{name:"file_path",type:"string",required:true,desc:"Server-relative file path"}] },
        { id: 2, name: "upload_file",   riskLevel: "high", description: "Upload a file to a document library.", params: [{name:"site_url",type:"string",required:true,desc:"Site URL"},{name:"library",type:"string",required:true,desc:"Target library"},{name:"filename",type:"string",required:true,desc:"File name"},{name:"content",type:"string",required:true,desc:"File content (base64 for binary)"}] },
    ],
    "linear-mcp": [
        { id: 0, name: "list_issues",   riskLevel: "low",    description: "List Linear issues with filtering by team, status, or assignee.", params: [{name:"team_id",type:"string",required:false,desc:"Team ID"},{name:"state",type:"string",required:false,desc:"Issue state"}] },
        { id: 1, name: "create_issue",  riskLevel: "medium", description: "Create a new issue in a Linear team.", params: [{name:"team_id",type:"string",required:true,desc:"Target team ID"},{name:"title",type:"string",required:true,desc:"Issue title"}] },
        { id: 2, name: "update_status", riskLevel: "low",    description: "Move a Linear issue to a different workflow state.", params: [{name:"issue_id",type:"string",required:true,desc:"Linear issue ID"},{name:"state_id",type:"string",required:true,desc:"Target state ID"}] },
    ],
    "quickbooks-mcp": [
        { id: 0, name: "list_invoices",     riskLevel: "high",     description: "List invoices with filtering by status or date.", params: [{name:"status",type:"string",required:false,desc:"Draft, Pending, Voided"},{name:"start_date",type:"string",required:false,desc:"Start date (YYYY-MM-DD)"}] },
        { id: 1, name: "create_invoice",    riskLevel: "critical", description: "Create a new invoice for a customer.", params: [{name:"customer_ref",type:"object",required:true,desc:"Customer reference { value: id }"},{name:"line_items",type:"array",required:true,desc:"Line items with amount"}] },
        { id: 2, name: "get_balance_sheet", riskLevel: "critical", description: "Retrieve balance sheet summarizing assets, liabilities, equity.", params: [{name:"as_of_date",type:"string",required:true,desc:"Balance sheet date (YYYY-MM-DD)"}] },
        { id: 3, name: "list_transactions", riskLevel: "critical", description: "List all financial transactions including payments and expenses.", params: [{name:"start_date",type:"string",required:true,desc:"Range start"},{name:"end_date",type:"string",required:true,desc:"Range end"}] },
    ],
    "sap-mcp": [
        { id: 0, name: "get_inventory",        riskLevel: "medium",   description: "Query current inventory levels for materials across storage locations.", params: [{name:"material_id",type:"string",required:false,desc:"Material number"},{name:"plant",type:"string",required:false,desc:"Plant/facility code"}] },
        { id: 1, name: "create_purchase_order", riskLevel: "critical", description: "Create a new purchase order with vendor, materials, and pricing.", params: [{name:"vendor",type:"string",required:true,desc:"Vendor account number"},{name:"items",type:"array",required:true,desc:"Line items: material, quantity, price"}] },
        { id: 2, name: "approve_expense",       riskLevel: "critical", description: "Approve a pending expense report in SAP FI.", params: [{name:"expense_id",type:"string",required:true,desc:"Expense report ID"},{name:"comment",type:"string",required:false,desc:"Approval comment"}] },
        { id: 3, name: "get_financial_report",  riskLevel: "critical", description: "Generate a P&L or cost center report for a given period.", params: [{name:"report_type",type:"string",required:true,desc:"P&L, CostCenter, BalanceSheet"},{name:"fiscal_year",type:"number",required:true,desc:"SAP fiscal year"}] },
    ],
    "databricks-mcp": [
        { id: 0, name: "run_notebook",   riskLevel: "critical", description: "Execute a Databricks notebook with optional parameters.", params: [{name:"path",type:"string",required:true,desc:"Notebook workspace path"},{name:"cluster_id",type:"string",required:false,desc:"Target cluster ID"},{name:"parameters",type:"object",required:false,desc:"Widget parameter overrides"}] },
        { id: 1, name: "query_table",    riskLevel: "high",     description: "Run a Spark SQL or Delta Lake query against a registered table.", params: [{name:"sql",type:"string",required:true,desc:"SQL query string"},{name:"limit",type:"number",required:false,desc:"Row limit (default 1000)"}] },
        { id: 2, name: "list_clusters",  riskLevel: "low",      description: "List Databricks compute clusters with their state.", params: [{name:"filter_by",type:"string",required:false,desc:"all, running, terminated"}] },
        { id: 3, name: "get_job_status", riskLevel: "low",      description: "Get the status and output of a Databricks workflow job run.", params: [{name:"run_id",type:"number",required:true,desc:"Job run ID"}] },
    ],
    "xcode-mcp": [
        { id: 0, name: "build_project",  riskLevel: "medium", description: "Build an Xcode project with a specified scheme and configuration.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Build scheme"}] },
        { id: 1, name: "run_tests",      riskLevel: "low",    description: "Execute the unit and UI test suite for a scheme.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Test scheme"}] },
        { id: 2, name: "archive_app",    riskLevel: "medium", description: "Archive an iOS/macOS app for App Store or ad-hoc distribution.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Archive scheme"}] },
        { id: 3, name: "get_build_logs", riskLevel: "low",    description: "Retrieve full build log output for the most recent build.", params: [] },
    ],
};

export const MCP_RESOURCES = {
    "razorpay-stdio": [
        { id: 0, uri: "razorpay://transactions", name: "transactions", description: "Real-time transaction feed" },
        { id: 1, uri: "razorpay://customers",    name: "customers",    description: "Customer registry" },
        { id: 2, uri: "razorpay://settlements",  name: "settlements",  description: "Settlement reports" },
    ],
    "mcp.akto.io": [
        { id: 0, uri: "akto://collections",     name: "collections",    description: "API collection registry" },
        { id: 1, uri: "akto://vulnerabilities", name: "vulnerabilities", description: "Active vulnerability list" },
    ],
    "postgres-mcp": [
        { id: 0, uri: "postgres://schema", name: "schema", description: "Full database schema" },
        { id: 1, uri: "postgres://tables", name: "tables", description: "Table and view listing" },
    ],
    "kubernetes-mcp": [
        { id: 0, uri: "k8s://namespaces", name: "namespaces", description: "All cluster namespaces" },
        { id: 1, uri: "k8s://nodes",      name: "nodes",      description: "Node status and capacity" },
        { id: 2, uri: "k8s://events",     name: "events",     description: "Recent cluster events" },
    ],
    "aws-mcp": [
        { id: 0, uri: "aws://s3/buckets",    name: "s3-buckets",   description: "All S3 buckets" },
        { id: 1, uri: "aws://ec2/instances", name: "ec2-instances", description: "EC2 instance inventory" },
        { id: 2, uri: "aws://iam/policies",  name: "iam-policies",  description: "IAM policy documents" },
    ],
    "github-mcp": [
        { id: 0, uri: "github://repos",      name: "repositories", description: "Accessible repositories" },
        { id: 1, uri: "github://org/members", name: "org-members",  description: "Organization members" },
    ],
    "slack-mcp": [
        { id: 0, uri: "slack://channels", name: "channels", description: "All accessible channels" },
        { id: 1, uri: "slack://users",    name: "users",    description: "Workspace user directory" },
    ],
    "jira-mcp": [
        { id: 0, uri: "jira://projects", name: "projects", description: "All JIRA projects" },
        { id: 1, uri: "jira://boards",   name: "boards",   description: "Agile boards" },
    ],
    "notion-mcp": [
        { id: 0, uri: "notion://pages",     name: "pages",     description: "All accessible pages" },
        { id: 1, uri: "notion://databases", name: "databases", description: "All Notion databases" },
    ],
    "databricks-mcp": [
        { id: 0, uri: "databricks://catalogs", name: "catalogs", description: "Unity Catalog listing" },
        { id: 1, uri: "databricks://jobs",     name: "jobs",     description: "Workflow job registry" },
        { id: 2, uri: "databricks://clusters", name: "clusters", description: "Compute cluster states" },
    ],
    "quickbooks-mcp": [
        { id: 0, uri: "qbo://accounts",     name: "chart-of-accounts", description: "Chart of accounts" },
        { id: 1, uri: "qbo://reports/pnl",  name: "pnl-report",        description: "Profit & loss report" },
    ],
};

export const MCP_PROMPTS = {
    "razorpay-stdio": [
        { id: 0, name: "analyze-dispute",    description: "Analyze a payment dispute and recommend resolution", args: ["payment_id", "dispute_reason"] },
        { id: 1, name: "reconcile-report",   description: "Reconcile settlements against ledger for a date range", args: ["start_date", "end_date"] },
        { id: 2, name: "fraud-risk-summary", description: "Summarize fraud risk signals for a customer account", args: ["customer_id"] },
    ],
    "mcp.akto.io": [
        { id: 0, name: "security-scan-report", description: "Generate a security report for an API collection", args: ["collection_id"] },
        { id: 1, name: "vulnerability-triage", description: "Triage and prioritize vulnerabilities by risk", args: ["collection_id", "severity"] },
    ],
    "postgres-mcp": [
        { id: 0, name: "explain-slow-query", description: "Analyze a query and suggest indexes or rewrites", args: ["query"] },
        { id: 1, name: "schema-docs",        description: "Generate documentation for a table schema", args: ["table_name"] },
    ],
    "kubernetes-mcp": [
        { id: 0, name: "incident-runbook",  description: "Generate a runbook for a failing workload", args: ["resource_name", "namespace"] },
        { id: 1, name: "capacity-analysis", description: "Analyze cluster resource utilization and headroom", args: [] },
    ],
    "aws-mcp": [
        { id: 0, name: "cost-anomaly",    description: "Identify unexpected cost spikes in AWS spend", args: ["start_date", "end_date"] },
        { id: 1, name: "security-review", description: "Review S3 and IAM config against CIS benchmarks", args: [] },
    ],
    "github-mcp": [
        { id: 0, name: "pr-summary",       description: "Summarize code changes in a pull request", args: ["owner", "repo", "pull_number"] },
        { id: 1, name: "dependency-audit", description: "Audit dependencies for known CVEs", args: ["owner", "repo"] },
    ],
    "slack-mcp": [
        { id: 0, name: "channel-summary", description: "Summarize recent activity in a channel", args: ["channel", "hours"] },
        { id: 1, name: "action-items",    description: "Extract action items from a thread", args: ["channel", "thread_ts"] },
    ],
    "databricks-mcp": [
        { id: 0, name: "notebook-review",     description: "Review a notebook for best practices and performance", args: ["path"] },
        { id: 1, name: "data-quality-check",  description: "Run data quality checks on a Delta table", args: ["table_name"] },
    ],
};

export const TOOL_VIOLATIONS = {
    "create_payment_link": 2, "refund_payment": 1, "generate_test_payload": 1,
    "run_notebook": 4, "query_table": 1, "execute_query": 2,
    "exec_command": 1, "scale_deployment": 1, "merge_pull_request": 1,
};

export function generateResourceSample(resource) {
    return {
        message: JSON.stringify({
            method: "GET",
            path: "/mcp/resources/read",
            requestHeaders: JSON.stringify({ "content-type": "application/json", "authorization": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAwMSJ9", "x-mcp-session": "sess_8f2a91b4c3d1" }),
            requestPayload: JSON.stringify({ uri: resource.uri }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json", "x-mcp-request-id": "req_res_7c3d" }),
            responsePayload: JSON.stringify({ contents: [{ uri: resource.uri, mimeType: "application/json", text: JSON.stringify({ id: "obj_001", name: resource.name, updatedAt: "2026-05-26T08:00:00Z" }) }] }),
        }),
    };
}

export function generatePromptSample(prompt) {
    const args = {};
    (prompt.args || []).forEach(a => { args[a] = `example_${a.replace(/_/g, "-")}`; });
    return {
        message: JSON.stringify({
            method: "POST",
            path: "/mcp/prompts/get",
            requestHeaders: JSON.stringify({ "content-type": "application/json", "authorization": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAwMSJ9", "x-mcp-session": "sess_8f2a91b4c3d1" }),
            requestPayload: JSON.stringify({ name: prompt.name, arguments: args }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json", "x-mcp-request-id": "req_prm_9a2b" }),
            responsePayload: JSON.stringify({ description: prompt.description, messages: [{ role: "user", content: { type: "text", text: `Execute: ${prompt.description}` } }] }),
        }),
    };
}

export function generateToolSample(tool) {
    const args = {};
    (tool.params || []).forEach(p => {
        if (!p.required) return;
        if (p.type === "string")       args[p.name] = `example_${p.name.replace(/_/g, "-")}`;
        else if (p.type === "number")  args[p.name] = 42;
        else if (p.type === "boolean") args[p.name] = true;
        else if (p.type === "array")   args[p.name] = ["item_1", "item_2"];
        else if (p.type === "object")  args[p.name] = { key: "value" };
    });

    const responsePayload = (() => {
        const n = tool.name;
        if (n.startsWith("list_") || n.startsWith("get_"))
            return { result: [{ id: "obj_001", name: "Example item", status: "active", createdAt: "2026-05-01T10:00:00Z" }], total: 1 };
        if (n.startsWith("create_") || n.startsWith("add_"))
            return { id: `id_${Math.random().toString(36).slice(2, 9)}`, status: "created", createdAt: "2026-05-25T08:30:00Z" };
        if (n.startsWith("update_") || n.startsWith("approve_"))
            return { id: "id_abc123", status: "updated", updatedAt: "2026-05-25T08:30:00Z" };
        if (n.startsWith("delete_") || n.startsWith("refund_"))
            return { success: true, id: "id_abc123", status: "deleted" };
        if (n.startsWith("execute_") || n.startsWith("run_"))
            return { success: true, rows: [{ id: 1, result: "processed" }], executionTime: "142ms" };
        if (n.startsWith("export_"))
            return { fileUrl: "https://storage.example.com/exports/report_20260525.csv", size: 48200, rows: 512 };
        return { success: true, result: "Operation completed successfully." };
    })();

    return {
        message: JSON.stringify({
            method: "POST",
            path: "/mcp/tools/call",
            requestHeaders: JSON.stringify({
                "content-type": "application/json",
                "authorization": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAwMSJ9",
                "x-mcp-session": "sess_8f2a91b4c3d1",
            }),
            requestPayload: JSON.stringify({ name: tool.name, arguments: args }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json", "x-mcp-request-id": "req_7c3d12e9a4b5" }),
            responsePayload: JSON.stringify({ content: [{ type: "text", text: JSON.stringify(responsePayload, null, 2) }], isError: false }),
        }),
    };
}

// ─── Agent risk data ──────────────────────────────────────────────────────────

export const AGENT_RISK_DATA = {
    "NYC-JDOE-MAC01/cursor-cli":          { riskScore: 1.2, violations: null },
    "NYC-JDOE-MAC01/cursor-code":         { riskScore: 3.8, violations: { critical:0, high:1, medium:0, low:0 } },
    "NYC-JDOE-MAC01/mcp-akto":            { riskScore: 4.1, violations: { critical:1, high:0, medium:0, low:0 } },
    "NYC-JDOE-MAC01/chatgpt":             { riskScore: 2.5, violations: null },
    "NYC-JDOE-MAC01/razorpay-stdio":      { riskScore: 4.5, violations: { critical:1, high:1, medium:0, low:0 } },
    "NYC-JDOE-MAC01/gemini":              { riskScore: 1.8, violations: null },
    "NYC-JDOE-WIN11/copilot365-jdoe":     { riskScore: 2.1, violations: null },
    "NYC-JDOE-WIN11/mcp-github-jdoe":     { riskScore: 3.2, violations: null },
    "NYC-JDOE-WIN11/gpt4-jdoe":           { riskScore: 1.9, violations: null },
    "BER-TSMITH-MAC02/vscode":            { riskScore: 2.3, violations: { critical:0, high:1, medium:0, low:0 } },
    "BER-TSMITH-MAC02/github-copilot":    { riskScore: 1.9, violations: null },
    "BER-TSMITH-MAC02/mcp-github":        { riskScore: 3.2, violations: { critical:1, high:0, medium:1, low:0 } },
    "SF-MWILSON-WIN10/copilot365":        { riskScore: 2.1, violations: null },
    "SF-MWILSON-WIN10/teams-bot":         { riskScore: 1.8, violations: null },
    "SF-MWILSON-WIN10/mcp-sharepoint":    { riskScore: 3.5, violations: { critical:2, high:2, medium:0, low:0 } },
    "SF-MWILSON-WIN10/gpt4":              { riskScore: 2.0, violations: null },
    "SF-MWILSON-MAC01/claude-mwilson":    { riskScore: 1.5, violations: null },
    "SF-MWILSON-MAC01/mcp-notion-mw":     { riskScore: 2.1, violations: null },
    "BER-DWILSON-MAC02/claude-desktop":   { riskScore: 2.8, violations: null },
    "BER-DWILSON-MAC02/mcp-slack":        { riskScore: 2.4, violations: null },
    "BER-DWILSON-MAC02/mcp-jira":         { riskScore: 2.0, violations: { critical:0, high:1, medium:0, low:0 } },
    "BER-DWILSON-MAC02/claude-3":         { riskScore: 1.6, violations: null },
    "SF-STAYLOR-MAC01/cursor-ai":         { riskScore: 3.1, violations: { critical:0, high:0, medium:0, low:3 } },
    "SF-STAYLOR-MAC01/mcp-k8s":           { riskScore: 4.2, violations: { critical:0, high:0, medium:0, low:2 } },
    "SF-STAYLOR-MAC01/mcp-aws":           { riskScore: 4.3, violations: null },
    "SF-STAYLOR-MAC01/gpt4-mini":         { riskScore: 1.7, violations: null },
    "SF-LTHOMAS-MAC02/playwright-ai":     { riskScore: 2.8, violations: { critical:1, high:2, medium:1, low:0 } },
    "SF-LTHOMAS-MAC02/gemini-pro":        { riskScore: 1.6, violations: null },
    "SF-RCLARK-MAC01/cursor-ai2":         { riskScore: 2.5, violations: { critical:0, high:1, medium:0, low:0 } },
    "SF-RCLARK-MAC01/mcp-xcode":          { riskScore: 2.9, violations: null },
    "SF-RCLARK-MAC01/claude-haiku":       { riskScore: 1.4, violations: null },
    "SF-JLEWIS-WIN10/copilot-data":       { riskScore: 2.4, violations: null },
    "SF-JLEWIS-WIN10/mcp-databricks":     { riskScore: 4.0, violations: { critical:4, high:1, medium:0, low:0 } },
    "SF-JLEWIS-WIN10/gpt4-data":          { riskScore: 2.1, violations: null },
    "SF-JLEWIS-LIN01/jupyter-jlewis":     { riskScore: 2.2, violations: null },
    "SF-JLEWIS-LIN01/ollama-jlewis":      { riskScore: 1.3, violations: null },
    "SF-JLEWIS-LIN01/mcp-pg-jlewis":      { riskScore: 3.4, violations: { critical:0, high:1, medium:0, low:0 } },
    "SF-JLEWIS-MAC01/cursor-jlewis":      { riskScore: 1.9, violations: null },
    "SF-JLEWIS-MAC01/claude-jlewis":      { riskScore: 1.4, violations: null },
    "SF-WHALL-WIN10/copilot365-pm":       { riskScore: 2.0, violations: null },
    "SF-WHALL-WIN10/mcp-notion":          { riskScore: 2.2, violations: { critical:0, high:0, medium:1, low:0 } },
    "SF-PYOUNG-WIN10/copilot365-fin":     { riskScore: 2.3, violations: null },
    "SF-PYOUNG-WIN10/mcp-sap":            { riskScore: 3.9, violations: { critical:0, high:0, medium:1, low:1 } },
    "SF-CKING-MAC01/claude-fin":          { riskScore: 2.8, violations: null },
    "SF-CKING-MAC01/mcp-quickbooks":      { riskScore: 4.1, violations: { critical:0, high:0, medium:1, low:1 } },
    "NYC-JANDERSON-MAC01/cursor-vp":      { riskScore: 2.5, violations: null },
    "NYC-JANDERSON-MAC01/mcp-linear":     { riskScore: 2.1, violations: { critical:0, high:0, medium:1, low:0 } },
    "NYC-JANDERSON-MAC01/gpt4-vp":        { riskScore: 1.9, violations: null },
    "LON-RJOHNSON-WIN11/copilot-win":     { riskScore: 2.2, violations: null },
    "LON-RJOHNSON-WIN11/mcp-crm":         { riskScore: 3.0, violations: { critical:0, high:1, medium:2, low:4 } },
    "TKY-AMATSUDA-LIN01/jupyter-ai":      { riskScore: 1.8, violations: null },
    "TKY-AMATSUDA-LIN01/ollama":          { riskScore: 1.2, violations: null },
    "TKY-AMATSUDA-LIN01/mcp-postgres":    { riskScore: 2.8, violations: { critical:0, high:0, medium:1, low:1 } },
};

// ─── Device violation templates & generator ───────────────────────────────────

export const VIOLATION_TEMPLATES = {
    critical: [
        { title: "Unauthorized API Key Exposure",       description: "Agent transmitted a plaintext API key in request payload to an external service granting write access to production infrastructure." },
        { title: "PII Sent to External LLM",            description: "Customer PII (email, phone) was included in a prompt sent to a third-party LLM without redaction or consent." },
        { title: "Financial Transaction Without Authorization", description: "Agent invoked payment API to initiate a transaction without explicit user confirmation." },
        { title: "Credential Exfiltration Attempt",     description: "Agent attempted to read system credential store and forward contents to an external endpoint." },
    ],
    high: [
        { title: "Excessive Database Query Scope",      description: "Agent executed a full-table scan on `users` (1.2M rows) without WHERE clause, violating least privilege." },
        { title: "Shadow IT: Unauthorized SaaS Auth",   description: "Personal account credentials used to authenticate an agent session. Corporate data may have been uploaded externally." },
        { title: "Prompt Injection Detected",           description: "A prompt injection payload was detected in tool call arguments. Agent may have been manipulated by adversarial content." },
        { title: "Overprivileged Tool Invocation",      description: "Agent invoked `admin_delete_user` outside its stated purpose. No business justification was logged." },
    ],
    medium: [
        { title: "Unusual Invocation Pattern",          description: "Agent invoked the same tool 47 times within 2 minutes, consistent with automated enumeration or data scraping." },
        { title: "Missing Rate Limit Compliance",       description: "Agent continued making API calls after receiving HTTP 429 responses, bypassing rate limiting." },
    ],
    low: [
        { title: "Deprecated Tool Version Used",        description: "Agent is calling a deprecated tool version. Upgrade for improved security controls." },
        { title: "Verbose Logging of Sensitive Fields", description: "Debug logs include response payloads containing authentication tokens." },
    ],
};

export const REL_TIMES = ["2m ago", "17m ago", "1h ago", "3h ago", "6h ago", "12h ago", "1d ago", "2d ago"];

export function generateViolations(device, agents) {
    const rows = [];
    let t = 0;
    const add = (sev, idx) => {
        const tpls = VIOLATION_TEMPLATES[sev] || VIOLATION_TEMPLATES.low;
        const tpl = tpls[idx % tpls.length];
        const ag = agents[(idx * 2) % Math.max(agents.length, 1)];
        rows.push({ id: rows.length, severity: sev, title: tpl.title, description: tpl.description, agent: ag?.endpoint || "Unknown", agentType: ag?.type || "AI Agent", time: REL_TIMES[t++ % REL_TIMES.length] });
    };
    for (let i = 0; i < (device.violations?.critical || 0); i++) add("critical", i);
    for (let i = 0; i < (device.violations?.high    || 0); i++) add("high",     i);
    for (let i = 0; i < (device.violations?.medium  || 0); i++) add("medium",   i);
    for (let i = 0; i < (device.violations?.low     || 0); i++) add("low",      i);
    return rows;
}

// ─── Device endpoints page data ───────────────────────────────────────────────

export const STAT_SPARKLINES = {
    endpoints: [4200,4500,4800,5100,5300,5500,5700,5900,6000,6100,6350,6403],
    users:     [2800,3000,3200,3400,3500,3700,3800,3900,4000,4050,4150,4203],
    violations:[800, 850, 900, 950,1000,1050,1100,1150,1200,1280,1370,1400],
};

export const OS_TREND = {
    mac:     [2500,2700,2900,3100,3200,3300,3500,3600,3700,3800,3900,4000],
    windows: [1200,1250,1300,1350,1400,1450,1500,1550,1600,1650,1700,1800],
    linux:   [500, 550, 600, 650, 700, 750, 700, 750, 700, 650, 750, 603],
};

export const VIOLATIONS_BY_SEVERITY = [
    { name: "Critical", y: 450, color: "#DC2626" },
    { name: "High",     y: 380, color: "#F97316" },
    { name: "Medium",   y: 320, color: "#EAB308" },
    { name: "Low",      y: 250, color: "#D1D5DB" },
];

export const DEVICE_FLAT_DATA = [
    { path: ["NYC-JDOE-MAC01"], endpoint: "NYC-JDOE-MAC01", os: "mac", userCount: 5, riskScore: 4.8, username: "John Doe", group: "Engineering", role: "Software Engineer", violations: { critical:2, high:0, medium:0, low:0 }, lastTraffic: "2h ago", hasPersonalAccount: true },
    { path: ["NYC-JDOE-WIN11"], endpoint: "NYC-JDOE-WIN11", os: "windows", userCount: 2, riskScore: 3.2, username: "John Doe", group: "Engineering", role: "Software Engineer", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "1d ago" },
    { path: ["NYC-JDOE-WIN11","copilot365-jdoe"], endpoint: "Microsoft Copilot 365", type: "AI Agent" },
    { path: ["NYC-JDOE-WIN11","mcp-github-jdoe"], endpoint: "github-mcp",            type: "MCP Server" },
    { path: ["NYC-JDOE-WIN11","gpt4-jdoe"],       endpoint: "GPT-4o",                type: "LLM" },
    { path: ["NYC-JDOE-MAC01","cursor-cli"],     endpoint: "Cursor CLI",             type: "AI Agent",   skillCount: 1   },
    { path: ["NYC-JDOE-MAC01","cursor-code"],    endpoint: "Cursor AI",              type: "AI Agent",   skillCount: 34  },
    { path: ["NYC-JDOE-MAC01","mcp-akto"],       endpoint: "mcp.akto.io",            type: "MCP Server"                  },
    { path: ["NYC-JDOE-MAC01","chatgpt"],        endpoint: "ChatGPT",                type: "AI Agent"                    },
    { path: ["NYC-JDOE-MAC01","razorpay-stdio"], endpoint: "razorpay-stdio",         type: "MCP Server"                  },
    { path: ["NYC-JDOE-MAC01","gemini"],         endpoint: "gemini-pro",             type: "LLM"                         },

    { path: ["BER-TSMITH-MAC02"], endpoint: "BER-TSMITH-MAC02", os: "mac", userCount: 23, riskScore: 4.7, username: "Traun Smith", group: "Engineering", role: "Frontend Developer", violations: { critical:1, high:1, medium:3, low:2 }, lastTraffic: "45m ago", hasPersonalAccount: true },
    { path: ["BER-TSMITH-MAC02","vscode"],         endpoint: "VS Code",        type: "AI Agent",   skillCount: 12 },
    { path: ["BER-TSMITH-MAC02","github-copilot"], endpoint: "GitHub Copilot", type: "AI Agent"                   },
    { path: ["BER-TSMITH-MAC02","mcp-github"],     endpoint: "github-mcp",     type: "MCP Server"                 },

    { path: ["SF-MWILSON-WIN10"], endpoint: "SF-MWILSON-WIN10", os: "windows", userCount: 23, riskScore: 4.5, username: "Mark Wilson", group: "Human Resources", role: "Lead HR", violations: { critical:2, high:4, medium:0, low:1 }, lastTraffic: "1d ago" },
    { path: ["SF-MWILSON-MAC01"], endpoint: "SF-MWILSON-MAC01", os: "mac", userCount: 1, riskScore: 2.8, username: "Mark Wilson", group: "Human Resources", role: "Lead HR", violations: { critical:0, high:0, medium:0, low:1 }, lastTraffic: "3d ago" },
    { path: ["SF-MWILSON-MAC01","claude-mwilson"], endpoint: "Claude Desktop",        type: "AI Agent"   },
    { path: ["SF-MWILSON-MAC01","mcp-notion-mw"],  endpoint: "notion-mcp",            type: "MCP Server" },
    { path: ["SF-MWILSON-WIN10","copilot365"],     endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-MWILSON-WIN10","teams-bot"],      endpoint: "Teams AI Bot",          type: "AI Agent"   },
    { path: ["SF-MWILSON-WIN10","mcp-sharepoint"], endpoint: "sharepoint-mcp",        type: "MCP Server" },
    { path: ["SF-MWILSON-WIN10","gpt4"],           endpoint: "GPT-4o",                type: "LLM"        },

    { path: ["BER-DWILSON-MAC02"], endpoint: "BER-DWILSON-MAC02", os: "mac", userCount: 10, riskScore: 4.5, username: "David Wilson", group: "Engineering", role: "Full Stack Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "3h ago" },
    { path: ["BER-DWILSON-MAC02","claude-desktop"], endpoint: "Claude Desktop",    type: "AI Agent",   skillCount: 23 },
    { path: ["BER-DWILSON-MAC02","mcp-slack"],      endpoint: "slack-mcp",         type: "MCP Server"               },
    { path: ["BER-DWILSON-MAC02","mcp-jira"],       endpoint: "jira-mcp",          type: "MCP Server"               },
    { path: ["BER-DWILSON-MAC02","claude-3"],       endpoint: "claude-3.7-sonnet", type: "LLM"                      },

    { path: ["SF-STAYLOR-MAC01"], endpoint: "SF-STAYLOR-MAC01", os: "mac", userCount: 30, riskScore: 4.5, username: "Sarah Taylor", group: "Engineering", role: "DevOps Engineer", violations: { critical:0, high:0, medium:0, low:5 }, lastTraffic: "6h ago" },
    { path: ["SF-STAYLOR-MAC01","cursor-ai"], endpoint: "Cursor AI",      type: "AI Agent",   skillCount: 88 },
    { path: ["SF-STAYLOR-MAC01","mcp-k8s"],   endpoint: "kubernetes-mcp", type: "MCP Server"               },
    { path: ["SF-STAYLOR-MAC01","mcp-aws"],   endpoint: "aws-mcp",        type: "MCP Server"               },
    { path: ["SF-STAYLOR-MAC01","gpt4-mini"], endpoint: "GPT-4o-mini",    type: "LLM"                      },

    { path: ["SF-LTHOMAS-MAC02"], endpoint: "SF-LTHOMAS-MAC02", os: "mac", userCount: 20, riskScore: 4.3, username: "Linda Thomas", group: "Engineering", role: "QA Engineer", violations: { critical:1, high:2, medium:1, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-LTHOMAS-MAC02","playwright-ai"], endpoint: "Playwright AI", type: "AI Agent", skillCount: 5 },
    { path: ["SF-LTHOMAS-MAC02","gemini-pro"],    endpoint: "gemini-pro",    type: "LLM"                   },

    { path: ["SF-RCLARK-MAC01"], endpoint: "SF-RCLARK-MAC01", os: "mac", userCount: 19, riskScore: 4.3, username: "Robert Clark", group: "Engineering", role: "Mobile App Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-RCLARK-MAC01","cursor-ai2"],   endpoint: "Cursor AI",    type: "AI Agent",   skillCount: 14 },
    { path: ["SF-RCLARK-MAC01","mcp-xcode"],    endpoint: "xcode-mcp",    type: "MCP Server"               },
    { path: ["SF-RCLARK-MAC01","claude-haiku"], endpoint: "claude-haiku", type: "LLM"                      },

    { path: ["SF-JLEWIS-WIN10"], endpoint: "SF-JLEWIS-WIN10", os: "windows", userCount: 34, riskScore: 4.3, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:4, high:1, medium:0, low:0 }, lastTraffic: "6h ago", hasPersonalAccount: true },
    { path: ["SF-JLEWIS-LIN01"], endpoint: "SF-JLEWIS-LIN01", os: "linux", userCount: 5, riskScore: 3.1, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "2d ago" },
    { path: ["SF-JLEWIS-LIN01","jupyter-jlewis"], endpoint: "Jupyter AI",   type: "AI Agent",   skillCount: 4 },
    { path: ["SF-JLEWIS-LIN01","ollama-jlewis"],  endpoint: "Ollama",       type: "LLM" },
    { path: ["SF-JLEWIS-LIN01","mcp-pg-jlewis"],  endpoint: "postgres-mcp", type: "MCP Server" },
    { path: ["SF-JLEWIS-MAC01"], endpoint: "SF-JLEWIS-MAC01", os: "mac", userCount: 3, riskScore: 2.4, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "5d ago" },
    { path: ["SF-JLEWIS-MAC01","cursor-jlewis"],  endpoint: "Cursor AI",        type: "AI Agent",   skillCount: 6 },
    { path: ["SF-JLEWIS-MAC01","claude-jlewis"],  endpoint: "claude-3.7-sonnet",type: "LLM" },
    { path: ["SF-JLEWIS-WIN10","copilot-data"],   endpoint: "GitHub Copilot",   type: "AI Agent"   },
    { path: ["SF-JLEWIS-WIN10","mcp-databricks"], endpoint: "databricks-mcp",   type: "MCP Server" },
    { path: ["SF-JLEWIS-WIN10","gpt4-data"],      endpoint: "GPT-4o",           type: "LLM"        },

    { path: ["SF-WHALL-WIN10"], endpoint: "SF-WHALL-WIN10", os: "windows", userCount: 19, riskScore: 4.1, username: "William Hall", group: "Engineering", role: "Product Manager", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-WHALL-WIN10","copilot365-pm"], endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-WHALL-WIN10","mcp-notion"],    endpoint: "notion-mcp",            type: "MCP Server" },

    { path: ["SF-PYOUNG-WIN10"], endpoint: "SF-PYOUNG-WIN10", os: "windows", userCount: 3, riskScore: 4.1, username: "Patricia Young", group: "Finance", role: "Finance Manager", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago" },
    { path: ["SF-PYOUNG-WIN10","copilot365-fin"], endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-PYOUNG-WIN10","mcp-sap"],        endpoint: "sap-mcp",               type: "MCP Server" },

    { path: ["SF-CKING-MAC01"], endpoint: "SF-CKING-MAC01", os: "mac", userCount: 1, riskScore: 4.1, username: "Charles King", group: "Finance", role: "Finance Manager", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago" },
    { path: ["SF-CKING-MAC01","claude-fin"],     endpoint: "Claude Desktop", type: "AI Agent"   },
    { path: ["SF-CKING-MAC01","mcp-quickbooks"], endpoint: "quickbooks-mcp", type: "MCP Server" },

    { path: ["NYC-JANDERSON-MAC01"], endpoint: "NYC-JANDERSON-MAC01", os: "mac", userCount: 4, riskScore: 3.8, username: "James Anderson", group: "Engineering", role: "VP of Engineering", violations: { critical:0, high:0, medium:2, low:0 }, lastTraffic: "5h ago" },
    { path: ["NYC-JANDERSON-MAC01","cursor-vp"],  endpoint: "Cursor AI",  type: "AI Agent",   skillCount: 9 },
    { path: ["NYC-JANDERSON-MAC01","mcp-linear"], endpoint: "linear-mcp", type: "MCP Server"              },
    { path: ["NYC-JANDERSON-MAC01","gpt4-vp"],    endpoint: "GPT-4o",     type: "LLM"                     },

    { path: ["LON-RJOHNSON-WIN11"], endpoint: "LON-RJOHNSON-WIN11", os: "windows", userCount: 15, riskScore: 3.8, username: "Robert Johnson", group: "Sales", role: "Sales Engineer", violations: { critical:0, high:1, medium:2, low:4 }, lastTraffic: "12h ago" },
    { path: ["LON-RJOHNSON-WIN11","copilot-win"], endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["LON-RJOHNSON-WIN11","mcp-crm"],     endpoint: "salesforce-mcp",   type: "MCP Server" },

    { path: ["TKY-AMATSUDA-LIN01"], endpoint: "TKY-AMATSUDA-LIN01", os: "linux", userCount: 8, riskScore: 2.1, username: "Akira Matsuda", group: "Data Science", role: "ML Engineer", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "2d ago" },
    { path: ["TKY-AMATSUDA-LIN01","jupyter-ai"],  endpoint: "Jupyter AI",   type: "AI Agent",   skillCount: 3 },
    { path: ["TKY-AMATSUDA-LIN01","ollama"],       endpoint: "Ollama",       type: "LLM"                     },
    { path: ["TKY-AMATSUDA-LIN01","mcp-postgres"], endpoint: "postgres-mcp", type: "MCP Server"              },
];

export const USER_FLAT_DATA = [
    { path: ["john-doe"],       username: "John Doe",       riskScore: 4.8, group: "Engineering",    role: "Software Engineer",    violations: { critical:2, high:0, medium:0, low:0 }, lastTraffic: "2h ago",  hasPersonalAccount: true },
    { path: ["traun-smith"],    username: "Traun Smith",    riskScore: 4.7, group: "Engineering",    role: "Frontend Developer",   violations: { critical:1, high:1, medium:3, low:2 }, lastTraffic: "45m ago", hasPersonalAccount: true },
    { path: ["mark-wilson"],    username: "Mark Wilson",    riskScore: 4.5, group: "Human Resources",role: "Lead HR",              violations: { critical:2, high:4, medium:0, low:1 }, lastTraffic: "1d ago"  },
    { path: ["david-wilson"],   username: "David Wilson",   riskScore: 4.5, group: "Engineering",    role: "Full Stack Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "3h ago"  },
    { path: ["sarah-taylor"],   username: "Sarah Taylor",   riskScore: 4.5, group: "Engineering",    role: "DevOps Engineer",      violations: { critical:0, high:0, medium:0, low:5 }, lastTraffic: "6h ago"  },
    { path: ["linda-thomas"],   username: "Linda Thomas",   riskScore: 4.3, group: "Engineering",    role: "QA Engineer",          violations: { critical:1, high:2, medium:1, low:0 }, lastTraffic: "6h ago"  },
    { path: ["robert-clark"],   username: "Robert Clark",   riskScore: 4.3, group: "Engineering",    role: "Mobile App Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "6h ago"  },
    { path: ["jennifer-lewis"], username: "Jennifer Lewis", riskScore: 4.3, group: "Engineering",    role: "Data Engineer",        violations: { critical:4, high:1, medium:0, low:0 }, lastTraffic: "6h ago",  hasPersonalAccount: true },
    { path: ["william-hall"],   username: "William Hall",   riskScore: 4.1, group: "Engineering",    role: "Product Manager",      violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "6h ago"  },
    { path: ["patricia-young"], username: "Patricia Young", riskScore: 4.1, group: "Finance",        role: "Finance Manager",      violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago"  },
    { path: ["charles-king"],   username: "Charles King",   riskScore: 4.1, group: "Finance",        role: "Finance Manager",      violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago"  },
    { path: ["james-anderson"], username: "James Anderson", riskScore: 3.8, group: "Engineering",    role: "VP of Engineering",    violations: { critical:0, high:0, medium:2, low:0 }, lastTraffic: "5h ago"  },
    { path: ["robert-johnson"], username: "Robert Johnson", riskScore: 3.8, group: "Sales",          role: "Sales Engineer",       violations: { critical:0, high:1, medium:2, low:4 }, lastTraffic: "12h ago" },
    { path: ["akira-matsuda"],  username: "Akira Matsuda",  riskScore: 2.1, group: "Data Science",   role: "ML Engineer",          violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "2d ago"  },
];

// Derived lookups (built from DEVICE_FLAT_DATA)
export const devicesByUsername = {};
export const deviceChildCount  = {};

DEVICE_FLAT_DATA.forEach(row => {
    if (row.path.length === 1 && row.username) {
        if (!devicesByUsername[row.username]) devicesByUsername[row.username] = [];
        devicesByUsername[row.username].push(row);
    }
    if (row.path.length === 2) {
        const id = row.path[0];
        deviceChildCount[id] = (deviceChildCount[id] || 0) + 1;
    }
});

// ─── Agentic Assets page data ─────────────────────────────────────────────────

// Rule: sum(groups[i].count) === endpointCount for every parent row.
export const AGENTIC_TREE_DATA = [
    // ── AI Agents ─────────────────────────────────────────────────────────────────
    { path: ["cursor-ai"],                      name: "Cursor AI",             type: "AI Agent",   assetTagValue: "cursor",     riskScore: 4.2, violations: { critical:1, high:2, medium:0, low:3 }, endpointCount: 88,  aiInteractions: 1200000, groups: [{ name: "Engineering", count: 65 }, { name: "DevOps", count: 23 }],                                           deviceCount: 4, lastSeen: "2m ago" },
    { path: ["cursor-ai","kubernetes-mcp"],     name: "kubernetes-mcp",        type: "MCP Server", endpoint: "kubernetes-mcp",                   riskScore: 4.2, violations: { critical:0, high:0, medium:0, low:2 } },
    { path: ["cursor-ai","aws-mcp"],            name: "aws-mcp",               type: "MCP Server", endpoint: "aws-mcp",                           riskScore: 4.3, violations: null },

    { path: ["vscode"],                         name: "VS Code",               type: "AI Agent",   assetTagValue: "vscode",     riskScore: 3.6, violations: { critical:0, high:1, medium:1, low:0 }, endpointCount: 12,  aiInteractions: 340000,  groups: [{ name: "Engineering", count: 12 }],                                                                         deviceCount: 1, lastSeen: "45m ago", skillCount: 12 },
    { path: ["vscode","github-mcp"],            name: "github-mcp",            type: "MCP Server", endpoint: "github-mcp",                        riskScore: 3.2, violations: { critical:1, high:0, medium:1, low:0 } },

    { path: ["github-copilot"],                 name: "GitHub Copilot",        type: "AI Agent",   assetTagValue: "github",     riskScore: 2.1, violations: { critical:0, high:0, medium:0, low:0 }, endpointCount: 2,   aiInteractions: 550000,  groups: [{ name: "Engineering", count: 2 }],                                                                          deviceCount: 2, lastSeen: "6h ago" },
    { path: ["github-copilot","databricks-mcp"],name: "databricks-mcp",        type: "MCP Server", endpoint: "databricks-mcp",                    riskScore: 4.0, violations: { critical:4, high:1, medium:0, low:0 } },

    { path: ["claude-desktop"],                 name: "Claude Desktop",        type: "AI Agent",   assetTagValue: "claude1",    riskScore: 3.2, violations: { critical:0, high:1, medium:0, low:1 }, endpointCount: 23,  aiInteractions: 680000,  groups: [{ name: "Engineering", count: 12 }, { name: "Finance", count: 7 }, { name: "HR", count: 4 }],               deviceCount: 3, lastSeen: "1h ago",  skillCount: 23 },
    { path: ["claude-desktop","slack-mcp"],     name: "slack-mcp",             type: "MCP Server", endpoint: "slack-mcp",                         riskScore: 2.4, violations: null },
    { path: ["claude-desktop","jira-mcp"],      name: "jira-mcp",              type: "MCP Server", endpoint: "jira-mcp",                           riskScore: 2.0, violations: { critical:0, high:1, medium:0, low:0 } },
    { path: ["claude-desktop","quickbooks-mcp"],name: "quickbooks-mcp",        type: "MCP Server", endpoint: "quickbooks-mcp",                     riskScore: 4.1, violations: { critical:0, high:0, medium:1, low:1 } },

    { path: ["copilot365"],                     name: "Microsoft Copilot 365", type: "AI Agent",   assetTagValue: "copilot",    riskScore: 2.8, violations: { critical:2, high:4, medium:1, low:1 }, endpointCount: 4,   aiInteractions: 990000,  groups: [{ name: "HR", count: 2 }, { name: "Finance", count: 1 }, { name: "Engineering", count: 1 }],               deviceCount: 4, lastSeen: "1d ago" },
    { path: ["copilot365","sharepoint-mcp"],    name: "sharepoint-mcp",        type: "MCP Server", endpoint: "sharepoint-mcp",                     riskScore: 3.5, violations: { critical:2, high:2, medium:0, low:0 } },
    { path: ["copilot365","notion-mcp"],        name: "notion-mcp",            type: "MCP Server", endpoint: "notion-mcp",                         riskScore: 2.1, violations: null },

    { path: ["chatgpt"],                        name: "ChatGPT",               type: "AI Agent",   assetTagValue: "chatgpt",    riskScore: 2.5, violations: { critical:0, high:0, medium:0, low:0 }, endpointCount: 1,   aiInteractions: 450000,  groups: [{ name: "Engineering", count: 1 }],                                                                          deviceCount: 1, lastSeen: "45m ago" },
    { path: ["chatgpt","razorpay-stdio"],       name: "razorpay-stdio",        type: "MCP Server", endpoint: "razorpay-stdio",                     riskScore: 4.5, violations: { critical:1, high:1, medium:0, low:0 } },

    { path: ["playwright-ai"],                  name: "Playwright AI",         type: "AI Agent",   assetTagValue: "playwright", riskScore: 4.3, violations: { critical:1, high:2, medium:1, low:0 }, endpointCount: 5,   aiInteractions: 120000,  groups: [{ name: "QA", count: 5 }],                                                                                   deviceCount: 1, lastSeen: "6h ago",  skillCount: 5 },

    { path: ["jupyter-ai"],                     name: "Jupyter AI",            type: "AI Agent",   assetTagValue: "jupyter",    riskScore: 2.0, violations: { critical:0, high:0, medium:1, low:1 }, endpointCount: 4,   aiInteractions: 85000,   groups: [{ name: "Data Science", count: 4 }],                                                                         deviceCount: 2, lastSeen: "2d ago",  skillCount: 4 },
    { path: ["jupyter-ai","postgres-mcp"],      name: "postgres-mcp",          type: "MCP Server", endpoint: "postgres-mcp",                       riskScore: 3.4, violations: { critical:0, high:1, medium:0, low:0 } },

    // ── Standalone MCP Servers ────────────────────────────────────────────────────
    { path: ["mcp-akto"],                       name: "mcp.akto.io",           type: "MCP Server", endpoint: "mcp.akto.io",     riskScore: 4.1, violations: { critical:1, high:0, medium:0, low:0 }, endpointCount: 5,   aiInteractions: 340000,  groups: [{ name: "Security", count: 5 }],                                                                              deviceCount: 1, lastSeen: "2h ago" },
    { path: ["linear-mcp"],                     name: "linear-mcp",            type: "MCP Server", endpoint: "linear-mcp",      riskScore: 2.1, violations: { critical:0, high:0, medium:1, low:0 }, endpointCount: 3,   aiInteractions: 95000,   groups: [{ name: "Engineering", count: 3 }],                                                                          deviceCount: 1, lastSeen: "5h ago" },
    { path: ["salesforce-mcp"],                 name: "salesforce-mcp",        type: "MCP Server", endpoint: "salesforce-mcp",  riskScore: 3.0, violations: { critical:0, high:1, medium:2, low:4 }, endpointCount: 3,   aiInteractions: 180000,  groups: [{ name: "Sales", count: 2 }, { name: "Marketing", count: 1 }],                                             deviceCount: 1, lastSeen: "12h ago" },
    { path: ["xcode-mcp"],                      name: "xcode-mcp",             type: "MCP Server", endpoint: "xcode-mcp",       riskScore: 2.9, violations: null,                                     endpointCount: 4,   aiInteractions: 62000,   groups: [{ name: "Mobile", count: 4 }],                                                                                deviceCount: 1, lastSeen: "6h ago" },
    { path: ["sap-mcp"],                        name: "sap-mcp",               type: "MCP Server", endpoint: "sap-mcp",         riskScore: 3.9, violations: { critical:0, high:0, medium:1, low:1 }, endpointCount: 4,   aiInteractions: 115000,  groups: [{ name: "Finance", count: 3 }, { name: "Operations", count: 1 }],                                          deviceCount: 1, lastSeen: "6h ago" },

    // ── LLMs — endpointCount = number of active model sessions (1 per device) ─────
    { path: ["gpt4o"],                          name: "GPT-4o",                type: "LLM",        assetTagValue: "chatgpt",    riskScore: 2.0, violations: null,                                     endpointCount: 3,   aiInteractions: 500000,  groups: [{ name: "Engineering", count: 2 }, { name: "HR", count: 1 }],             deviceCount: 3, lastSeen: "1d ago" },
    { path: ["gpt4o-mini"],                     name: "GPT-4o-mini",           type: "LLM",        assetTagValue: "chatgpt",    riskScore: 1.5, violations: null,                                     endpointCount: 1,   aiInteractions: 220000,  groups: [{ name: "Engineering", count: 1 }],                                        deviceCount: 1, lastSeen: "6h ago" },
    { path: ["claude-3-7"],                     name: "claude-3.7-sonnet",     type: "LLM",        assetTagValue: "claude",     riskScore: 1.6, violations: null,                                     endpointCount: 2,   aiInteractions: 320000,  groups: [{ name: "Engineering", count: 2 }],                                        deviceCount: 2, lastSeen: "3h ago" },
    { path: ["claude-haiku"],                   name: "claude-haiku",          type: "LLM",        assetTagValue: "claude",     riskScore: 1.4, violations: null,                                     endpointCount: 1,   aiInteractions: 140000,  groups: [{ name: "Mobile", count: 1 }],                                             deviceCount: 1, lastSeen: "6h ago" },
    { path: ["gemini-pro"],                     name: "gemini-pro",            type: "LLM",        assetTagValue: "gemini",     riskScore: 1.6, violations: null,                                     endpointCount: 2,   aiInteractions: 180000,  groups: [{ name: "QA", count: 1 }, { name: "Engineering", count: 1 }],             deviceCount: 2, lastSeen: "6h ago" },
    { path: ["ollama"],                         name: "Ollama",                type: "LLM",        riskScore: 1.3,              violations: null,                                                     endpointCount: 2,   aiInteractions: 95000,   groups: [{ name: "Data Science", count: 2 }],                                       deviceCount: 2, lastSeen: "2d ago" },

    // ── Additional AI Agents from Endpoints page ───────────────────────────────────
    { path: ["cursor-cli"],                     name: "Cursor CLI",            type: "AI Agent",   assetTagValue: "cursor",     riskScore: 2.5, violations: null,                                     endpointCount: 1,   aiInteractions: 42000,   groups: [{ name: "Engineering", count: 1 }],                                        deviceCount: 1, lastSeen: "2h ago",  skillCount: 1 },
    { path: ["teams-ai-bot"],                   name: "Teams AI Bot",          type: "AI Agent",   assetTagValue: "copilot",    riskScore: 1.8, violations: null,                                     endpointCount: 1,   aiInteractions: 310000,  groups: [{ name: "HR", count: 1 }],                                                 deviceCount: 1, lastSeen: "1d ago" },

    // ── Standalone child MCPs (also connected to parent agents) ───────────────────
    { path: ["notion-mcp"],                     name: "notion-mcp",            type: "MCP Server", endpoint: "notion-mcp",      riskScore: 2.1, violations: null,                                     endpointCount: 2,   aiInteractions: 78000,   groups: [{ name: "HR", count: 1 }, { name: "Engineering", count: 1 }],             deviceCount: 2, lastSeen: "1h ago" },
    { path: ["sharepoint-mcp"],                 name: "sharepoint-mcp",        type: "MCP Server", endpoint: "sharepoint-mcp",  riskScore: 3.5, violations: { critical:2, high:2, medium:0, low:0 }, endpointCount: 3,   aiInteractions: 190000,  groups: [{ name: "HR", count: 3 }],                                                 deviceCount: 1, lastSeen: "1d ago" },
    { path: ["quickbooks-mcp"],                 name: "quickbooks-mcp",        type: "MCP Server", endpoint: "quickbooks-mcp",  riskScore: 4.1, violations: { critical:0, high:0, medium:1, low:1 }, endpointCount: 4,   aiInteractions: 115000,  groups: [{ name: "Finance", count: 4 }],                                            deviceCount: 1, lastSeen: "6h ago" },
    { path: ["jira-mcp"],                       name: "jira-mcp",              type: "MCP Server", endpoint: "jira-mcp",        riskScore: 2.0, violations: { critical:0, high:1, medium:0, low:0 }, endpointCount: 4,   aiInteractions: 88000,   groups: [{ name: "Engineering", count: 4 }],                                        deviceCount: 1, lastSeen: "3h ago" },

    // ── Skills ────────────────────────────────────────────────────────────────────
    { path: ["skill-fetch-credentials"],        name: "Fetch Credentials",     type: "Skill",      riskScore: 4.5, violations: { critical:3, high:2, medium:0, low:0 }, endpointCount: 6,  aiInteractions: 12000,  groups: [{ name: "Engineering", count: 4 }, { name: "Security", count: 2 }],                                       deviceCount: 6, lastSeen: "2h ago" },
    { path: ["skill-run-api-scan"],             name: "Run API Scan",          type: "Skill",      riskScore: 4.0, violations: { critical:2, high:1, medium:0, low:0 }, endpointCount: 3,  aiInteractions: 28000,  groups: [{ name: "Security", count: 3 }],                                                                              deviceCount: 2, lastSeen: "30m ago" },
    { path: ["skill-query-database"],           name: "Query Database",        type: "Skill",      riskScore: 3.8, violations: { critical:0, high:2, medium:1, low:0 }, endpointCount: 9,  aiInteractions: 67000,  groups: [{ name: "Data Science", count: 5 }, { name: "Engineering", count: 4 }],                                   deviceCount: 3, lastSeen: "45m ago" },
    { path: ["skill-generate-snapshot"],        name: "Generate Snapshot",     type: "Skill",      riskScore: 3.1, violations: { critical:1, high:0, medium:0, low:0 }, endpointCount: 8,  aiInteractions: 45000,  groups: [{ name: "Engineering", count: 8 }],                                                                          deviceCount: 3, lastSeen: "1h ago" },
    { path: ["skill-deploy-service"],           name: "Deploy Service",        type: "Skill",      riskScore: 2.8, violations: { critical:0, high:1, medium:0, low:1 }, endpointCount: 5,  aiInteractions: 33000,  groups: [{ name: "Engineering", count: 5 }],                                                                          deviceCount: 2, lastSeen: "3h ago" },
    { path: ["skill-export-report"],            name: "Export Report",         type: "Skill",      riskScore: 1.8, violations: null,                                    endpointCount: 4,  aiInteractions: 18000,  groups: [{ name: "Finance", count: 2 }, { name: "Sales", count: 2 }],                                              deviceCount: 2, lastSeen: "6h ago" },
];

export const AGENTIC_FLAT_DATA = [
    // AI Agents
    { id: "cursor-ai",      name: "Cursor AI",             type: "AI Agent",   riskScore: 4.2, violations: { critical:1, high:2, medium:0, low:3 }, skillCount: 88, toolCount: 0, deviceCount: 4, lastSeen: "2m ago",   mcpServers: ["kubernetes-mcp","aws-mcp"] },
    { id: "vscode",         name: "VS Code",               type: "AI Agent",   riskScore: 3.6, violations: { critical:0, high:1, medium:1, low:0 }, skillCount: 12, toolCount: 0, deviceCount: 1, lastSeen: "45m ago",  mcpServers: ["github-mcp"] },
    { id: "github-copilot", name: "GitHub Copilot",        type: "AI Agent",   riskScore: 2.1, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 2, lastSeen: "6h ago",   mcpServers: [] },
    { id: "claude-desktop", name: "Claude Desktop",        type: "AI Agent",   riskScore: 3.2, violations: { critical:0, high:1, medium:0, low:1 }, skillCount: 23, toolCount: 0, deviceCount: 3, lastSeen: "1h ago",   mcpServers: ["slack-mcp","jira-mcp","quickbooks-mcp"] },
    { id: "copilot365",     name: "Microsoft Copilot 365", type: "AI Agent",   riskScore: 2.8, violations: { critical:2, high:4, medium:1, low:1 }, skillCount: 0,  toolCount: 0, deviceCount: 4, lastSeen: "1d ago",   mcpServers: ["sharepoint-mcp"] },
    { id: "chatgpt",        name: "ChatGPT",               type: "AI Agent",   riskScore: 2.5, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 1, lastSeen: "45m ago",  mcpServers: ["razorpay-stdio"] },
    { id: "playwright-ai",  name: "Playwright AI",         type: "AI Agent",   riskScore: 4.3, violations: { critical:1, high:2, medium:1, low:0 }, skillCount: 5,  toolCount: 0, deviceCount: 1, lastSeen: "6h ago",   mcpServers: [] },
    { id: "jupyter-ai",     name: "Jupyter AI",            type: "AI Agent",   riskScore: 2.0, violations: { critical:0, high:0, medium:1, low:1 }, skillCount: 4,  toolCount: 0, deviceCount: 2, lastSeen: "2d ago",   mcpServers: ["postgres-mcp"] },
    // MCP Servers
    { id: "mcp-akto",       name: "mcp.akto.io",           type: "MCP Server", riskScore: 4.1, violations: { critical:1, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 5, deviceCount: 1, lastSeen: "2h ago" },
    { id: "razorpay-stdio", name: "razorpay-stdio",        type: "MCP Server", riskScore: 4.5, violations: { critical:1, high:1, medium:0, low:0 }, skillCount: 0,  toolCount: 6, deviceCount: 1, lastSeen: "2h ago" },
    { id: "postgres-mcp",   name: "postgres-mcp",          type: "MCP Server", riskScore: 3.4, violations: { critical:0, high:1, medium:0, low:0 }, skillCount: 0,  toolCount: 4, deviceCount: 2, lastSeen: "2d ago" },
    { id: "github-mcp",     name: "github-mcp",            type: "MCP Server", riskScore: 3.2, violations: { critical:1, high:0, medium:1, low:0 }, skillCount: 0,  toolCount: 4, deviceCount: 2, lastSeen: "45m ago" },
    { id: "kubernetes-mcp", name: "kubernetes-mcp",        type: "MCP Server", riskScore: 4.2, violations: { critical:0, high:0, medium:0, low:2 }, skillCount: 0,  toolCount: 5, deviceCount: 1, lastSeen: "6h ago" },
    { id: "databricks-mcp", name: "databricks-mcp",        type: "MCP Server", riskScore: 4.0, violations: { critical:4, high:1, medium:0, low:0 }, skillCount: 0,  toolCount: 4, deviceCount: 1, lastSeen: "6h ago" },
    { id: "slack-mcp",      name: "slack-mcp",             type: "MCP Server", riskScore: 2.4, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 4, deviceCount: 1, lastSeen: "3h ago" },
    // LLMs
    { id: "gpt4o",          name: "GPT-4o",                type: "LLM",        riskScore: 2.0, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 3, lastSeen: "1d ago" },
    { id: "gpt4o-mini",     name: "GPT-4o-mini",           type: "LLM",        riskScore: 1.5, violations: null,                                     skillCount: 0,  toolCount: 0, deviceCount: 1, lastSeen: "6h ago" },
    { id: "claude-3-7",     name: "claude-3.7-sonnet",     type: "LLM",        riskScore: 1.6, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 2, lastSeen: "3h ago" },
    { id: "claude-haiku",   name: "claude-haiku",          type: "LLM",        riskScore: 1.4, violations: null,                                     skillCount: 0,  toolCount: 0, deviceCount: 1, lastSeen: "6h ago" },
    { id: "gemini-pro",     name: "gemini-pro",            type: "LLM",        riskScore: 1.6, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 2, lastSeen: "6h ago" },
    { id: "ollama",         name: "Ollama",                type: "LLM",        riskScore: 1.3, violations: { critical:0, high:0, medium:0, low:0 }, skillCount: 0,  toolCount: 0, deviceCount: 2, lastSeen: "2d ago" },
    // Additional AI Agents on endpoints not yet in flat data
    { id: "cursor-cli",     name: "Cursor CLI",            type: "AI Agent",   riskScore: 2.5, violations: null,                                     skillCount: 1,  toolCount: 0, deviceCount: 1, lastSeen: "2h ago",  mcpServers: ["mcp.akto.io"] },
    { id: "teams-ai-bot",   name: "Teams AI Bot",          type: "AI Agent",   riskScore: 1.8, violations: null,                                     skillCount: 0,  toolCount: 0, deviceCount: 1, lastSeen: "1d ago",  mcpServers: [] },
    // Child MCPs that need direct flyout access from DeviceFlyout
    { id: "notion-mcp",     name: "notion-mcp",            type: "MCP Server", riskScore: 2.1, violations: null,                                     skillCount: 0,  toolCount: 4, deviceCount: 2, lastSeen: "1h ago" },
    { id: "sharepoint-mcp", name: "sharepoint-mcp",        type: "MCP Server", riskScore: 3.5, violations: { critical:2, high:2, medium:0, low:0 }, skillCount: 0,  toolCount: 3, deviceCount: 1, lastSeen: "1d ago" },
    { id: "quickbooks-mcp", name: "quickbooks-mcp",        type: "MCP Server", riskScore: 4.1, violations: { critical:0, high:0, medium:1, low:1 }, skillCount: 0,  toolCount: 4, deviceCount: 1, lastSeen: "6h ago" },
    { id: "jira-mcp",       name: "jira-mcp",              type: "MCP Server", riskScore: 2.0, violations: { critical:0, high:1, medium:0, low:0 }, skillCount: 0,  toolCount: 4, deviceCount: 1, lastSeen: "3h ago" },
];

export const AGENTIC_ASSET_DEVICES = {
    "cursor-ai": [
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
        { deviceId: "SF-RCLARK-MAC01",     endpoint: "SF-RCLARK-MAC01",     username: "Robert Clark",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
        { deviceId: "SF-JLEWIS-MAC01",     endpoint: "SF-JLEWIS-MAC01",     username: "Jennifer Lewis", os: "mac",     lastSeen: "5d ago",  riskScore: 2.4 },
        { deviceId: "NYC-JANDERSON-MAC01", endpoint: "NYC-JANDERSON-MAC01", username: "James Anderson", os: "mac",     lastSeen: "5h ago",  riskScore: 3.8 },
    ],
    "vscode": [
        { deviceId: "BER-TSMITH-MAC02",    endpoint: "BER-TSMITH-MAC02",    username: "Traun Smith",    os: "mac",     lastSeen: "45m ago", riskScore: 4.7 },
    ],
    "github-copilot": [
        { deviceId: "BER-TSMITH-MAC02",    endpoint: "BER-TSMITH-MAC02",    username: "Traun Smith",    os: "mac",     lastSeen: "45m ago", riskScore: 4.7 },
        { deviceId: "SF-JLEWIS-WIN10",     endpoint: "SF-JLEWIS-WIN10",     username: "Jennifer Lewis", os: "windows", lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "claude-desktop": [
        { deviceId: "SF-MWILSON-MAC01",    endpoint: "SF-MWILSON-MAC01",    username: "Mark Wilson",    os: "mac",     lastSeen: "3d ago",  riskScore: 2.8 },
        { deviceId: "BER-DWILSON-MAC02",   endpoint: "BER-DWILSON-MAC02",   username: "David Wilson",   os: "mac",     lastSeen: "3h ago",  riskScore: 4.5 },
        { deviceId: "SF-CKING-MAC01",      endpoint: "SF-CKING-MAC01",      username: "Charles King",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    "copilot365": [
        { deviceId: "NYC-JDOE-WIN11",      endpoint: "NYC-JDOE-WIN11",      username: "John Doe",       os: "windows", lastSeen: "1d ago",  riskScore: 3.2 },
        { deviceId: "SF-MWILSON-WIN10",    endpoint: "SF-MWILSON-WIN10",    username: "Mark Wilson",    os: "windows", lastSeen: "1d ago",  riskScore: 4.5 },
        { deviceId: "SF-WHALL-WIN10",      endpoint: "SF-WHALL-WIN10",      username: "William Hall",   os: "windows", lastSeen: "6h ago",  riskScore: 4.1 },
        { deviceId: "SF-PYOUNG-WIN10",     endpoint: "SF-PYOUNG-WIN10",     username: "Patricia Young", os: "windows", lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    "chatgpt": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
    ],
    "playwright-ai": [
        { deviceId: "SF-LTHOMAS-MAC02",    endpoint: "SF-LTHOMAS-MAC02",    username: "Linda Thomas",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "jupyter-ai": [
        { deviceId: "TKY-AMATSUDA-LIN01",  endpoint: "TKY-AMATSUDA-LIN01",  username: "Akira Matsuda",  os: "linux",   lastSeen: "2d ago",  riskScore: 2.1 },
        { deviceId: "SF-JLEWIS-LIN01",     endpoint: "SF-JLEWIS-LIN01",     username: "Jennifer Lewis", os: "linux",   lastSeen: "2d ago",  riskScore: 3.1 },
    ],
    "mcp-akto": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
    ],
    "razorpay-stdio": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
    ],
    "postgres-mcp": [
        { deviceId: "SF-JLEWIS-LIN01",     endpoint: "SF-JLEWIS-LIN01",     username: "Jennifer Lewis", os: "linux",   lastSeen: "2d ago",  riskScore: 3.1 },
        { deviceId: "TKY-AMATSUDA-LIN01",  endpoint: "TKY-AMATSUDA-LIN01",  username: "Akira Matsuda",  os: "linux",   lastSeen: "2d ago",  riskScore: 2.1 },
    ],
    "github-mcp": [
        { deviceId: "BER-TSMITH-MAC02",    endpoint: "BER-TSMITH-MAC02",    username: "Traun Smith",    os: "mac",     lastSeen: "45m ago", riskScore: 4.7 },
        { deviceId: "NYC-JDOE-WIN11",      endpoint: "NYC-JDOE-WIN11",      username: "John Doe",       os: "windows", lastSeen: "1d ago",  riskScore: 3.2 },
    ],
    "kubernetes-mcp": [
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
    ],
    "databricks-mcp": [
        { deviceId: "SF-JLEWIS-WIN10",     endpoint: "SF-JLEWIS-WIN10",     username: "Jennifer Lewis", os: "windows", lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "slack-mcp": [
        { deviceId: "BER-DWILSON-MAC02",   endpoint: "BER-DWILSON-MAC02",   username: "David Wilson",   os: "mac",     lastSeen: "3h ago",  riskScore: 4.5 },
    ],
    "gpt4o": [
        { deviceId: "NYC-JDOE-WIN11",      endpoint: "NYC-JDOE-WIN11",      username: "John Doe",       os: "windows", lastSeen: "1d ago",  riskScore: 3.2 },
        { deviceId: "SF-MWILSON-WIN10",    endpoint: "SF-MWILSON-WIN10",    username: "Mark Wilson",    os: "windows", lastSeen: "1d ago",  riskScore: 4.5 },
        { deviceId: "NYC-JANDERSON-MAC01", endpoint: "NYC-JANDERSON-MAC01", username: "James Anderson", os: "mac",     lastSeen: "5h ago",  riskScore: 3.8 },
    ],
    "claude-3-7": [
        { deviceId: "BER-DWILSON-MAC02",   endpoint: "BER-DWILSON-MAC02",   username: "David Wilson",   os: "mac",     lastSeen: "3h ago",  riskScore: 4.5 },
        { deviceId: "SF-JLEWIS-MAC01",     endpoint: "SF-JLEWIS-MAC01",     username: "Jennifer Lewis", os: "mac",     lastSeen: "5d ago",  riskScore: 2.4 },
    ],
    "gemini-pro": [
        { deviceId: "SF-LTHOMAS-MAC02",    endpoint: "SF-LTHOMAS-MAC02",    username: "Linda Thomas",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
    ],
    "ollama": [
        { deviceId: "TKY-AMATSUDA-LIN01",  endpoint: "TKY-AMATSUDA-LIN01",  username: "Akira Matsuda",  os: "linux",   lastSeen: "2d ago",  riskScore: 2.1 },
        { deviceId: "SF-JLEWIS-LIN01",     endpoint: "SF-JLEWIS-LIN01",     username: "Jennifer Lewis", os: "linux",   lastSeen: "2d ago",  riskScore: 3.1 },
    ],
    "skill-fetch-credentials": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
        { deviceId: "BER-TSMITH-MAC02",    endpoint: "BER-TSMITH-MAC02",    username: "Traun Smith",    os: "mac",     lastSeen: "45m ago", riskScore: 4.7 },
        { deviceId: "BER-DWILSON-MAC02",   endpoint: "BER-DWILSON-MAC02",   username: "David Wilson",   os: "mac",     lastSeen: "3h ago",  riskScore: 4.5 },
        { deviceId: "NYC-JANDERSON-MAC01", endpoint: "NYC-JANDERSON-MAC01", username: "James Anderson", os: "mac",     lastSeen: "5h ago",  riskScore: 3.8 },
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
        { deviceId: "SF-RCLARK-MAC01",     endpoint: "SF-RCLARK-MAC01",     username: "Robert Clark",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "skill-run-api-scan": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
    ],
    "skill-query-database": [
        { deviceId: "SF-JLEWIS-LIN01",     endpoint: "SF-JLEWIS-LIN01",     username: "Jennifer Lewis", os: "linux",   lastSeen: "2d ago",  riskScore: 3.1 },
        { deviceId: "TKY-AMATSUDA-LIN01",  endpoint: "TKY-AMATSUDA-LIN01",  username: "Akira Matsuda",  os: "linux",   lastSeen: "2d ago",  riskScore: 2.1 },
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
    ],
    "skill-generate-snapshot": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
        { deviceId: "BER-TSMITH-MAC02",    endpoint: "BER-TSMITH-MAC02",    username: "Traun Smith",    os: "mac",     lastSeen: "45m ago", riskScore: 4.7 },
        { deviceId: "NYC-JANDERSON-MAC01", endpoint: "NYC-JANDERSON-MAC01", username: "James Anderson", os: "mac",     lastSeen: "5h ago",  riskScore: 3.8 },
    ],
    "skill-deploy-service": [
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
        { deviceId: "SF-RCLARK-MAC01",     endpoint: "SF-RCLARK-MAC01",     username: "Robert Clark",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "skill-export-report": [
        { deviceId: "SF-CKING-MAC01",      endpoint: "SF-CKING-MAC01",      username: "Charles King",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.1 },
        { deviceId: "SF-PYOUNG-WIN10",     endpoint: "SF-PYOUNG-WIN10",     username: "Patricia Young", os: "windows", lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    // Standalone MCP Servers
    "linear-mcp": [
        { deviceId: "NYC-JANDERSON-MAC01", endpoint: "NYC-JANDERSON-MAC01", username: "James Anderson", os: "mac",     lastSeen: "5h ago",  riskScore: 3.8 },
    ],
    "salesforce-mcp": [
        { deviceId: "LON-RJOHNSON-WIN11",  endpoint: "LON-RJOHNSON-WIN11",  username: "Robert Johnson", os: "windows", lastSeen: "12h ago", riskScore: 3.8 },
    ],
    "xcode-mcp": [
        { deviceId: "SF-RCLARK-MAC01",     endpoint: "SF-RCLARK-MAC01",     username: "Robert Clark",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "sap-mcp": [
        { deviceId: "SF-PYOUNG-WIN10",     endpoint: "SF-PYOUNG-WIN10",     username: "Patricia Young", os: "windows", lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    "cursor-cli": [
        { deviceId: "NYC-JDOE-MAC01",      endpoint: "NYC-JDOE-MAC01",      username: "John Doe",       os: "mac",     lastSeen: "2h ago",  riskScore: 4.8 },
    ],
    "gpt4o-mini": [
        { deviceId: "SF-STAYLOR-MAC01",    endpoint: "SF-STAYLOR-MAC01",    username: "Sarah Taylor",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.5 },
    ],
    "claude-haiku": [
        { deviceId: "SF-RCLARK-MAC01",     endpoint: "SF-RCLARK-MAC01",     username: "Robert Clark",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.3 },
    ],
    "teams-ai-bot": [
        { deviceId: "SF-MWILSON-WIN10",    endpoint: "SF-MWILSON-WIN10",    username: "Mark Wilson",    os: "windows", lastSeen: "1d ago",  riskScore: 4.5 },
    ],
    "notion-mcp": [
        { deviceId: "SF-MWILSON-MAC01",    endpoint: "SF-MWILSON-MAC01",    username: "Mark Wilson",    os: "mac",     lastSeen: "3d ago",  riskScore: 2.8 },
        { deviceId: "SF-WHALL-WIN10",      endpoint: "SF-WHALL-WIN10",      username: "William Hall",   os: "windows", lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    "sharepoint-mcp": [
        { deviceId: "SF-MWILSON-WIN10",    endpoint: "SF-MWILSON-WIN10",    username: "Mark Wilson",    os: "windows", lastSeen: "1d ago",  riskScore: 4.5 },
    ],
    "quickbooks-mcp": [
        { deviceId: "SF-CKING-MAC01",      endpoint: "SF-CKING-MAC01",      username: "Charles King",   os: "mac",     lastSeen: "6h ago",  riskScore: 4.1 },
    ],
    "jira-mcp": [
        { deviceId: "BER-DWILSON-MAC02",   endpoint: "BER-DWILSON-MAC02",   username: "David Wilson",   os: "mac",     lastSeen: "3h ago",  riskScore: 4.5 },
    ],
};

export const AGENTIC_ASSET_SPARKLINES = {
    "cursor-ai":      [18,20,22,24,23,26,28,27,30,33,36,38],
    "vscode":         [8,9,10,11,12,11,13,14,12,15,16,17],
    "github-copilot": [12,13,14,13,15,14,16,15,17,18,17,19],
    "claude-desktop": [10,12,15,16,18,17,20,22,21,24,25,28],
    "copilot365":     [40,42,45,44,48,50,49,52,55,54,58,60],
    "chatgpt":        [22,24,25,27,26,28,30,29,32,34,33,35],
    "playwright-ai":  [6,7,8,9,8,10,11,10,12,13,14,15],
    "jupyter-ai":     [5,5,6,7,6,8,7,9,8,10,9,11],
    "mcp-akto":       [15,17,18,20,22,21,24,25,23,27,28,30],
    "razorpay-stdio": [30,32,35,38,37,40,42,45,44,48,50,52],
    "postgres-mcp":   [10,11,12,11,13,12,14,13,15,14,16,15],
    "github-mcp":     [18,19,21,20,22,23,22,24,25,24,26,27],
    "kubernetes-mcp": [25,27,28,30,29,32,34,33,36,38,37,40],
    "databricks-mcp": [20,22,24,26,25,28,30,29,32,35,38,40],
    "slack-mcp":      [8,9,10,11,10,12,11,13,12,14,13,15],
    "gpt4o":          [35,37,38,40,39,42,44,43,46,48,47,50],
    "claude-3-7":     [12,13,15,14,16,15,17,16,18,19,18,20],
    "gemini-pro":     [10,11,12,13,12,14,13,15,14,16,15,17],
    "ollama":         [4,5,5,6,5,7,6,8,7,9,8,10],
};

export function generateAssetViolations(asset) {
    const rows = [];
    let t = 0;
    const add = (sev, idx) => {
        const tpls = VIOLATION_TEMPLATES[sev] || VIOLATION_TEMPLATES.low;
        const tpl = tpls[idx % tpls.length];
        rows.push({ id: rows.length, severity: sev, title: tpl.title, description: tpl.description, agent: asset.name, agentType: asset.type, time: REL_TIMES[t++ % REL_TIMES.length] });
    };
    for (let i = 0; i < (asset.violations?.critical || 0); i++) add("critical", i);
    for (let i = 0; i < (asset.violations?.high    || 0); i++) add("high",     i);
    for (let i = 0; i < (asset.violations?.medium  || 0); i++) add("medium",   i);
    for (let i = 0; i < (asset.violations?.low     || 0); i++) add("low",      i);
    return rows;
}
