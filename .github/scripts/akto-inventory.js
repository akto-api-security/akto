const axios = require("axios")
const fs = require("fs")
const { exec } = require("child_process");

const AKTO_DASHBOARD_URL = process.env.AKTO_DASHBOARD_URL
const AKTO_API_KEY = process.env.AKTO_API_KEY
const GITHUB_STEP_SUMMARY = process.env.GITHUB_STEP_SUMMARY

const headers = {
    'X-API-KEY': AKTO_API_KEY,
}

function logGithubStepSummary(message) {
    fs.appendFileSync(GITHUB_STEP_SUMMARY, `${message}\n`);
}

function processOpenAPIfile(openAPIObject) {
    // Modify the headers in the openAPIString object
    for (const path in openAPIObject.paths) {
        
        if (!path.startsWith("/api")) {
            delete openAPIObject.paths[path]
            continue
        }

        const pathInfo = openAPIObject.paths[path];
        for (const method in pathInfo) {
            if (method === "description" || method === 'parameters') {
                continue
            }
            const operation = pathInfo[method];
            if (operation.parameters) {
                // Remove existing headers
                operation.parameters = operation.parameters.filter(param => param.in !== 'header');
            }
            // Add new headers
            operation.parameters = operation.parameters || [];
            operation.parameters.push(
                {
                    name: 'X-API-KEY',
                    in: 'header',
                    schema: {
                        type: 'string',
                        example: 'your-api-key',
                    },
                },
                {
                    name: 'content-type',
                    in: 'header',
                    schema: {
                        type: 'string',
                        example: 'application/json',
                    },
                }
            );

        }
    }

    return openAPIObject
}

async function saveOpenAPIfile(openAPIObject) {
    // Convert the JSON object to a JSON string
    const jsonString = JSON.stringify(openAPIObject, null, 2);

    // Specify the file path where you want to save the JSON
    const filePath = './akto_open_api.json';

    // Write the JSON string to the file
    fs.writeFile(filePath, jsonString, 'utf8', (err) => {
        if (err) {
            console.error(`Error writing file: ${err}`);
        } else {
            console.log(`JSON object saved to ${filePath}`);
        }
    });

}

function generateAktoEndpointsSummary(processedOpenAPIObject) {
    let sourceAktoEndpoints 

    exec('cd ../../apps/dashboard/web/polaris_web/web/src; grep -r --include "*.js" "api/" . | awk -F"\'" \'/url:/ {print $2}\'', (error, stdout, stderr) => {
        if (error) {
            console.log(`error: ${error.message}`);
            return;
        }
        if (stderr) {
            console.log(`stderr: ${stderr}`);
            return;
        }
        
        sourceAktoEndpoints = stdout.split('\n')
        sourceAktoEndpoints.pop()
        sourceAktoEndpoints.forEach(endpoint => console.log(endpoint))

        const openAPIAktoEndpoints = Object.keys(processedOpenAPIObject.paths)
        
        logGithubStepSummary(`Akto endpoints count (source): ${sourceAktoEndpoints.length}`)
        logGithubStepSummary(`Akto endpoints count (OpenAPI file): ${openAPIAktoEndpoints.length}`)
        logGithubStepSummary(`#### Endpoints missing in OpenAPI file`)
        
        let counter = 1
        logGithubStepSummary("S.No | Endpoint ")
        logGithubStepSummary("--- | --- ") 

        sourceAktoEndpoints.forEach(sourceEndpoint => {
            if (!openAPIAktoEndpoints.includes(sourceEndpoint)) {
                logGithubStepSummary(`${counter} | ${sourceEndpoint} `)
                counter += 1
            }
        });
    });
}

async function main() {

    const data = {
        apiCollectionId: "-1753579810"
    }

    try {
        const generateOpenApiFileResponse = await axios.post(`${AKTO_DASHBOARD_URL}/api/generateOpenApiFile`, { ...data }, { headers })

        if (generateOpenApiFileResponse.status === 200) {
            const openAPIObject = JSON.parse(generateOpenApiFileResponse.data.openAPIString)
            const processedOpenAPIObject = processOpenAPIfile(openAPIObject)
            saveOpenAPIfile(processedOpenAPIObject)

            logGithubStepSummary("### Akto inventory summary")
            generateAktoEndpointsSummary(processedOpenAPIObject)
        }
    } catch (error) {
        console.error('Error:', error.message);
    }
}

main()