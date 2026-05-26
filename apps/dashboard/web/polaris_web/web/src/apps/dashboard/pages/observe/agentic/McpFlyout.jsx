import React, { useState, useMemo, useCallback, useRef } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Tabs, Popover, ActionList, LegacyCard, Link, Icon, TextField, Badge } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AiChatSection from "./AiChatSection";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import "../../../components/layouts/style.css";

// ─── Theme ────────────────────────────────────────────────────────────────────

const gridTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
    borderRadius: 4,
    browserColorScheme: "light",
    cellTextColor: "#202223",
    columnBorder: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontSize: 13,
    foregroundColor: "#202223",
    headerFontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    headerRowBorder: true,
    headerTextColor: "#6D7175",
    rowBorder: true,
    spacing: 8,
    wrapperBorder: false,
    headerFontSize: 12,
    headerFontWeight: 500,
    checkboxBorderRadius: 4,
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getRiskColors(score) {
    if (score >= 4.5) return { bg: "#FEE2E2", color: "#DC2626" };
    if (score >= 4.0) return { bg: "#FFEDD5", color: "#EA580C" };
    if (score >= 3.5) return { bg: "#FEF9C3", color: "#CA8A04" };
    return { bg: "#F0FDF4", color: "#16A34A" };
}

function RiskBadge({ score }) {
    if (score == null) return null;
    const { bg, color } = getRiskColors(score);
    return (
        <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", padding: "2px 8px", borderRadius: 12, fontSize: 12, fontWeight: 600, background: bg, color, flexShrink: 0 }}>
            {score.toFixed(1)}
        </span>
    );
}

const RISK_LEVEL_STYLES = {
    critical: { bg: "#DF2909", color: "#FFFBFB" },
    high:     { bg: "#FED3D1", color: "#202223" },
    medium:   { bg: "#FFD79D", color: "#202223" },
    low:      { bg: "#E4E5E7", color: "#202223" },
};

// ─── MCP tool definitions ─────────────────────────────────────────────────────

