
const STEPS_PER_AGENT_ID = {
    "FIND_VULNERABILITIES_FROM_SOURCE_CODE": 5,
    "FIND_APIS_FROM_SOURCE_CODE": 3,
    "FIND_SENSITIVE_DATA_TYPES": 1,
    "CREATE_TEST_TEMPLATES": 1,
    "GROUP_APIS": 1,
    "FIND_FALSE_POSITIVE": 1,
}

const checkForSourceCodeApis = async()=> {
    // make and api call to check the source code apis of this directory
    // chosen directory => {get from output of step "1"}
    return false
}

export const preRequisitesMap = {
    "FIND_VULNERABILITIES_FROM_SOURCE_CODE": {
        4: {
            "text": "Please provide the list of apis for finding vulnerabilities",
            "action": () => checkForSourceCodeApis()
        },
    }
}

export function structuredOutputFormat (output: any, agentType: string | undefined, subProcessId: string): any {
    console.log("output", output)
    switch (agentType) {
        case "FIND_VULNERABILITIES_FROM_SOURCE_CODE":
            switch (subProcessId) {
                case "1":
                    return {
                        "chosenBackendDirectory": output
                    }
                case "2":
                    if(typeof output === "string") {
                        const jsonStr = `{${output}}`;
                        const obj = JSON.parse(jsonStr);
                        console.log("obj", obj)
                        return obj
                    }else{
                        return output
                    }
            default:
                return output
            }
        case "FIND_APIS_FROM_SOURCE_CODE":
            switch (subProcessId) {
                case "1":
                    return {
                        "chosenBackendDirectory": output
                    }
                case "2":
                    if(typeof output === "string") {
                        const jsonStr = `{${output}}`;
                        const obj = JSON.parse(jsonStr);
                        console.log("obj", obj)
                        return obj
                    }else{
                        return output
                    }
            default:
                return output
            }
        default:
            return output
    }
}

export default STEPS_PER_AGENT_ID