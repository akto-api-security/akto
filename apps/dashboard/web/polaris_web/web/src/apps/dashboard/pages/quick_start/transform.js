import {Avatar, Badge} from "@shopify/polaris"
import PostmanSource from "./components/PostmanSource"
import BurpSource from "./components/BurpSource"
import HybridSaasSource from "./components/HybridSaasSource"
import AgenticShield from "./components/AgenticShield"
import AwsSource from "./components/AwsSource"
import FargateSource from "./components/FargateSource"
import Kubernetes from "./components/Kubernetes"
import BannerComponent from "./components/shared/BannerComponent"
import OpenApiSource from "./components/OpenApiSource"
import ApiInventoryFromSourceCode from "./components/ApiInventoryFromSourceCode"
import func from "@/util/func"
import AddOnComponenet from "./components/shared/AddOnComponenet"
import BitBucketSource from "./components/BitBucketSource"
import GithubSource from "./components/GithubSource"
import AktoJax from "./components/AktoJax"  
import McpScan from "./components/McpScan" 
import AiAgentScan from "./components/AiAgentScan"
import { isGenAISecurityCategory, isMCPSecurityCategory, isAgenticSecurityCategory, isDastCategory, isApiSecurityCategory } from "../../../main/labelHelper"
import McpRecon from "./components/McpRecon"
import McpProxy from "./components/McpProxy"
import AwsLogAccountComponent from "./components/shared/AwsLogAccountComponent"
import McpGateway from "./McpGateway"
import AIAgentsGateway from "./AIAgentsGateway"
import ImpervaImport from "./components/ImpervaImport"
import BrowserExtension from "./components/BrowserExtension"

const mirroringObj = {
    icon: '/public/aws.svg',
    label: "AWS Mirroring",
    text: "You can deploy Akto in AWS and collect traffic through traffic mirroring.",
    docsUrl: 'https://docs.akto.io/traffic-connector/mirroring/amazon-aws',
    key: "AWS",
    component: <AwsSource />
}
const apigeeObj = {
    icon: '/public/apigee.svg',
    label: "Apigee",
    text: "Apigee setup is recommended if you are using Google's Apigee API Management platform to design, secure, and scale your APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/gcp-services/connect-akto-with-apigee',
    key: "Apigee",
    component: <AddOnComponenet/>
}

const iisObj = {
    icon: '/public/iis.svg',
    label: "IIS",
    text: "IIS setup is recommended if you are using Microsoft's Internet Information Services to host your applications.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-iis',
    key: "IIS",
    component: <AddOnComponenet/>
}

const githubObj = {
    icon: '/public/github.svg',
    label: "GitHub",
    text: "We recommend setting up GitHub if you want to connect and retrieve APIs directly from your source code hosted on GitHub.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    key: "GitHub",
    component: <GithubSource/>
}

const gitlabObj = {
    icon: '/public/gitlab.svg',
    label: "GitLab",
    text: "We recommend setting up GitLab if you want to connect and retrieve APIs directly from your source code hosted on GitLab.",
    docsUrl: 'https://docs.akto.io/traffic-connector/source-code/gitlab',
    key: "GitLab",
    component: <AddOnComponenet/>
}

const bitbucketObj = {
    icon: '/public/bitbucket.svg',
    label: "Bitbucket",
    text: "We recommend setting up Bitbucket if you want to connect and retrieve APIs directly from your source code hosted on Bitbucket.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    key: "Bitbucket",
    component: <BitBucketSource/>
}

const azureObj = {
    icon: '/public/azure.svg',
    label: "Azure API Gateway",
    text: "Azure API Gateway setup is recommended if you are using Azure's API Management service to manage, secure, and analyze your APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-azure-api-management',
    key: "Azure",
    component: <AddOnComponenet/>
}

const cloudflareObj = {
    icon: '/public/cloudflare.svg',
    label: "Cloudflare",
    text: "You should use Cloudflare as a traffic connector if you are leveraging Cloudflare's CDN and security features to manage and optimize your API traffic.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-cloudflare',
    key: "Cloudflare",
    component: <AddOnComponenet/>
}

const f5Obj = {
    icon: '/public/f5.svg',
    label: "F5",
    text: "F5 setup is recommended if you are using F5's BIG-IP as an API gateway or load balancer to manage and control your API traffic.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-f5',
    key: "F5",
    component: <AddOnComponenet/>
}

const goObj = {
    icon: '/public/go.svg',
    label: "Go",
    text: "Use where Go-based services are deployed.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    key: "Go",
    component: <AddOnComponenet/>
}

const haproxyObj = {
    icon: '/public/haproxy.svg',
    label: "HA Proxy",
    text: "HA Proxy should be used as a traffic connector if you are leveraging HA Proxy for load balancing, high availability, and proxying HTTP and TCP-based applications.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-haproxy',
    key: "HAproxy",
    component: <AddOnComponenet/>
}

const javaObj = {
    icon: '/public/java.svg',
    label: "Java",
    text: "You can use Akto's Java agent to capture API traffic directly from your Java applications.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    key: "Java",
    component: <AddOnComponenet/>
}

const kongmeshObj = {
    icon: '/public/kongmesh.svg',
    label: "Kong Mesh",
    text: "Use this set-up if you are utilizing Kong's service mesh capabilities to manage and secure your microservices and APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-kong-mesh',
    key: "Kong Mesh",
    component: <AddOnComponenet/>
}

const layer7Obj = {
    icon: '/public/layer7.svg',
    label: "Layer 7",
    text: "Layer7 is recommended if you are using CA Technologies' Layer7 API Management for securing and managing your APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-layer7-api-gateway',
    key: "Layer 7",
    component: <AddOnComponenet/>
}

const nodejsObj = {
    icon: '/public/nodejs.svg',
    label: "NodeJS",
    text: "This setup is ideal for environments where NodeJS-based services are deployed, ensuring seamless integration and real-time traffic monitoring.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    key: "NodeJS",
    component: <AddOnComponenet/>
}

const openshiftObj = {
    icon: '/public/openshift.svg',
    label: "OpenShift",
    text: "OpenShift should be used as a traffic connector if you are deploying and managing containerized applications using OpenShift.",
    docsUrl: 'https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/openshift-deploy',
    key: "OpenShift",
    component: <AddOnComponenet/>
}