const MCP_TOOLS = {
    "mcp.akto.io": [
        { id: 0, name: "run_api_scan",        riskLevel: "high",     description: "Trigger a security scan on a target API endpoint to detect OWASP Top 10 vulnerabilities and misconfigurations.", params: [{ name:"target_url",type:"string",required:true,desc:"API endpoint URL to scan"},{name:"scan_profile",type:"string",required:false,desc:"Scan depth: quick, standard, deep"},{name:"auth_token",type:"string",required:false,desc:"Bearer token for authenticated scanning"}] },
        { id: 1, name: "get_vulnerabilities", riskLevel: "medium",   description: "Retrieve vulnerabilities detected in the most recent scan, filtered by severity.", params: [{name:"severity",type:"string",required:false,desc:"Filter: critical, high, medium, low"},{name:"limit",type:"number",required:false,desc:"Max results (default 50)"}] },
        { id: 2, name: "generate_test_payload",riskLevel: "critical", description: "Generate synthetic attack payloads for pen-testing and red-team exercises.", params: [{name:"attack_type",type:"string",required:true,desc:"Attack class: sqli, xss, ssrf, auth_bypass, rce"},{name:"count",type:"number",required:false,desc:"Number of payloads to generate"},{name:"encoding",type:"string",required:false,desc:"Output encoding: none, base64, url"}] },
        { id: 3, name: "export_report",       riskLevel: "low",      description: "Export vulnerability scan results as a structured report.", params: [{name:"format",type:"string",required:true,desc:"Export format: pdf, csv, json, sarif"},{name:"scan_id",type:"string",required:false,desc:"Specific scan ID (defaults to latest)"}] },
        { id: 4, name: "replay_request",      riskLevel: "high",     description: "Replay a captured HTTP request with optional parameter mutations to test different inputs.", params: [{name:"request_id",type:"string",required:true,desc:"ID of the captured request"},{name:"mutations",type:"object",required:false,desc:"Key-value pairs to override in headers or payload"}] },
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
        { id: 0, name: "execute_query", riskLevel: "critical", description: "Execute an arbitrary SQL query. Supports SELECT, INSERT, UPDATE, DELETE.", params: [{name:"query",type:"string",required:true,desc:"SQL query string"},{name:"params",type:"array",required:false,desc:"Parameterized bindings"},{name:"timeout_ms",type:"number",required:false,desc:"Query timeout in milliseconds"}] },
        { id: 1, name: "list_tables",   riskLevel: "low",      description: "List all tables and views in the database schema.", params: [{name:"schema",type:"string",required:false,desc:"Schema name (default: public)"},{name:"include_views",type:"boolean",required:false,desc:"Include views in result"}] },
        { id: 2, name: "describe_table",riskLevel: "low",      description: "Get column definitions, data types, constraints, and indexes for a table.", params: [{name:"table_name",type:"string",required:true,desc:"Fully qualified table name"}] },
        { id: 3, name: "export_data",   riskLevel: "high",     description: "Export the result of a SELECT query to CSV or JSON.", params: [{name:"query",type:"string",required:true,desc:"SELECT query to export"},{name:"format",type:"string",required:true,desc:"Output format: csv or json"}] },
    ],
    "kubernetes-mcp": [
        { id: 0, name: "list_pods",       riskLevel: "low",      description: "List running pods with their current status and resource utilization.", params: [{name:"namespace",type:"string",required:false,desc:"Kubernetes namespace"},{name:"label_selector",type:"string",required:false,desc:"Label selector to filter pods"}] },
        { id: 1, name: "scale_deployment",riskLevel: "critical", description: "Scale a Kubernetes deployment to a specified number of replicas.", params: [{name:"deployment",type:"string",required:true,desc:"Deployment name"},{name:"replicas",type:"number",required:true,desc:"Desired replica count"},{name:"namespace",type:"string",required:false,desc:"Target namespace"}] },
        { id: 2, name: "get_logs",        riskLevel: "medium",   description: "Stream or fetch logs from a specific pod or container.", params: [{name:"pod_name",type:"string",required:true,desc:"Pod name"},{name:"container",type:"string",required:false,desc:"Container name"},{name:"tail_lines",type:"number",required:false,desc:"Number of log lines to return"}] },
        { id: 3, name: "exec_command",    riskLevel: "critical", description: "Execute a shell command inside a running container. Equivalent to kubectl exec.", params: [{name:"pod_name",type:"string",required:true,desc:"Target pod"},{name:"command",type:"array",required:true,desc:"Command array e.g. ['sh','-c','ls /etc']"}] },
        { id: 4, name: "delete_resource", riskLevel: "critical", description: "Delete a Kubernetes resource by name and kind.", params: [{name:"kind",type:"string",required:true,desc:"Resource kind: Pod, Deployment, Service..."},{name:"name",type:"string",required:true,desc:"Resource name"},{name:"namespace",type:"string",required:false,desc:"Target namespace"}] },
    ],
    "aws-mcp": [
        { id: 0, name: "list_buckets",     riskLevel: "low",      description: "List all S3 buckets in the AWS account.", params: [] },
        { id: 1, name: "get_object",       riskLevel: "high",     description: "Download the contents of an object from S3.", params: [{name:"bucket",type:"string",required:true,desc:"S3 bucket name"},{name:"key",type:"string",required:true,desc:"Object key within bucket"}] },
        { id: 2, name: "invoke_function",  riskLevel: "critical", description: "Invoke an AWS Lambda function synchronously or asynchronously.", params: [{name:"function_name",type:"string",required:true,desc:"Lambda function name or ARN"},{name:"payload",type:"object",required:false,desc:"JSON event payload"},{name:"invocation_type",type:"string",required:false,desc:"RequestResponse or Event"}] },
        { id: 3, name: "describe_instances",riskLevel:"medium",   description: "Describe EC2 instances with state, instance type, and network config.", params: [{name:"instance_ids",type:"array",required:false,desc:"List of EC2 instance IDs"},{name:"filters",type:"array",required:false,desc:"EC2 filter objects"}] },
        { id: 4, name: "put_secret",       riskLevel: "critical", description: "Create or update a secret in AWS Secrets Manager.", params: [{name:"secret_name",type:"string",required:true,desc:"Secret name or ARN"},{name:"secret_value",type:"string",required:true,desc:"New secret value"}] },
    ],
    "github-mcp": [
        { id: 0, name: "list_repos",           riskLevel: "low",    description: "List repositories accessible to the token, including private repos.", params: [{name:"org",type:"string",required:false,desc:"Organization name"},{name:"visibility",type:"string",required:false,desc:"all, public, or private"}] },
        { id: 1, name: "read_file",            riskLevel: "medium", description: "Read the raw contents of a file at a specific path and branch.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"path",type:"string",required:true,desc:"File path"},{name:"ref",type:"string",required:false,desc:"Branch or commit SHA"}] },
        { id: 2, name: "create_issue",         riskLevel: "medium", description: "Create a new GitHub issue with title, body, and labels.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"title",type:"string",required:true,desc:"Issue title"},{name:"body",type:"string",required:false,desc:"Markdown body"}] },
        { id: 3, name: "merge_pull_request",   riskLevel: "high",   description: "Merge an open pull request using squash, merge, or rebase.", params: [{name:"owner",type:"string",required:true,desc:"Repo owner"},{name:"repo",type:"string",required:true,desc:"Repository name"},{name:"pull_number",type:"number",required:true,desc:"PR number"},{name:"merge_method",type:"string",required:false,desc:"merge, squash, or rebase"}] },
    ],
    "slack-mcp": [
        { id: 0, name: "send_message", riskLevel: "high",   description: "Send a message to a Slack channel or DM.", params: [{name:"channel",type:"string",required:true,desc:"Channel ID or #name"},{name:"text",type:"string",required:true,desc:"Message text (supports mrkdwn)"},{name:"thread_ts",type:"string",required:false,desc:"Parent message timestamp for threading"}] },
        { id: 1, name: "get_history",  riskLevel: "medium", description: "Retrieve message history of a Slack channel.", params: [{name:"channel",type:"string",required:true,desc:"Channel ID"},{name:"limit",type:"number",required:false,desc:"Max messages (default 100)"}] },
        { id: 2, name: "list_channels",riskLevel: "low",    description: "List channels the bot has access to.", params: [{name:"types",type:"string",required:false,desc:"public_channel, private_channel, im, mpim"}] },
        { id: 3, name: "upload_file",  riskLevel: "high",   description: "Upload a file to a Slack channel.", params: [{name:"channel",type:"string",required:true,desc:"Target channel"},{name:"content",type:"string",required:true,desc:"File content"},{name:"filename",type:"string",required:true,desc:"Display filename"}] },
    ],
    "jira-mcp": [
        { id: 0, name: "list_issues",  riskLevel: "low",    description: "Query JIRA issues using JQL with pagination.", params: [{name:"jql",type:"string",required:true,desc:"JQL query string"},{name:"max_results",type:"number",required:false,desc:"Max issues (default 50)"}] },
        { id: 1, name: "create_issue", riskLevel: "medium", description: "Create a new JIRA issue.", params: [{name:"project_key",type:"string",required:true,desc:"JIRA project key"},{name:"issue_type",type:"string",required:true,desc:"Bug, Task, Story, Epic"},{name:"summary",type:"string",required:true,desc:"Issue title"}] },
        { id: 2, name: "update_status",riskLevel: "medium", description: "Transition a JIRA issue to a new workflow status.", params: [{name:"issue_key",type:"string",required:true,desc:"JIRA issue key (ENG-123)"},{name:"transition",type:"string",required:true,desc:"Transition name"}] },
        { id: 3, name: "assign_issue", riskLevel: "low",    description: "Assign a JIRA issue to a specific user.", params: [{name:"issue_key",type:"string",required:true,desc:"JIRA issue key"},{name:"account_id",type:"string",required:true,desc:"Atlassian account ID"}] },
    ],
    "notion-mcp": [
        { id: 0, name: "list_pages",   riskLevel: "low",    description: "List pages accessible to the integration.", params: [{name:"page_size",type:"number",required:false,desc:"Results per page (max 100)"}] },
        { id: 1, name: "create_page",  riskLevel: "medium", description: "Create a new Notion page under a parent.", params: [{name:"parent_id",type:"string",required:true,desc:"Parent page or database ID"},{name:"title",type:"string",required:true,desc:"Page title"}] },
        { id: 2, name: "update_page",  riskLevel: "medium", description: "Update properties or content of an existing page.", params: [{name:"page_id",type:"string",required:true,desc:"Notion page ID"},{name:"properties",type:"object",required:false,desc:"Property values to update"}] },
        { id: 3, name: "search",       riskLevel: "low",    description: "Full-text search across the Notion workspace.", params: [{name:"query",type:"string",required:true,desc:"Search text"}] },
    ],
    "salesforce-mcp": [
        { id: 0, name: "query_records",     riskLevel: "high",   description: "Execute a SOQL query against Salesforce objects.", params: [{name:"soql",type:"string",required:true,desc:"SOQL query string"}] },
        { id: 1, name: "create_opportunity",riskLevel: "medium", description: "Create a new Salesforce Opportunity.", params: [{name:"name",type:"string",required:true,desc:"Opportunity name"},{name:"account_id",type:"string",required:true,desc:"Parent Account ID"},{name:"stage",type:"string",required:true,desc:"Sales stage"},{name:"close_date",type:"string",required:true,desc:"Expected close (YYYY-MM-DD)"}] },
        { id: 2, name: "update_contact",    riskLevel: "high",   description: "Update fields on an existing Contact record.", params: [{name:"contact_id",type:"string",required:true,desc:"Salesforce Contact ID"},{name:"fields",type:"object",required:true,desc:"Key-value field updates"}] },
    ],
    "sharepoint-mcp": [
        { id: 0, name: "list_files",   riskLevel: "low",  description: "List files in a SharePoint document library.", params: [{name:"site_url",type:"string",required:true,desc:"SharePoint site URL"},{name:"library",type:"string",required:true,desc:"Document library name"}] },
        { id: 1, name: "download_file",riskLevel: "high", description: "Download contents of a SharePoint file.", params: [{name:"site_url",type:"string",required:true,desc:"Site URL"},{name:"file_path",type:"string",required:true,desc:"Server-relative file path"}] },
        { id: 2, name: "upload_file",  riskLevel: "high", description: "Upload a file to a document library.", params: [{name:"site_url",type:"string",required:true,desc:"Site URL"},{name:"library",type:"string",required:true,desc:"Target library"},{name:"filename",type:"string",required:true,desc:"File name"},{name:"content",type:"string",required:true,desc:"File content (base64 for binary)"}] },
    ],
    "linear-mcp": [
        { id: 0, name: "list_issues",  riskLevel: "low",    description: "List Linear issues with filtering by team, status, or assignee.", params: [{name:"team_id",type:"string",required:false,desc:"Team ID"},{name:"state",type:"string",required:false,desc:"Issue state"}] },
        { id: 1, name: "create_issue", riskLevel: "medium", description: "Create a new issue in a Linear team.", params: [{name:"team_id",type:"string",required:true,desc:"Target team ID"},{name:"title",type:"string",required:true,desc:"Issue title"}] },
        { id: 2, name: "update_status",riskLevel: "low",    description: "Move a Linear issue to a different workflow state.", params: [{name:"issue_id",type:"string",required:true,desc:"Linear issue ID"},{name:"state_id",type:"string",required:true,desc:"Target state ID"}] },
    ],
    "quickbooks-mcp": [
        { id: 0, name: "list_invoices",    riskLevel: "high",     description: "List invoices with filtering by status or date.", params: [{name:"status",type:"string",required:false,desc:"Draft, Pending, Voided"},{name:"start_date",type:"string",required:false,desc:"Start date (YYYY-MM-DD)"}] },
        { id: 1, name: "create_invoice",   riskLevel: "critical", description: "Create a new invoice for a customer.", params: [{name:"customer_ref",type:"object",required:true,desc:"Customer reference { value: id }"},{name:"line_items",type:"array",required:true,desc:"Line items with amount"}] },
        { id: 2, name: "get_balance_sheet",riskLevel: "critical", description: "Retrieve balance sheet summarizing assets, liabilities, equity.", params: [{name:"as_of_date",type:"string",required:true,desc:"Balance sheet date (YYYY-MM-DD)"}] },
        { id: 3, name: "list_transactions",riskLevel: "critical", description: "List all financial transactions including payments and expenses.", params: [{name:"start_date",type:"string",required:true,desc:"Range start"},{name:"end_date",type:"string",required:true,desc:"Range end"}] },
    ],
    "sap-mcp": [
        { id: 0, name: "get_inventory",       riskLevel: "medium",   description: "Query current inventory levels for materials across storage locations.", params: [{name:"material_id",type:"string",required:false,desc:"Material number"},{name:"plant",type:"string",required:false,desc:"Plant/facility code"}] },
        { id: 1, name: "create_purchase_order",riskLevel:"critical", description: "Create a new purchase order with vendor, materials, and pricing.", params: [{name:"vendor",type:"string",required:true,desc:"Vendor account number"},{name:"items",type:"array",required:true,desc:"Line items: material, quantity, price"}] },
        { id: 2, name: "approve_expense",     riskLevel: "critical", description: "Approve a pending expense report in SAP FI.", params: [{name:"expense_id",type:"string",required:true,desc:"Expense report ID"},{name:"comment",type:"string",required:false,desc:"Approval comment"}] },
        { id: 3, name: "get_financial_report",riskLevel: "critical", description: "Generate a P&L or cost center report for a given period.", params: [{name:"report_type",type:"string",required:true,desc:"P&L, CostCenter, BalanceSheet"},{name:"fiscal_year",type:"number",required:true,desc:"SAP fiscal year"}] },
    ],
    "databricks-mcp": [
        { id: 0, name: "run_notebook", riskLevel: "critical", description: "Execute a Databricks notebook with optional parameters.", params: [{name:"path",type:"string",required:true,desc:"Notebook workspace path"},{name:"cluster_id",type:"string",required:false,desc:"Target cluster ID"},{name:"parameters",type:"object",required:false,desc:"Widget parameter overrides"}] },
        { id: 1, name: "query_table",  riskLevel: "high",     description: "Run a Spark SQL or Delta Lake query against a registered table.", params: [{name:"sql",type:"string",required:true,desc:"SQL query string"},{name:"limit",type:"number",required:false,desc:"Row limit (default 1000)"}] },
        { id: 2, name: "list_clusters",riskLevel: "low",      description: "List Databricks compute clusters with their state.", params: [{name:"filter_by",type:"string",required:false,desc:"all, running, terminated"}] },
        { id: 3, name: "get_job_status",riskLevel:"low",      description: "Get the status and output of a Databricks workflow job run.", params: [{name:"run_id",type:"number",required:true,desc:"Job run ID"}] },
    ],
    "xcode-mcp": [
        { id: 0, name: "build_project", riskLevel: "medium", description: "Build an Xcode project with a specified scheme and configuration.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Build scheme"}] },
        { id: 1, name: "run_tests",     riskLevel: "low",    description: "Execute the unit and UI test suite for a scheme.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Test scheme"}] },
        { id: 2, name: "archive_app",   riskLevel: "medium", description: "Archive an iOS/macOS app for App Store or ad-hoc distribution.", params: [{name:"project_path",type:"string",required:true,desc:"Path to .xcodeproj"},{name:"scheme",type:"string",required:true,desc:"Archive scheme"}] },
        { id: 3, name: "get_build_logs",riskLevel: "low",    description: "Retrieve full build log output for the most recent build.", params: [] },
    ],
};

