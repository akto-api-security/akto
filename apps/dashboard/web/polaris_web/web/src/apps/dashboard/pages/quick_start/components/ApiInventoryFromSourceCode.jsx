import { Box, HorizontalStack, LegacyCard, Text, VerticalStack } from "@shopify/polaris";
import JsonComponent from "./shared/JsonComponent";
import { useRef } from "react";
import func from '@/util/func'

function ApiInventoryFromSourceCode() {

    const ref = useRef(null)

    let githubWorkflowStep = [
        `- name: Akto code analysis`,
        `  uses: akto-api-security/code-analysis-action@v1`,
        `  with:`,
        `    AKTO_DASHBOARD_URL: "<AKTO_DASHBOARD_URL>"`,
        `    AKTO_API_KEY: {{ secrets.AKTO_API_KEY }}`,
        `    API_COLLECTION_NAME: juice_shop_demo`
    ]
    githubWorkflowStep = githubWorkflowStep.join("\n")

    let dockerRunCommand = [
        `docker run -it --rm -v "$(pwd)":/usr/source_code \\`,
        `  aktosecurity/akto-puppeteer-replay:latest cli extract \\`,
        `    --IS_DOCKER="true" \\`,
        `    --AKTO_SYNC="true" \\`,
        `    --AKTO_DASHBOARD_URL="AKTO_DASHBOARD_URL" \\`,
        `    --AKTO_API_KEY="AKTO_API_KEY" \\`,
        `    --API_COLLECTION_NAME="juice_shop_demo" \\`,
        `    --GITHUB_REPOSITORY="akto-api-security/juice-shop" \\`,
        `    --GITHUB_BRANCH="master"`
    ];
    dockerRunCommand = dockerRunCommand.join("\n");    

    const copyGithubWorkflowStep = () => {
        func.copyToClipboard(githubWorkflowStep, ref, "Github workflow step copied to clipboard!")
    }

    const copyDockerRunCommand = () => {
        func.copyToClipboard(dockerRunCommand, ref, "Docker run command copied to clipboard!")
    }

    return (
        <div className="card-items">
            <Text variant="bodyMd">
                Akto supports creating API inventory from source code with the help of our code analysis tool. Simply run our code analysis tool on your source code repositories to discover APIs, including shadow APIs.
            </Text>

            <div>
                <Text variant="headingSm">Extract APIs from github hosted source code using our Github Action</Text>
                <Text variant="bodyMd">Add a step in your github action workflow based on the following example:</Text>
                <br/>
                <JsonComponent title="Github workflow step" toolTipContent="Copy the github workflow step" onClickFunc={() => copyGithubWorkflowStep()} dataString={githubWorkflowStep} language="yaml" minHeight="150px"/>
            </div>

            <div>
                <Text variant="headingSm">Extract APIs from source code using our Docker based CLI</Text>
                <Text variant="bodyMd">Run the following docker run command in a terminal:</Text>
                <br/>
                <JsonComponent title="Docker run command" toolTipContent="Copy the docker run command" onClickFunc={() => copyDockerRunCommand()} dataString={dockerRunCommand} language="bash" minHeight="150px"/>
            </div>

            <div ref={ref} />
        </div>
    )
}

export default ApiInventoryFromSourceCode;