const threescaleObj = {
    icon: '/public/threescale.svg',
    label: "3Scale",
    text: "This setup is recommended if your APIs are managed by 3scale.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-3scale',
    key: "3Scale",
    component: <AddOnComponenet/>
}

const beanStalkObj = {
    icon: '/public/beanstalk.svg',
    label: "AWS Beanstalk",
    text: "You can deploy Akto in AWS and collect traffic through mirroring on your AWS Beanstalk setup.",
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/aws-beanstalk',
    component: <AddOnComponenet/>,
    key: "Bean Stalk",
}

const eksObj = {
    icon: '/public/eks.svg',
    label: 'AWS EKS',
    text: "You can deploy Akto in AWS and collect traffic through a daemonset on your AWS EKS configuration.",
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/aws-eks',
    component: <Kubernetes docsUrl= 'https://docs.akto.io/traffic-connector/aws-services/aws-eks' bannerTitle="Setup using AWS EKS"/>,
    key: "EKS"
}

const fargateObj = {
    icon: '/public/fargate.svg',
    label: 'AWS Fargate',
    text: "AWS Fargate allows you to use Amazon ECS to run containers without having to manage servers or clusters of Amazon EC2 instances.", 
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/aws-fargate',
    component: <FargateSource docsUrl='https://docs.akto.io/traffic-connector/aws-services/aws-fargate' bannerTitle="Setup using Fargate" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/aws-fargate#add-akto-traffic-collector-container"/>,
    key: "FARGATE"
}
const burpObj = {
    icon: '/public/burp.svg',
    label: "Burp Suite",
    text: "You can deploy Akto on your machine and download Akto's Burp extension to collect API traffic.",   
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/burp-suite',
    key: "BURP",
    component : <BurpSource/>
}

const aktoJaxObj = {
    icon: '/public/aktojax.svg',
    label: "Akto DAST",
    text: "You can deploy Akto's DAST on your machine to automatically scan and collect API endpoints and traffic data.",   
    docsUrl: 'https://docs.akto.io/dast/akto-dast',
    key: "AKTODAST",
    component : <AktoJax />
}