function getToolsForServer(endpoint) {
    if (!endpoint) return [];
    const normalised = endpoint.toLowerCase();
    const key = Object.keys(MCP_TOOLS).find(k => normalised.includes(k.replace("-mcp","").replace("-stdio","")) || normalised === k);
    return key ? MCP_TOOLS[key] : [];
}

// ─── MCP Resources & Prompts ──────────────────────────────────────────────────

const MCP_RESOURCES = {
    "razorpay-stdio": [
        { id: 0, uri: "razorpay://transactions",    name: "transactions",      description: "Real-time transaction feed" },
        { id: 1, uri: "razorpay://customers",        name: "customers",         description: "Customer registry" },
        { id: 2, uri: "razorpay://settlements",      name: "settlements",       description: "Settlement reports" },
    ],
    "mcp.akto.io": [
        { id: 0, uri: "akto://collections",          name: "collections",       description: "API collection registry" },
        { id: 1, uri: "akto://vulnerabilities",      name: "vulnerabilities",   description: "Active vulnerability list" },
    ],
    "postgres-mcp": [
        { id: 0, uri: "postgres://schema",           name: "schema",            description: "Full database schema" },
        { id: 1, uri: "postgres://tables",           name: "tables",            description: "Table and view listing" },
    ],
    "kubernetes-mcp": [
        { id: 0, uri: "k8s://namespaces",            name: "namespaces",        description: "All cluster namespaces" },
        { id: 1, uri: "k8s://nodes",                 name: "nodes",             description: "Node status and capacity" },
        { id: 2, uri: "k8s://events",                name: "events",            description: "Recent cluster events" },
    ],
    "aws-mcp": [
        { id: 0, uri: "aws://s3/buckets",            name: "s3-buckets",        description: "All S3 buckets" },
        { id: 1, uri: "aws://ec2/instances",         name: "ec2-instances",     description: "EC2 instance inventory" },
        { id: 2, uri: "aws://iam/policies",          name: "iam-policies",      description: "IAM policy documents" },
    ],
    "github-mcp": [
        { id: 0, uri: "github://repos",              name: "repositories",      description: "Accessible repositories" },
        { id: 1, uri: "github://org/members",        name: "org-members",       description: "Organization members" },
    ],
    "slack-mcp": [
        { id: 0, uri: "slack://channels",            name: "channels",          description: "All accessible channels" },
        { id: 1, uri: "slack://users",               name: "users",             description: "Workspace user directory" },
    ],
    "jira-mcp": [
        { id: 0, uri: "jira://projects",             name: "projects",          description: "All JIRA projects" },
        { id: 1, uri: "jira://boards",               name: "boards",            description: "Agile boards" },
    ],
    "notion-mcp": [
        { id: 0, uri: "notion://pages",              name: "pages",             description: "All accessible pages" },
        { id: 1, uri: "notion://databases",          name: "databases",         description: "All Notion databases" },
    ],
    "databricks-mcp": [
        { id: 0, uri: "databricks://catalogs",       name: "catalogs",          description: "Unity Catalog listing" },
        { id: 1, uri: "databricks://jobs",           name: "jobs",              description: "Workflow job registry" },
        { id: 2, uri: "databricks://clusters",       name: "clusters",          description: "Compute cluster states" },
    ],
    "quickbooks-mcp": [
        { id: 0, uri: "qbo://accounts",              name: "chart-of-accounts", description: "Chart of accounts" },
        { id: 1, uri: "qbo://reports/pnl",           name: "pnl-report",        description: "Profit & loss report" },
    ],
};

const MCP_PROMPTS = {
    "razorpay-stdio": [
        { id: 0, name: "analyze-dispute",       description: "Analyze a payment dispute and recommend resolution", args: ["payment_id", "dispute_reason"] },
        { id: 1, name: "reconcile-report",      description: "Reconcile settlements against ledger for a date range", args: ["start_date", "end_date"] },
        { id: 2, name: "fraud-risk-summary",    description: "Summarize fraud risk signals for a customer account", args: ["customer_id"] },
    ],
    "mcp.akto.io": [
        { id: 0, name: "security-scan-report",  description: "Generate a security report for an API collection", args: ["collection_id"] },
        { id: 1, name: "vulnerability-triage",  description: "Triage and prioritize vulnerabilities by risk", args: ["collection_id", "severity"] },
    ],
    "postgres-mcp": [
        { id: 0, name: "explain-slow-query",    description: "Analyze a query and suggest indexes or rewrites", args: ["query"] },
        { id: 1, name: "schema-docs",           description: "Generate documentation for a table schema", args: ["table_name"] },
    ],
    "kubernetes-mcp": [
        { id: 0, name: "incident-runbook",      description: "Generate a runbook for a failing workload", args: ["resource_name", "namespace"] },
        { id: 1, name: "capacity-analysis",     description: "Analyze cluster resource utilization and headroom", args: [] },
    ],
    "aws-mcp": [
        { id: 0, name: "cost-anomaly",          description: "Identify unexpected cost spikes in AWS spend", args: ["start_date", "end_date"] },
        { id: 1, name: "security-review",       description: "Review S3 and IAM config against CIS benchmarks", args: [] },
    ],
    "github-mcp": [
        { id: 0, name: "pr-summary",            description: "Summarize code changes in a pull request", args: ["owner", "repo", "pull_number"] },
        { id: 1, name: "dependency-audit",      description: "Audit dependencies for known CVEs", args: ["owner", "repo"] },
    ],
    "slack-mcp": [
        { id: 0, name: "channel-summary",       description: "Summarize recent activity in a channel", args: ["channel", "hours"] },
        { id: 1, name: "action-items",          description: "Extract action items from a thread", args: ["channel", "thread_ts"] },
    ],
    "databricks-mcp": [
        { id: 0, name: "notebook-review",       description: "Review a notebook for best practices and performance", args: ["path"] },
        { id: 1, name: "data-quality-check",    description: "Run data quality checks on a Delta table", args: ["table_name"] },
    ],
};