const geminiObj = {
    icon: '/public/gemini.svg',
    label: "Gemini",
    text: "Import Google Gemini models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "GEMINI",
    component : <AiAgentScan
        description="Import your Gemini models, seamlessly in AKTO."
        defaultRequestBody={{
            "contents": [{"parts": [{"text": "Why is the sky blue?"}]}]
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const openAIObj = {
    icon: '/public/openai.svg',
    label: "OpenAI",
    text: "Import OpenAI models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "OPENAI",
    component : <AiAgentScan
        description="Import your OpenAI models, seamlessly in AKTO."
        defaultRequestBody={{
            "model": "gpt-3.5-turbo",
            "messages": [{"role": "user", "content": "Why is the sky blue?"}]
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const claudeObj = {
    icon: '/public/claude.svg',
    label: "Claude",
    text: "Import Anthropic Claude models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "CLAUDE",
    component : <AiAgentScan
        description="Import your Claude models, seamlessly in AKTO."
        defaultRequestBody={{
            "model": "claude-3-opus-20240229",
            "messages": [{"role": "user", "content": "Why is the sky blue?"}],
            "max_tokens": 1024
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const deepseekObj = {
    icon: '/public/deepseek.svg',
    label: "DeepSeek",
    text: "Import DeepSeek models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "DEEPSEEK",
    component : <AiAgentScan
        description="Import your DeepSeek models, seamlessly in AKTO."
        defaultRequestBody={{
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": "Why is the sky blue?"}]
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const llamaObj = {
    icon: '/public/ollama.svg',
    label: "Llama",
    text: "Import Meta Llama models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "LLAMA",
    component : <AiAgentScan
        description="Import your Llama models, seamlessly in AKTO."
        defaultRequestBody={{
            "model": "llama3.2",
            "prompt": "Why is the sky blue?"
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const grokObj = {
    icon: '/public/grok.svg',
    label: "Grok",
    text: "Import xAI Grok models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "GROK",
    component : <AiAgentScan
        description="Import your Grok models, seamlessly in AKTO."
        defaultRequestBody={{
            "model": "grok-beta",
            "messages": [{"role": "user", "content": "Why is the sky blue?"}]
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

const customAIObj = {
    icon: '/public/ai_custom.svg',
    label: "Bring Your Own Model",
    text: "Import your custom AI models seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-security',
    key: "CUSTOM_AI",
    component : <AiAgentScan
        description="Import your custom AI models, seamlessly in AKTO."
        defaultRequestBody={{
            "prompt": "Why is the sky blue?"
        }}
        docsLink='https://docs.akto.io/ai-security'
    />
}

// AI Agent Platform Objects
const awsBedrockObj = {
    icon: '/public/aws_bedrock.svg',
    label: "AWS Bedrock",
    text: "Import AWS Bedrock AI agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "AWS_BEDROCK",
    component : <AiAgentScan
        description="Import your AWS Bedrock AI agents, seamlessly in AKTO."
        defaultRequestBody={{
            "modelId": "anthropic.claude-v2",
            "contentType": "application/json",
            "accept": "application/json",
            "body": {
                "prompt": "\n\nHuman: Why is the sky blue?\n\nAssistant:",
                "max_tokens_to_sample": 300
            }
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const aiAgentGlobalProxy = {
    icon: '/public/aws_bedrock.svg',
    label: "AI Agent Global Proxy",
    text: "A publicly hosted secure gateway that enforces guardrails and advanced threat protection for all requests to your public AI agents, ensuring safe and compliant communication.",
    docsUrl: 'https://docs.akto.io/akto-agent-proxy',
    key: "AI_AGENT_GLOBAL_PROXY",
    component : <AIAgentsGateway />
}

const aiAgentGateway = {
    icon: '/public/aws_bedrock.svg',
    label: "AI Agent Gateway",
    text: "AI agent proxy gateway to be deployed on premise for securing AI agents in your network",
    docsUrl: 'https://docs.akto.io/akto-agent-proxy',
    key: "AI_AGENT_GATEWAY",
    component : <AIAgentsGateway />
}

const azureAIFoundryObj = {
    icon: '/public/azure_ai.svg',
    label: "Azure AI Foundry",
    text: "Import Azure AI Foundry agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "AZURE_AI_FOUNDRY",
    component : <AiAgentScan
        description="Import your Azure AI Foundry agents, seamlessly in AKTO."
        defaultRequestBody={{
            "messages": [{"role": "user", "content": "Why is the sky blue?"}],
            "max_tokens": 100,
            "temperature": 0.7
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const databricksObj = {
    icon: '/public/databricks.svg',
    label: "Databricks",
    text: "Import Databricks AI agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "DATABRICKS",
    component : <AiAgentScan
        description="Import your Databricks AI agents, seamlessly in AKTO."
        defaultRequestBody={{
            "prompt": "Why is the sky blue?",
            "max_tokens": 100
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const googleVertexAIObj = {
    icon: '/public/vertex_ai.svg',
    label: "Google Vertex AI",
    text: "Import Google Vertex AI agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "GOOGLE_VERTEX_AI",
    component : <AiAgentScan
        description="Import your Google Vertex AI agents, seamlessly in AKTO."
        defaultRequestBody={{
            "instances": [{"content": "Why is the sky blue?"}],
            "parameters": {
                "candidateCount": 1,
                "maxOutputTokens": 256,
                "temperature": 0.2
            }
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const ibmWatsonxObj = {
    icon: '/public/ibm_watsonx.svg',
    label: "IBM Watsonx",
    text: "Import IBM Watsonx AI agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "IBM_WATSONX",
    component : <AiAgentScan
        description="Import your IBM Watsonx AI agents, seamlessly in AKTO."
        defaultRequestBody={{
            "input": "Why is the sky blue?",
            "model_id": "meta-llama/llama-2-70b-chat",
            "parameters": {
                "max_new_tokens": 100,
                "temperature": 0.7
            }
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const customAgentObj = {
    icon: '/public/ai_agent_custom.svg',
    label: "Bring Your Own AI Agent",
    text: "Import your custom AI agents seamlessly into AKTO.",
    docsUrl: 'https://docs.akto.io/ai-agent-security',
    key: "CUSTOM_AGENT",
    component : <AiAgentScan
        description="Import your custom AI agents, seamlessly in AKTO."
        defaultRequestBody={{
            "query": "Why is the sky blue?"
        }}
        docsLink='https://docs.akto.io/ai-agent-security'
    />
}

const mcpWrapperObj = {
    icon: '/public/mcp.svg',
    label: "MCP Endpoint Shield",
    text: "MCP Endpoint Shield provides runtime security and auto-discovery of local MCP servers configured on your machine, requiring no changes to your setup.",
    docsUrl: 'https://docs.akto.io/mcp-endpoint-shield',
    key: "MCP_ENDPOINT_SHIELD",
    component: <AddOnComponenet/>
}

const agenticShieldObj = {
    icon: '/public/mcp.svg',
    label: "Agentic Shield",
    text: "Agentic Shield provides runtime protection and discovery of LLMs/AI Agents on your local environment.",
    docsUrl: 'https://docs.akto.io/agentic-shield',
    key: "AGENTIC_SHIELD",
    component: <AgenticShield/>
}

const mcpScanObj = {
    icon: '/public/mcp.svg',
    label: "MCP Import",
    text: "You can use Akto's MCP import to capture traffic and instantly send it to your dashboard for real-time insights.",   
    docsUrl: 'https://docs.akto.io/mcp-import',
    key: "MCP_SCAN",
    component : <McpScan/>
}

const mcpReconObj = {
    icon: '/public/mcp.svg',
    label: "MCP Recon",
    text: "Use our MCP Recon feature to discover and catalog MCP-compatible servers across your network IP ranges for comprehensive security analysis.",
    docsUrl: 'https://docs.akto.io/mcp-recon',
    key: "MCP_RECON",
    component : <McpRecon/>
}

const mcpProxyObj = {
    icon: '/public/mcp.svg',
    label: "MCP Global Proxy",
    text: "A publicly hostedsecure gateway that enforces guardrails and advanced threat protection for all requests to your public MCP servers, ensuring safe and compliant communication.",
    docsUrl: 'https://docs.akto.io/akto-mcp-proxy',
    key: "MCP_PROXY",
    component : <McpProxy/>
}

const mcpGateway = {
    icon: '/public/mcp.svg',
    label: "MCP Gateway",
    text: "MCP proxy gateway to be deployed on premise for securing MCP servers in your network.",
    docsUrl: 'https://docs.akto.io/akto-mcp-proxy',
    key: "MCP_GATEWAY",
    component : <McpGateway/>
}

const dockerObj = {
    icon: '/public/docker.svg',
    label: "Docker",
    text: "This setup is recommended only if other setups for AWS or GCP don't work.",
    docsUrl: 'https://docs.akto.io/traffic-connector/virtual-machines/docker',
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/virtual-machines/docker" bannerTitle="Setup using Docker"innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/docker#adding-akto-docker-service"/>,
    key: "DOCKER"
}

const envoyObj = {
    icon: '/public/envoy.svg',
    label: 'Envoy',
    text: 'Akto-Envoy setup is recommended if your APIs are routed by Envoy.',
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/envoy',
    key: "ENVOY",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/api-gateways/envoy" bannerTitle="Setup using Envoy" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/envoy#adding-akto-traffic-collector"/>,
}

const gcpObj = {
    icon: '/public/gcp.svg',
    label: 'GCP Mirroring',
    text: 'This setup only takes ten minutes. Once you connect GCP, Akto will process GCP traffic to create an API Inventory in real time.',
    docsUrl: 'https://docs.akto.io/traffic-connector/mirroring/google-cloud-gcp',
    key: "GCP",
    component: <BannerComponent title="Setup using GCP Mirroring" docsUrl="https://docs.akto.io/traffic-connector/mirroring/google-cloud-gcp"
                    content="Use Google packet mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs." />
}

const harFileUploadObj = {
    icon: '/public/HAR.svg',
    label: 'Har File Upload',
    text: "For a very quick view of your inventory, you can upload a HAR file that contains traffic to Akto.",
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/har-file-upload',
    key: "HAR",
    component: <BannerComponent title="Upload .har file" docsUrl="https://docs.akto.io/traffic-connector/manual/har-file-upload"
                    content=" You can use this method if you quickly want to try out Akto. Akto can process HAR (Http ARchive) files and populate inventory from it." />
}

const kongObj = {
    icon: '/public/kong.svg',
    label: 'Kong',
    text: 'Kong Gateway is an open source API gateway, built for multi-cloud and hybrid, and optimized for microservices and distributed architectures.',
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/kong',
    key: "KONG",
    component: <AddOnComponenet/>
}

const kubernetesObj = {
    icon: '/public/kubernetes.svg',
    label: 'Kubernetes Daemonset',
    text: 'You can deploy Akto in Kubernetes and collect traffic through a daemonset on your Kubernetes configuration.',
    docsUrl: 'https://docs.akto.io/traffic-connector/kubernetes/kubernetes',
    key: "KUBERNETES",
    component: <Kubernetes docsUrl="https://docs.akto.io/traffic-connector/kubernetes/kubernetes" bannerTitle="Setup using Kubernetes Daemonset"/>
}

const nginxObj = {
    icon: '/public/Nginx.svg',
    label: 'NGINX',
    text: 'This setup is recommended if your APIs are routed by NGINX.',
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/nginx',
    key: "NGINX",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/api-gateways/nginx" bannerTitle="Setup using NGINX" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/nginx#add-nginx-module"/>
}

const postmanObj = {
    icon: '/public/postman.svg',
    label: 'Postman',
    text: 'This setup is recommended if you have updated API collections maintained in Postman.',
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/postman',
    component: <PostmanSource/>,
    key: "POSTMAN"
}

const openApiObj = {
    icon: '/public/openApi.svg',
    label: 'OpenAPI',
    text: 'Upload Open API/Swagger specification file to Akto to create an API inventory.',
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/openapi',
    component: <OpenApiSource/>,
    key: "OPENAPI"
}

const impervaImportObj = {
    icon: '/public/imperva.svg',
    label: 'Imperva Import',
    text: 'Upload Imperva specification file to Akto to create an API inventory.',
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/imperva',
    component: <ImpervaImport/>,
    key: "IMPERVA_IMPORT"
}

const wsdlApiObj = {
    icon: '/public/xml_api.svg',
    label: 'SOAP API',
    text: 'Upload WSDL file using Postman to Akto to create an API inventory.',
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/wsdl',
    component: <PostmanSource/>,
    key: "SOAPAPI"
}

const graphqlApiIObj = {
    icon: '/public/graphql_api.svg',
    label: 'GraphQL Schema',
    text: 'Upload GraphQL Schema file using Postman to Akto to create an API inventory.',
    docsUrl: 'https://docs.akto.io/traffic-connector/manual/graphql',
    component: <PostmanSource/>,
    key: "GRAPHQLAPI"
}

const tcpObj = {
    icon: '/public/TCP.svg',
    label: 'TCP Agent',
    text: ' This setup is recommended only if other setups for AWS or GCP do not work.',
    docsUrl: 'https://docs.akto.io/traffic-connector/virtual-machines/tcp-agent',
    key: "NGINX",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/virtual-machines/tcp-agent" bannerTitle="Setup using TCP Agent" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/tcp-agent#adding-akto-traffic-collector-container" />
}

const hybridSaasObj = {
    icon: '/public/aws.svg',
    label: "Hybrid Saas",
    text: "You can just deploy our agent in your cloud and send the data to Akto's Saas Setup",
    docsUrl: 'https://docs.akto.io/getting-started/traffic-processor/hybrid-saas',
    key: "Hybrid Saas",
    component : <HybridSaasSource/>
}

const apiInventoryFromSourceCodeObj = {
    icon: '/public/favicon.svg',
    badge: "Beta",
    label: 'API inventory from source code',
    text: 'Create API inventory from your source code using our code analysis tool.',
    docsUrl: 'https://docs.akto.io/api-inventory/concepts/api-inventory-from-source-code',
    component: <ApiInventoryFromSourceCode/>,
    key: "API_INVENTORY_FROM_SOURCE_CODE"
}

const ebpfObj = {
    icon: '/public/ebpf.svg',
    label: 'eBPF',
    badge: "Recommended",
    text: 'Akto-eBPF setup is recommended for mTLS systems when TLS termination happens at a proxy.',
    docsUrl: 'https://docs.akto.io/traffic-connector/ebpf/ebpf',
    key: "EBPF",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/ebpf/ebpf" bannerTitle="Setup using eBPF" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/ebpf#adding-akto-traffic-collector"/>,
}

const ebpfMTLSObj = {
    icon: '/public/ebpf.svg',
    label: 'eBPF on mTLS',
    text: 'Akto-eBPF-mTLS setup is recommended for mTLS systems where TLS termination occurs at the application.',
    docsUrl: 'https://docs.akto.io/traffic-connector/ebpf/ebpf-mtls',
    key: "EBPF_MTLS",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connector/ebpf/ebpf-mtls" bannerTitle="Setup using eBPF mTLS" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/ebpf-mtls#adding-akto-traffic-collector"/>,
}

const istioObj = {
    icon: '/public/istio.svg',
    label: 'Istio',
    text: 'Akto-Istio setup is recommended if your APIs are routed by Istio.',
    docsUrl: 'https://docs.akto.io/getting-started/traffic-processor',
    key: "ISTIO",
    component: <FargateSource docsUrl="https://docs.akto.io/getting-started/traffic-processor" bannerTitle="Setup using Istio" innerUrl="https://docs.akto.io/traffic-connections/traffic-data-sources/envoy-1#adding-akto-traffic-collector"/>,
}

const pythonObj = {
    icon: '/public/python.svg',
    label: 'Python',
    text: 'Use where Python-based services are deployed.',
    docsUrl: 'https://docs.akto.io/traffic-connector/',
    component: <AddOnComponenet/>,
    key: "Python"
}

const awsApiGatewayObj = {
    icon: '/public/aws_api_gateway.svg',
    label: "AWS API Gateway",
    text: "Akto-AWS-API-Gateway setup is recommended if you are using AWS API Gateway.",
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/aws-api-gateway',
    component: <AddOnComponenet featureLabel="AWS_API_GATEWAY" featureComponent={<AwsLogAccountComponent/>}/>,
    key: "AWS_API_GATEWAY",
}

const awsLambdaObj = {
    icon: '/public/aws_lambda.svg',
    label: "AWS Lambda",
    text: "Akto AWS Lambda setup is recommended if you are using AWS Lambda functions to handle your APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/connect-akto-with-aws-lambda',
    component: <AddOnComponenet/>,
    key: "AWS_LAMBDA",
}

const ecsObj = {
    icon: '/public/ecs.svg',
    label: "AWS ECS",
    text: "You can deploy Akto in AWS and collect traffic through containers on your AWS ECS cluster.",
    docsUrl: 'https://docs.akto.io/traffic-connector/aws-services/aws-ecs',
    component: <AddOnComponenet/>,
    key: "ECS",
}

const mulesoftObj = {
    icon: '/public/mulesoft.svg',
    label: "MuleSoft",
    text: "Mulesoft setup is recommended if you are using API management and ESB capabilities to manage, secure, and analyze your APIs.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/mulesoft-flex',
    component: <AddOnComponenet/>,
    key: "mulesoft",
}

const citrixObj = {
    icon: '/public/citrix.svg',
    label: "Citrix",
    text: "Citrix setup is recommended if you are using ADC (NetScaler) to manage, secure, and optimize your API traffic.",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-citrix',
    component: <AddOnComponenet/>,
    key: "citrix",
}

const azureappserviceObj = {
    icon: '/public/azureappservice.svg',
    label: "Azure App Services",
    text: "Azure App Services setup is recommended if you are using Microsoft's web app service with sidecar containers to collect and analyze your API traffic.",
    docsUrl: 'https://docs.akto.io/traffic-connector/azure-services/azure-app-service',
    component: <AddOnComponenet/>,
    key: "azureappservice",
}

const ibmapiconnectObj = {
    icon: '/public/ibmapiconnect.svg',
    label: "IBM API Connect",
    text: "This setup is recommended if your APIs are managed by IBM API Connect",
    docsUrl: 'https://docs.akto.io/traffic-connector/api-gateways/connect-akto-with-ibm-connect',
    component: <AddOnComponenet/>,
    key: "IBM API connect",
}

const gcrfuncObj = {
    icon: '/public/gcrfunc.svg',
    label: "Google Cloud Run Functions",
    text: "This setup is recommended if your applications are deployed using Google Cloud Run Functions.",
    docsUrl: 'https://docs.akto.io/traffic-connector/gcp-services/connect-akto-with-google-cloud-run-functions',
    component: <AddOnComponenet/>,
    key: "Google Cloud Run Functions",
}

const gcrObj = {
    icon: '/public/gcr.svg',
    label: "Google Cloud Run",
    text: "Google Cloud Run setup is recommended if you are using Google's serverless platform.",
    docsUrl: 'https://docs.akto.io/traffic-connector/gcp-services/connect-akto-with-google-cloud-run',
    component: <AddOnComponenet/>,
    key: "Google Cloud Run",
}

const gkeObj = {
    icon: '/public/gke.svg',
    label: "GKE",
    text: "This setup is recommended if you are running containerized applications on Google Kubernetes Engine.",
    docsUrl: 'https://docs.akto.io/traffic-connector/gcp-services/connect-akto-with-gke',
    component: <AddOnComponenet/>,
    key: "GKE",
}

const aksObj = {
    icon: '/public/aks.svg',
    label: "AKS",
    text: "This setup is recommended if you are deploying applications on Azure Kubernetes Service.",
    docsUrl: 'https://docs.akto.io/traffic-connector/azure-services/connect-akto-with-aks',
    component: <AddOnComponenet/>,
    key: "AKS",
}

const azureopenshiftObj = {
    icon: '/public/openshift.svg',
    label: "Azure Openshift",
    text: "Azure OpenShift setup is recommended if your containerized applications are running on Azure Red Hat OpenShift.",
    docsUrl: 'https://docs.akto.io/traffic-connector/azure-services/connect-akto-with-azure-openshift',
    component: <AddOnComponenet/>,
    key: "Azure Openshift",
}

const azurecontainerappObj = {
    icon: '/public/aca.svg',
    label: "Azure Container App",
    text: "This setup is recommended if your applications are deployed using Azure Container Apps.",
    docsUrl: 'https://docs.akto.io/traffic-connector/azure-services/connect-akto-with-azure-container-app',
    component: <AddOnComponenet/>,
    key: "Azure Container App",
}

const azurefuncObj = {
    icon: '/public/af.svg',
    label: "Azure Functions",
    text: "Azure Functions setup is recommended if you are using Azure's serverless compute service.",
    docsUrl: 'https://docs.akto.io/traffic-connector/azure-services/connect-akto-with-azure-functions',
    component: <AddOnComponenet/>,
    key: "Azure Functions",
}

const cloudflareWarpObj = {
    icon: '/public/cloudflare.svg',
    label: "Cloudflare WARP",
    text: "Cloudflare WARP setup is recommended, if you use Cloudflare WARP for secure, accelerated device-to-internet connectivity.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    component: <AddOnComponenet/>,
    key: "CLOUDFLARE_WARP",
}

const zscalerObj = {
    icon: '/public/zscaler_logo.svg',
    label: "Zscaler",
    text: "Zscaler setup is recommended,if your organization uses Zscaler for secure, zero-trust internet and app access.",
    docsUrl: 'https://docs.akto.io/traffic-connector',
    component: <AddOnComponenet/>,
    key: "ZSCALER",
}
const chromeExtensionObj = {
    icon: '/public/chrome.svg',
    label: "Chrome Extension",
    text: "A browser extension that enforces your company's AI usage policies when interacting with ChatGPT and other LLM preventing sensitive data leaks, applying guardrails, and logging policy-relevant events.",
    docsUrl: 'https://docs.akto.io/browser-extension',
    component: <BrowserExtension browserName="Google chrome"/>,
    key: "CHROME_BROWSER_EXTENSION",
}

const firefoxExtensionObj = {
    icon: '/public/firefox.svg',
    label: "Firefox Extension",
    text: "A browser extension that enforces your company's AI usage policies when interacting with ChatGPT and other LLM preventing sensitive data leaks, applying guardrails, and logging policy-relevant events.",
    docsUrl: 'https://docs.akto.io/browser-extension',
    component: <BrowserExtension browserName="Firefox"/>,
    key: "FIREFOX_BROWSER_EXTENSION",
}

const safariExtensionObj = {
    icon: '/public/safari.svg',
    label: "Safari Extension",
    text: "A browser extension that enforces your company's AI usage policies when interacting with ChatGPT and other LLM preventing sensitive data leaks, applying guardrails, and logging policy-relevant events.",
    docsUrl: 'https://docs.akto.io/browser-extension',
    component: <BrowserExtension browserName="Safari"/>,
    key: "SAFARI_BROWSER_EXTENSION",
}


const quick_start_policy_lines= [
    `{`,
    `    "Version": "2012-10-17",`,
    `    "Statement": [`,
    `        {`,
    `            "Sid": "1",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:DescribePolicies",`,
    `                "autoscaling:DescribeAutoScalingGroups",`,
    `                "autoscaling:DescribeScalingActivities",`,
    `                "autoscaling:DescribeLaunchConfigurations",`,
    `                "ec2:DescribeSubnets",`,
    `                "ec2:DescribeKeyPairs",`,
    `                "ec2:DescribeSecurityGroups"`,
    `            ],`,
    `            "Resource": "*"`,
    `        },`,
    `        {`,
    `            "Sid": "2",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:PutScalingPolicy",`,
    `                "autoscaling:UpdateAutoScalingGroup",`,
    `                "autoscaling:CreateAutoScalingGroup",`,
    `                "autoscaling:StartInstanceRefresh"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/DASHBOARD_STACK_NAME*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/MIRRORING_STACK_NAME*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "3",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:CreateLaunchConfiguration"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/MIRRORING_STACK_NAME*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/DASHBOARD_STACK_NAME*"`,
    `             ]`,
    `        },`,
    `        {`,
    `            "Sid": "4",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "cloudformation:CreateStack",`,
    `                "cloudformation:DescribeStackResources",`,
    `                "cloudformation:DescribeStacks"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/MIRRORING_STACK_NAME/*",`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/DASHBOARD_STACK_NAME/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "5",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `               "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "6",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "ec2:CreateSecurityGroup",`,
    `                "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:vpc/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "7", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:RevokeSecurityGroupEgress", `,
    `                "ec2:AuthorizeSecurityGroupEgress", `,
    `                "ec2:RevokeSecurityGroupIngress", `,
    `                "ec2:AuthorizeSecurityGroupIngress", `,
    `                "ec2:CreateTags"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*", `,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "8", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:CreateTrafficMirrorTarget"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:traffic-mirror-target/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "9", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:ModifyTrafficMirrorFilterNetworkServices", `,
    `                "ec2:CreateTrafficMirrorFilter"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:traffic-mirror-filter/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "10", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:DescribeLoadBalancers", `,
    `                "elasticloadbalancing:DescribeListeners", `,
    `                "elasticloadbalancing:DescribeTargetGroups", `,
    `                "ec2:DescribeVpcs"`,
    `            ], `,
    `            "Resource": `,
    `                "*"`,
    `        }, `,
    `        {`,
    `            "Sid": "11", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:CreateListener", `,
    `                "elasticloadbalancing:ModifyLoadBalancerAttributes", `,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateLoadBalancer"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:loadbalancer/net/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "12", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateTargetGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*", `,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "13", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:GetFunction", `,
    `                "lambda:CreateFunction", `,
    `                "lambda:DeleteFunction", `,
    `                "lambda:UpdateFunctionConfiguration", `,
    `                "lambda:UpdateFunctionCode", `,
    `                "lambda:GetFunctionCodeSigningConfig", `,
    `                "lambda:InvokeFunction", `,
    `                "lambda:ListFunctions", `,
    `                "lambda:TagResource", `,
    `                "lambda:AddPermission"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "14", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:ListFunctions" `,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "15", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "logs:CreateLogStream", `,
    `                "logs:PutRetentionPolicy", `,
    `                "logs:TagResource", `,
    `                "logs:CreateLogGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/MIRRORING_STACK_NAME*", `,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/MIRRORING_STACK_NAME*:log-stream:"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "16", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateRole", `,
    `                "iam:PutRolePolicy", `,
    `                "iam:GetRole", `,
    `                "iam:GetRolePolicy", `,
    `                "iam:TagRole", `,
    `                "iam:PassRole"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/service-role/*akto*", `,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "17", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateInstanceProfile", `,
    `                "iam:GetInstanceProfile"`,
    `            ], `,
    `            "Resource": "arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/MIRRORING_STACK_NAME*"`,
    `        }, `,
    `        {`,
    `            "Sid": "18", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:AddRoleToInstanceProfile"`,
    `            ], `,
    `            "Resource": ["arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/MIRRORING_STACK_NAME*", "arn:aws:iam::AWS_ACCOUNT_ID:role/MIRRORING_STACK_NAME-*"]`,
    `        }, `,
    `        {`,
    `            "Sid": "19", `,
    `            "Effect": "Allow", `,
    `            "Action": "s3:GetObject", `,
    `            "Resource": [`,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/get-akto-setup-details.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/create-mirror-session.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/configure_security_groups.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/mirroring-collections-split.zip"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "20", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "events:DescribeRule", `,
    `                "events:PutRule", `,
    `                "events:PutTargets"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:events:AWS_REGION:AWS_ACCOUNT_ID:rule/MIRRORING_STACK_NAME-PeriodicRule"`,
    `            ]`,
    `        } `,
    `    ]`,
    `}`
]

const  quick_start_policy_lines_kubernetes = [
    `{`,
    `    "Version": "2012-10-17",`,
    `    "Statement": [`,
    `        {`,
    `            "Sid": "1",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:DescribePolicies",`,
    `                "autoscaling:DescribeAutoScalingGroups",`,
    `                "autoscaling:DescribeScalingActivities",`,
    `                "autoscaling:DescribeLaunchConfigurations",`,
    `                "ec2:DescribeSubnets",`,
    `                "ec2:DescribeKeyPairs",`,
    `                "cloudformation:DescribeStacks",`,
    `                "cloudformation:ListStacks",`,
    `                "ec2:DescribeSecurityGroups"`,
    `            ],`,
    `            "Resource": "*"`,
    `        },`,
    `        {`,
    `            "Sid": "2",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:PutScalingPolicy",`,
    `                "autoscaling:UpdateAutoScalingGroup",`,
    `                "autoscaling:CreateAutoScalingGroup",`,
    `                "autoscaling:StartInstanceRefresh"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "3",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:CreateLaunchConfiguration"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*"`,
    `             ]`,
    `        },`,
    `        {`,
    `            "Sid": "4",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "cloudformation:CreateStack",`,
    `                "cloudformation:DescribeStackResources"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*",`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "5",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `               "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "6",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "ec2:CreateSecurityGroup",`,
    `                "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:vpc/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "7", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:RevokeSecurityGroupEgress", `,
    `                "ec2:AuthorizeSecurityGroupEgress", `,
    `                "ec2:RevokeSecurityGroupIngress", `,
    `                "ec2:AuthorizeSecurityGroupIngress", `,
    `                "ec2:CreateTags"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*", `,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "10", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:DescribeLoadBalancers", `,
    `                "elasticloadbalancing:DescribeListeners", `,
    `                "elasticloadbalancing:DescribeTargetGroups", `,
    `                "ec2:DescribeVpcs"`,
    `            ], `,
    `            "Resource": `,
    `                "*"`,
    `        }, `,
    `        {`,
    `            "Sid": "11", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:CreateListener", `,
    `                "elasticloadbalancing:ModifyLoadBalancerAttributes", `,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateLoadBalancer"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:loadbalancer/net/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "12", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateTargetGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*", `,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "13", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:GetFunction", `,
    `                "lambda:CreateFunction", `,
    `                "lambda:DeleteFunction", `,
    `                "lambda:UpdateFunctionConfiguration", `,
    `                "lambda:UpdateFunctionCode", `,
    `                "lambda:GetFunctionCodeSigningConfig", `,
    `                "lambda:InvokeFunction", `,
    `                "lambda:ListFunctions", `,
    `                "lambda:TagResource", `,
    `                "lambda:AddPermission"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "14", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:ListFunctions" `,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "15", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "logs:CreateLogStream", `,
    `                "logs:PutRetentionPolicy", `,
    `                "logs:TagResource", `,
    `                "logs:CreateLogGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*", `,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*:log-stream:"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "16", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateRole", `,
    `                "iam:PutRolePolicy", `,
    `                "iam:GetRole", `,
    `                "iam:GetRolePolicy", `,
    `                "iam:TagRole", `,
    `                "iam:PassRole"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/service-role/*akto*", `,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "17", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateInstanceProfile", `,
    `                "iam:GetInstanceProfile"`,
    `            ], `,
    `            "Resource": "arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*"`,
    `        }, `,
    `        {`,
    `            "Sid": "18", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:AddRoleToInstanceProfile"`,
    `            ], `,
    `            "Resource": ["arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*", "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"]`,
    `        }, `,
    `        {`,
    `            "Sid": "19", `,
    `            "Effect": "Allow", `,
    `            "Action": "s3:GetObject", `,
    `            "Resource": [`,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/get-akto-setup-details.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/create-mirror-session.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/configure_security_groups.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/mirroring-collections-split.zip"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "20", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "events:DescribeRule", `,
    `                "events:PutRule", `,
    `                "events:PutTargets"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:events:AWS_REGION:AWS_ACCOUNT_ID:rule/*akto*"`,
    `            ]`,
    `        } `,
    `    ]`,
    `}`
]

const yaml_fargate =[
    `"AKTO_NLB": "<AKTO_NLB_IP>",`,
    `"AKTO_MONGO_IP": "<AKTO_MONGO_CONN>"`   
]

const yaml_kubernetes = [
    `apiVersion: apps/v1`,
    `kind: DaemonSet`,
    `metadata:`,
    `  name: akto-k8s`,
    `  namespace: {NAMESPACE}`,
    `  labels:`,
    `    app: {APP_NAME}`,
    `spec:`,
    `  selector:`,
    `    matchLabels:`,
    `      app: {APP_NAME}`,
    `  template:`,
    `    metadata:`,
    `      labels:`,
    `        app: {APP_NAME}`,
    `    spec:`,
    `      hostNetwork: true`,
    `      containers:`,
    `      - name: mirror-api-logging`,
    `        image: aktosecurity/mirror-api-logging:k8s_agent`,
    `        env: `,
    `          - name: AKTO_TRAFFIC_BATCH_TIME_SECS`,
    `            value: "10"`,
    `          - name: AKTO_TRAFFIC_BATCH_SIZE`,
    `            value: "100"`,
    `          - name: AKTO_INFRA_MIRRORING_MODE`,
    `            value: "gcp"`,
    `          - name: AKTO_KAFKA_BROKER_MAL`,
    `            value: "<AKTO_NLB_IP>:9092"`,
    `          - name: AKTO_MONGO_CONN`,
    `            value: "<AKTO_MONGO_CONN>"`,
]



const quickStartFunc = {

    getConnectorsListCategorized: function () {
        // Virtual Machines
        const vm = [
            dockerObj, tcpObj
        ];
        
        // Source code
        const sourceCode = [
            apiInventoryFromSourceCodeObj, githubObj, gitlabObj, bitbucketObj
        ];

        // Hybrid Saas
        const hybridSaas = [
            hybridSaasObj
        ];

        // Kubernetes
        const kubernetes = [
            kubernetesObj, openshiftObj, ebpfObj, ebpfMTLSObj
        ];

        // API Gateways
        const apiGateways = [
            apigeeObj, iisObj, azureObj, cloudflareObj, f5Obj, kongmeshObj, layer7Obj, threescaleObj, nginxObj, haproxyObj, envoyObj, istioObj, kongObj, ibmapiconnectObj, citrixObj, azureappserviceObj, mulesoftObj
        ];

        // Mirroring
        const mirroring = [
            mirroringObj, gcpObj
        ];

        // AWS Services
        const awsServices = [
            awsApiGatewayObj, awsLambdaObj, eksObj, fargateObj, beanStalkObj, ecsObj
        ];

        // GCP Services
        const gcpServices = [
            gcpObj, apigeeObj, gcrObj, gcrfuncObj, gkeObj
        ];

        // Azure Services
        const azureServices = [
            azureappserviceObj, aksObj, azureopenshiftObj, azurefuncObj, azurecontainerappObj, azureObj
        ];

        // Manual
        const manual = [
            burpObj, postmanObj, harFileUploadObj, openApiObj, wsdlApiObj, graphqlApiIObj, impervaImportObj
        ];

        // Crawler
        const crawler = [
            aktoJaxObj
        ];

        const aiScanConnectors = [
            geminiObj, openAIObj, claudeObj, deepseekObj, llamaObj, grokObj, customAIObj
        ]

        const aiAgentGateways = [
            aiAgentGlobalProxy, aiAgentGateway

        ]

        const aiAgentConnectors = [
            awsBedrockObj, azureAIFoundryObj, databricksObj, googleVertexAIObj, ibmWatsonxObj, customAgentObj, agenticShieldObj
        ]

        // MCP Scan
        const mcpScan = [
            mcpScanObj, mcpReconObj, mcpProxyObj, mcpGateway,mcpWrapperObj
        ];

        // Akto SDK
        const aktoSdk = [
            goObj, javaObj, nodejsObj, pythonObj
        ];

        // Secure Web Networks
        const secureWebNetworks = [
            cloudflareWarpObj, zscalerObj
        ];

        const browserExtensions = [
            chromeExtensionObj, firefoxExtensionObj, safariExtensionObj
        ]

       if(func.checkLocal() || func.isLimitedAccount()){
           return {
               "Manual": manual
           }
       }

        let connectors = {}

        if(isGenAISecurityCategory() || isAgenticSecurityCategory()){
            connectors["AI Agent Scan"] = aiAgentGateways
            connectors["AI Agent Security"] = aiAgentConnectors
            connectors["AI Model Security"] = aiScanConnectors
            connectors["Browser Extension"] = browserExtensions
            connectors["Secure Web Networks"] = secureWebNetworks
        }

        if(isMCPSecurityCategory() || isAgenticSecurityCategory()){
            connectors["MCP Scan"] = mcpScan
        }

        if (isDastCategory()) {
            connectors["DAST"] = crawler
        }

        connectors = {
            "Hybrid SaaS": hybridSaas,
            ...connectors,
            "Kubernetes": kubernetes,
            "API Gateways": apiGateways,
            "Mirroring": mirroring,
            "AWS Services": awsServices,
            "GCP Services": gcpServices,
            "Azure Services": azureServices,
            "Manual": manual,
            "Akto SDK": aktoSdk,
            "Virtual Machines": vm,
            "Source Code": sourceCode,
        }

        return connectors;
    },
    getSourceCodeConnectors: function () {
        const sourceCode = [
            apiInventoryFromSourceCodeObj, githubObj, gitlabObj, bitbucketObj
        ];
        return sourceCode
    },

    getConnectorsList: function () {

        if(func.checkLocal() || func.isLimitedAccount()){
            return [burpObj, postmanObj, openApiObj, harFileUploadObj, impervaImportObj]
        }

        // Combine all categories into connectorsList
        let connectorsList = [
            gcpObj, kubernetesObj, fargateObj, nginxObj, burpObj, postmanObj,
            openApiObj, beanStalkObj, eksObj, dockerObj, envoyObj, mcpScanObj, mcpProxyObj, mcpGateway, mcpWrapperObj, impervaImportObj,
            harFileUploadObj, kongObj, tcpObj, mirroringObj, hybridSaasObj, apiInventoryFromSourceCodeObj,
            ebpfObj, ebpfMTLSObj, istioObj, pythonObj, awsApiGatewayObj, awsLambdaObj,
            apigeeObj, iisObj, azureObj, cloudflareObj, f5Obj, goObj, haproxyObj, javaObj, kongmeshObj, layer7Obj, nodejsObj, openshiftObj, threescaleObj, githubObj, gitlabObj, bitbucketObj, aktoJaxObj,
            cloudflareWarpObj, zscalerObj
        ]

        if(isGenAISecurityCategory() || isAgenticSecurityCategory()){
            connectorsList = connectorsList.concat([
                geminiObj, openAIObj, claudeObj, deepseekObj, llamaObj, grokObj, customAIObj,
                awsBedrockObj, azureAIFoundryObj, databricksObj, googleVertexAIObj, ibmWatsonxObj, customAgentObj, agenticShieldObj
            ])
        }

        return connectorsList
    },

    convertListForMenu: function(items) {
        const arr = items.map((item,index)=> {
            let label = (
                <div style={{display: 'flex', gap: '4px', alignItems: 'center'}}>
                    <Avatar customer size="extraSmall" name={item.label} source={item.icon}/>
                    {item.label}
                    {item.badge  ? <Badge size='small' status='success'>{item.badge}</Badge> : null}
                </div>
            )
            return{
                id: index + 1,
                label: label,
                value: item.label,
            }
        })

        return arr
    },

    getPolicyLines: function(key){
        switch(key) {
            case "AWS":
                return quick_start_policy_lines

            case "FARGATE":
                return quick_start_policy_lines_kubernetes

            case "KUBERNETES":
                return quick_start_policy_lines_kubernetes

            default:
                return []
        }
    },

    getYamlLines: function(key){
        switch(key){
            case "FARGATE":
                return yaml_fargate

            case "KUBERNETES":
                return yaml_kubernetes

            default :
                return []
        }
    },

    convertLbList: function(lbList){
        const arr = lbList.map((item) => {
            return{
                label: item.resourceName,
                value: item.resourceId,
            }
        })
        return arr
    },

    getValuesArr: function(lbList){
        let arr = []
        lbList.forEach((element) => {
            arr.push(element.resourceId)
        })
        return arr
    },

    getLBListFromValues: function(valuesArr,mapNameToId){
        const arr = valuesArr.map((element) => {
            return{
                resourceId: element,
                resourceName: mapNameToId[element]
            }
        })
        return arr
    },

    getDesiredSteps: function(url) {
        const steps = [
            {
              textComponent: <span>Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking <a target='_blank' href={url}>here</a>.</span>, 
            },
            {
              text: "We will create an inline policy, navigate to JSON tab and paste the copied JSON here."
            },
            {
              text: "Click on 'Review policy'."
            },
            {
              text: "Now lets name the policy as 'AktoDashboardPolicy'."
            },
            {
              text: "Finally create the policy by clicking on 'Create policy'."
            },
        ]
        return steps
    },

    renderProgressBar: function (creationTimeInMs, progressBar){
        const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
        progressBarCopy.show = true;
        const currTimeInMs = Date.now();
        const maxDeploymentTimeInMs = progressBarCopy.max_deployment_time_in_ms;
        let progressPercent = ((currTimeInMs - creationTimeInMs) * 100) / maxDeploymentTimeInMs
        if (progressPercent > 90) {
            progressPercent = 90;
        }
        // to add more else if blocks to handle cases where deployment is stuck
        progressBarCopy.value = Math.round(progressPercent);
        return progressBarCopy
      },
  
      removeProgressBarAndStatuschecks: function(progressBar) {
        const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
        progressBarCopy.show = false;
        progressBarCopy.value = 0;
        return progressBarCopy
      }
}

export default quickStartFunc