const TOOL_VIOLATIONS = {
    "create_payment_link": 2, "refund_payment": 1, "generate_test_payload": 1,
    "run_notebook": 4, "query_table": 1, "execute_query": 2,
    "exec_command": 1, "scale_deployment": 1, "merge_pull_request": 1,
};

function getResourcesForServer(endpoint) {
    if (!endpoint) return [];
    const n = endpoint.toLowerCase();
    const key = Object.keys(MCP_RESOURCES).find(k => n.includes(k.replace("-mcp","").replace("-stdio","")) || n === k);
    return key ? MCP_RESOURCES[key] : [];
}

function getPromptsForServer(endpoint) {
    if (!endpoint) return [];
    const n = endpoint.toLowerCase();
    const key = Object.keys(MCP_PROMPTS).find(k => n.includes(k.replace("-mcp","").replace("-stdio","")) || n === k);
    return key ? MCP_PROMPTS[key] : [];
}

// ─── Generate sample request/response for a tool ─────────────────────────────

function generateResourceSample(resource) {
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

function generatePromptSample(prompt) {
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

function generateToolSample(tool) {
    const args = {};
    (tool.params || []).forEach(p => {
        if (!p.required) return;
        if (p.type === "string")  args[p.name] = `example_${p.name.replace(/_/g, "-")}`;
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
            return { id: `id_abc123`, status: "updated", updatedAt: "2026-05-25T08:30:00Z" };
        if (n.startsWith("delete_") || n.startsWith("refund_"))
            return { success: true, id: `id_abc123`, status: "deleted" };
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

// ─── Cell renderers ───────────────────────────────────────────────────────────

function ToolNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 600, fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
        </div>
    );
}

function ToolViolationsCell({ data }) {
    if (!data) return null;
    const count = TOOL_VIOLATIONS[data.name] || 0;
    if (!count) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 22, height: 20, padding: "0 6px", borderRadius: 10, fontSize: 11, fontWeight: 700, background: "#DF2909", color: "#FFFBFB" }}>
                {count}
            </span>
        </div>
    );
}

function ToolParamsCell({ data }) {
    if (!data) return null;
    const count = data.params?.length || 0;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#6D7175" }}>{count}</span></div>;
}

const TOOLS_COL_DEFS = [
    { field: "name",       headerName: "Tool",       flex: 1,   minWidth: 160, cellRenderer: ToolNameCell,       cellStyle: { display: "flex", alignItems: "center" } },
    { field: "violations", headerName: "Violations", width: 110, sort: "desc", suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolViolationsCell, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => TOOL_VIOLATIONS[p.data?.name] || 0 },
    { field: "params",     headerName: "Params",     width: 80,  suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolParamsCell,     cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.params?.length ?? 0 },
];

// ─── Resources cell renderers ─────────────────────────────────────────────────

function ResourceNameCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</span></div>;
}

function ResourceUriCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#8C9196", fontFamily: "ui-monospace, 'Cascadia Mono', Consolas, monospace" }}>{data.uri}</span></div>;
}

const RESOURCES_COL_DEFS = [
    { field: "name", headerName: "Name", flex: 1,   minWidth: 120, cellRenderer: ResourceNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "uri",  headerName: "URI",  flex: 1,   minWidth: 160, cellRenderer: ResourceUriCell,  cellStyle: { display: "flex", alignItems: "center" } },
];

// ─── Prompts cell renderers ───────────────────────────────────────────────────

function PromptNameCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</span></div>;
}

function PromptDescCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#6D7175" }}>{data.description}</span></div>;
}

const PROMPTS_COL_DEFS = [
    { field: "name",        headerName: "Session Title", flex: 1,   minWidth: 140, cellRenderer: PromptNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "description", headerName: "Prompt",   flex: 2,   minWidth: 200, cellRenderer: PromptDescCell, cellStyle: { display: "flex", alignItems: "center" } },
];

// ─── Schema param cell renderers (shared with SkillsFlyout pattern) ──────────

function ParamNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: "#202223" }}>{data.name}</span>
            {data.required ? <Badge status="critical">required</Badge> : <Badge>optional</Badge>}
        </div>
    );
}

function ParamTypeCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <Badge status="info">{data.type}</Badge>
        </div>
    );
}

function ParamDescCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 12, color: "#6D7175", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.desc}</span>
        </div>
    );
}

const SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,   minWidth: 140, cellRenderer: ParamNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,   minWidth: 160, cellRenderer: ParamDescCell, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Tool detail view (mirrors SkillDetailView) ───────────────────────────────

const TOOL_TABS = [
    { id: "value",  content: "Value" },
    { id: "schema", content: "Schema" },
    { id: "traces", content: "Traces" },
];

function ToolDetailView({ tool, device, agent, allTools, onBack, onClose, onToolChange, onDeviceClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [pickerOpen, setPickerOpen]   = useState(false);
    const [pickerSearch, setPickerSearch] = useState("");

    const sampleData = useMemo(() => generateToolSample(tool), [tool.id]);

    const tabs = [
        ...TOOL_TABS,
        { id: "violations", content: `Violations (0)` },
    ];

    const otherTools = useMemo(() => allTools.filter(t => t.id !== tool.id), [allTools, tool.id]);

    const filteredTools = useMemo(() =>
        pickerSearch
            ? otherTools.filter(t => t.name.toLowerCase().includes(pickerSearch.toLowerCase()))
            : otherTools,
        [otherTools, pickerSearch]
    );

    const toolActions = useMemo(() =>
        filteredTools.map(t => ({
            content: t.name,
            onAction: () => { onToolChange(t); setPickerOpen(false); setPickerSearch(""); setSelectedTab(0); },
        })),
        [filteredTools, onToolChange]
    );

    const showChevron = otherTools.length > 0;
    const showSearch  = otherTools.length > 5;

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            {/* Breadcrumb header */}
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                ]}
                onClose={onClose}
            >
                <span style={{ color: "#8C9196" }}>/</span>
                <Popover
                    active={pickerOpen}
                    onClose={() => { setPickerOpen(false); setPickerSearch(""); }}
                    preferredAlignment="left"
                    activator={
                        <button
                            onClick={() => showChevron && setPickerOpen(s => !s)}
                            style={{
                                background: "none", border: "none", padding: 0,
                                cursor: showChevron ? "pointer" : "default",
                                fontSize: 13, fontWeight: 600, color: "#202223",
                                fontFamily: "Inter, sans-serif",
                                display: "flex", alignItems: "center", gap: 2,
                            }}
                        >
                            {tool.name}
                            {showChevron && (
                                <span style={{ display: "flex", alignItems: "center", color: "#6D7175" }}>
                                    <Icon source={ChevronDownMinor} />
                                </span>
                            )}
                        </button>
                    }
                >
                    {showSearch && (
                        <Popover.Pane fixed>
                            <Popover.Section>
                                <TextField
                                    autoFocus
                                    type="search"
                                    placeholder="Search tools…"
                                    value={pickerSearch}
                                    onChange={setPickerSearch}
                                    autoComplete="off"
                                    clearButton
                                    onClearButtonClick={() => setPickerSearch("")}
                                />
                            </Popover.Section>
                        </Popover.Pane>
                    )}
                    <Popover.Pane>
                        <ActionList items={toolActions} />
                    </Popover.Pane>
                </Popover>
            </FlyoutBreadcrumb>

            {/* Tabs bar */}
            <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            </div>

            {/* Tab content */}
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && (
                    <div style={{ flex: 1, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                        <LegacyCard>
                            <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                        <LegacyCard>
                            <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                    </div>
                )}
                {selectedTab === 1 && (
                    tool.params && tool.params.length > 0 ? (
                        <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                            <div style={{ position: "absolute", inset: 0 }}>
                                <AgGridReact
                                    theme={gridTheme}
                                    rowData={tool.params}
                                    columnDefs={SCHEMA_COL_DEFS}
                                    defaultColDef={{ sortable: false, resizable: true }}
                                    rowHeight={44}
                                    headerHeight={40}
                                    suppressCellFocus
                                />
                            </div>
                        </div>
                    ) : (
                        <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No parameters.</div>
                    )
                )}
                {selectedTab === 2 && <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No traces recorded yet.</div>}
                {selectedTab === 3 && <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No violations found.</div>}
            </div>
        </div>
    );
}

// ─── Resource & Prompt detail views ──────────────────────────────────────────

function ResourceDetailView({ resource, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generateResourceSample(resource), [resource.id]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: resource.name },
                ]}
                onClose={onClose}
            />
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                <LegacyCard>
                    <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
                <LegacyCard>
                    <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
            </div>
        </div>
    );
}

function PromptDetailView({ prompt, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generatePromptSample(prompt), [prompt.id]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: prompt.name },
                ]}
                onClose={onClose}
            />
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                <LegacyCard>
                    <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
                <LegacyCard>
                    <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
            </div>
        </div>
    );
}

// ─── Resources & Prompts views ────────────────────────────────────────────────

function ResourcesView({ resources, onResourceClick }) {
    if (resources.length === 0) {
        return (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No resources exposed</span>
                <span style={{ fontSize: 12, color: "#6D7175" }}>This MCP server does not expose any resources.</span>
            </div>
        );
    }
    return (
        <div style={{ position: "absolute", inset: 0 }}>
            <AgGridReact
                theme={gridTheme}
                rowData={resources}
                columnDefs={RESOURCES_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                onRowClicked={e => { if (e.data) onResourceClick(e.data); }}
            />
        </div>
    );
}

function PromptsView({ prompts, onPromptClick }) {
    if (prompts.length === 0) {
        return (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No prompts defined</span>
                <span style={{ fontSize: 12, color: "#6D7175" }}>This MCP server does not expose any prompt templates.</span>
            </div>
        );
    }
    return (
        <div style={{ position: "absolute", inset: 0 }}>
            <AgGridReact
                theme={gridTheme}
                rowData={prompts}
                columnDefs={PROMPTS_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                onRowClicked={e => { if (e.data) onPromptClick(e.data); }}
            />
        </div>
    );
}

// ─── Tools list view ──────────────────────────────────────────────────────────

function ToolsListView({ agent, device, tools, resources, prompts, onToolClick, onClose, onDeviceClick, onResourceClick, onPromptClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [quickFilter, setQuickFilter] = useState("");
    const gridRef = useRef(null);

    const tabs = useMemo(() => [
        { id: "tools",     content: `Tools (${tools.length})` },
        { id: "resources", content: `Resources (${resources.length})` },
        { id: "prompts",   content: `Prompts (${prompts.length})` },
    ], [tools.length, resources.length, prompts.length]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            {/* Header / breadcrumb */}
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: onDeviceClick ? () => onDeviceClick(device) : undefined },
                    { label: agent?.endpoint },
                ]}
                onClose={onClose}
            />

            {/* Tabs */}
            <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                <Tabs tabs={tabs} selected={selectedTab} onSelect={i => { setSelectedTab(i); setQuickFilter(""); }} />
            </div>

            {/* Search toolbar — always visible for Tools tab */}
            {selectedTab === 0 && (
                <div style={{ display: "flex", alignItems: "center", padding: "6px 12px", borderBottom: "1px solid #E1E3E5", flexShrink: 0, gap: 8 }}>
                    <svg width="13" height="13" viewBox="0 0 20 20" fill="#8C9196" style={{ flexShrink: 0 }}>
                        <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd"/>
                    </svg>
                    <input
                        type="text" placeholder="Search tools…" value={quickFilter}
                        onChange={e => setQuickFilter(e.target.value)}
                        style={{ flex: 1, border: "none", outline: "none", fontSize: 13, color: "#202223", background: "transparent", fontFamily: "Inter, sans-serif" }}
                    />
                    {quickFilter && <button onClick={() => setQuickFilter("")} style={{ background: "none", border: "none", cursor: "pointer", color: "#8C9196", fontSize: 16, lineHeight: 1, padding: 0 }}>×</button>}
                </div>
            )}

            {/* Tools tab */}
            {selectedTab === 0 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    {tools.length === 0 ? (
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                            <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No tools captured</span>
                            <span style={{ fontSize: 12, color: "#6D7175" }}>Tool schema for <strong>{agent?.endpoint}</strong> has not been captured yet.</span>
                        </div>
                    ) : (
                        <div style={{ position: "absolute", inset: 0 }}>
                            <AgGridReact
                                ref={gridRef}
                                theme={gridTheme}
                                rowData={tools}
                                columnDefs={TOOLS_COL_DEFS}
                                defaultColDef={GRID_DEFAULT_COL}
                                rowHeight={44}
                                headerHeight={40}
                                suppressCellFocus
                                quickFilterText={quickFilter}
                                onRowClicked={e => { if (e.data) onToolClick(e.data); }}
                                pagination
                                paginationPageSize={20}
                                paginationPageSizeSelector={[20, 50, 100]}
                            />
                        </div>
                    )}
                </div>
            )}

            {/* Resources tab */}
            {selectedTab === 1 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    <ResourcesView resources={resources} onResourceClick={onResourceClick} />
                </div>
            )}

            {/* Prompts tab */}
            {selectedTab === 2 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    <PromptsView prompts={prompts} onPromptClick={onPromptClick} />
                </div>
            )}
        </div>
    );
}

// ─── Main McpFlyout ───────────────────────────────────────────────────────────

export default function McpFlyout({ agent, device, show, onClose, onDeviceClick }) {
    const [selectedTool,     setSelectedTool]     = useState(null);
    const [selectedResource, setSelectedResource] = useState(null);
    const [selectedPrompt,   setSelectedPrompt]   = useState(null);

    const allTools     = useMemo(() => getToolsForServer(agent?.endpoint),     [agent?.endpoint]);
    const allResources = useMemo(() => getResourcesForServer(agent?.endpoint), [agent?.endpoint]);
    const allPrompts   = useMemo(() => getPromptsForServer(agent?.endpoint),   [agent?.endpoint]);

    // Reset when flyout closes or agent changes
    React.useEffect(() => { if (!show) { setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null); } }, [show]);
    React.useEffect(() => { setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null); }, [agent?.endpoint]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    React.useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);

    if (!agent) return null;

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            <div
                className="innerFlyLayout"
                onMouseEnter={lockScroll}
                onMouseLeave={unlockScroll}
                style={{
                    width: 720, top: "3.5rem",
                    height: "calc(100vh - 3.5rem)",
                    overflowY: "hidden",
                    display: "flex", flexDirection: "column",
                    background: "white",
                    borderLeft: "1px solid #E1E3E5",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            >
                {selectedTool ? (
                    <ToolDetailView
                        key={selectedTool.id}
                        tool={selectedTool}
                        device={device}
                        agent={agent}
                        allTools={allTools}
                        onBack={() => setSelectedTool(null)}
                        onClose={onClose}
                        onToolChange={setSelectedTool}
                        onDeviceClick={onDeviceClick}
                    />
                ) : selectedResource ? (
                    <ResourceDetailView
                        resource={selectedResource}
                        agent={agent}
                        device={device}
                        onBack={() => setSelectedResource(null)}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                    />
                ) : selectedPrompt ? (
                    <PromptDetailView
                        prompt={selectedPrompt}
                        agent={agent}
                        device={device}
                        onBack={() => setSelectedPrompt(null)}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                    />
                ) : (
                    <ToolsListView
                        agent={agent}
                        device={device}
                        tools={allTools}
                        resources={allResources}
                        prompts={allPrompts}
                        onToolClick={setSelectedTool}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                        onResourceClick={setSelectedResource}
                        onPromptClick={setSelectedPrompt}
                    />
                )}

                {/* Ask Akto — expands to half-screen as user types */}
                <AiChatSection
                    placeholder="Ask about this MCP server's tools and risks..."
                    resetKey={agent?.endpoint}
                />
            </div>
        </div>
    );
}
